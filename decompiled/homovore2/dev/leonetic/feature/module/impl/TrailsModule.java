package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.EntityDataSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.feature.setting.SettingGroup;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.class_12178;
import net.minecraft.class_12180;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import net.minecraft.class_638;

public class TrailsModule extends Module {
   private static final double MIN_SEGMENT_SQ = 1.0E-4;
   public final EntityDataSetting<TrailsModule.TrailData> entities = this.register(
      new EntityDataSetting<>("Entities", type -> new TrailsModule.TrailData(type), List.of(class_1299.field_6097, class_1299.field_6082))
   );
   public final Setting<Boolean> self = this.bool("Self", true).describe("Draws your own trail, using the player entry's look.");
   public final Setting<Integer> delay = this.integer("Delay", 2, 0, 20)
      .describe("Ticks to ignore after an entity spawns, so a thrown projectile does not trail back to the hand that threw it.");
   public final Setting<Boolean> fade = this.bool("Fade", true);
   public final Setting<Float> fadeStart = this.number("FadeStart", 0.5F, 0.0F, 1.0F).describe("Fraction of a segment's life spent at full alpha.");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true);
   private final Map<Integer, TrailsModule.Trail> trails = new HashMap<>();
   private class_638 level;

   public TrailsModule() {
      super("Trails", Category.RENDER, "Draws the path selected entities have travelled.");
      this.fadeStart.visibleWhen(this.fade::getValue);
   }

   @Override
   public void onEnable() {
      this.clear();
   }

   @Override
   public void onDisable() {
      this.clear();
   }

   private void clear() {
      this.trails.clear();
      this.level = null;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (this.level != mc.field_1687) {
            this.trails.clear();
            this.level = mc.field_1687;
         }

         this.age();

         for (TrailsModule.Trail trail : this.trails.values()) {
            trail.present = false;
         }

         for (class_1297 entity : mc.field_1687.method_18112()) {
            TrailsModule.TrailData data = this.getTrail(entity);
            if (data != null) {
               TrailsModule.Trail trail = this.trails.computeIfAbsent(entity.method_5628(), id -> new TrailsModule.Trail(entity.method_5864()));
               trail.present = true;
               class_243 sample = new class_243(entity.field_6038, entity.field_5971, entity.field_5989);
               if (trail.last != null && entity.field_6012 > this.delay.getValue() && trail.last.method_1025(sample) > 1.0E-4) {
                  trail.segments.addLast(new TrailsModule.Segment(trail.last, sample, data.length.getValue()));
               }

               trail.last = sample;
            }
         }

         this.trails.values().removeIf(trailx -> !trailx.present && trailx.segments.isEmpty());
      } else {
         this.clear();
      }
   }

   private void age() {
      for (TrailsModule.Trail trail : this.trails.values()) {
         Iterator<TrailsModule.Segment> it = trail.segments.iterator();

         while (it.hasNext()) {
            TrailsModule.Segment segment = it.next();
            if (++segment.age >= segment.lifeTime) {
               it.remove();
            }
         }
      }
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         for (TrailsModule.Trail trail : this.trails.values()) {
            TrailsModule.TrailData data = this.entities.contains(trail.type) ? this.entities.get(trail.type) : null;
            if (data != null) {
               Color base = data.color.getValue();
               float width = data.width.getValue().floatValue();
               double height = data.height.getValue();

               for (TrailsModule.Segment segment : trail.segments) {
                  this.draw(segment.from, segment.to, height, this.alpha(base, segment, partialTick), width);
               }
            }
         }

         for (class_1297 entity : mc.field_1687.method_18112()) {
            TrailsModule.TrailData data = this.getTrail(entity);
            if (data != null && entity.field_6012 > this.delay.getValue()) {
               TrailsModule.Trail trail = this.trails.get(entity.method_5628());
               if (trail != null && trail.last != null) {
                  this.draw(
                     trail.last, entity.method_30950(partialTick), data.height.getValue(), data.color.getValue().getRGB(), data.width.getValue().floatValue()
                  );
               }
            }
         }
      }
   }

   private void draw(class_243 from, class_243 to, double height, int color, float width) {
      class_12178 line = class_12180.method_75546(from.method_1031(0.0, height, 0.0), to.method_1031(0.0, height, 0.0), color, width);
      if (this.throughWalls.getValue()) {
         line.method_75533();
      }
   }

   private TrailsModule.TrailData getTrail(class_1297 entity) {
      if (entity == mc.field_1724 && !this.self.getValue()) {
         return null;
      } else {
         return this.entities.contains(entity.method_5864()) ? this.entities.get(entity.method_5864()) : null;
      }
   }

   private int alpha(Color base, TrailsModule.Segment segment, float partialTick) {
      if (!this.fade.getValue()) {
         return base.getRGB();
      }

      float life = (segment.age + partialTick) / segment.lifeTime;
      float start = this.fadeStart.getValue();
      float progress = start >= 1.0F ? 0.0F : (life - start) / (1.0F - start);
      progress = Math.clamp(progress, 0.0F, 1.0F);
      int a = Math.round(base.getAlpha() * (1.0F - progress));
      return new Color(base.getRed(), base.getGreen(), base.getBlue(), a).getRGB();
   }

   private static class Segment {
      final class_243 from;
      final class_243 to;
      final int lifeTime;
      int age;

      Segment(class_243 from, class_243 to, int lifeTime) {
         this.from = from;
         this.to = to;
         this.lifeTime = lifeTime;
      }
   }

   private static class Trail {
      final class_1299<?> type;
      final Deque<TrailsModule.Segment> segments = new ArrayDeque<>();
      class_243 last;
      boolean present;

      Trail(class_1299<?> type) {
         this.type = type;
      }
   }

   public static class TrailData extends SettingGroup {
      public final Setting<Color> color;
      public final Setting<Double> height;
      public final Setting<Integer> length = this.register(new Setting<>("Length", 80, 5, 600)).describe("Ticks a segment stays on screen.");
      public final Setting<Double> width = this.register(new Setting<>("Width", 3.0, 0.1, 16.0));

      public TrailData(class_1299<?> type) {
         this.color = this.register(new Setting<>("Color", defaultColor(type)));
         this.height = this.register(new Setting<>("Height", defaultHeight(type), 0.0, 4.0))
            .describe("Blocks above the entity's feet that the trail is drawn at.");
      }

      private static Color defaultColor(class_1299<?> type) {
         if (type == class_1299.field_6082) {
            return new Color(190, 110, 255, 200);
         } else {
            return type == class_1299.field_6097 ? new Color(255, 255, 255, 200) : new Color(120, 200, 255, 200);
         }
      }

      private static double defaultHeight(class_1299<?> type) {
         return type == class_1299.field_6097 ? 0.1 : 0.0;
      }
   }
}
