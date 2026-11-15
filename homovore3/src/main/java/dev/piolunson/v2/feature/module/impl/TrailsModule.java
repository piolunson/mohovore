package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.EntityDataSetting;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.feature.setting.SettingGroup;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

public class TrailsModule extends Module {
   private static final double MIN_SEGMENT_SQ = 1.0E-4;
   public final EntityDataSetting<TrailsModule.TrailData> entities = this.register(
      new EntityDataSetting<>("Entities", type -> new TrailsModule.TrailData(type), List.of(EntityType.PLAYER, EntityType.ENDER_PEARL))
   );
   public final Setting<Boolean> self = this.bool("Self", true).describe("Draws your own trail, using the player entry's look.");
   public final Setting<Integer> delay = this.integer("Delay", 2, 0, 20)
      .describe("Ticks to ignore after an entity spawns, so a thrown projectile does not trail back to the hand that threw it.");
   public final Setting<Boolean> fade = this.bool("Fade", true);
   public final Setting<Float> fadeStart = this.number("FadeStart", 0.5F, 0.0F, 1.0F).describe("Fraction of a segment's life spent at full alpha.");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true);
   private final Map<Integer, TrailsModule.Trail> trails = new HashMap<>();
   private ClientWorld level;

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
      if (mc.world != null && mc.player != null) {
         if (this.level != mc.world) {
            this.trails.clear();
            this.level = mc.world;
         }

         this.age();

         for (TrailsModule.Trail trail : this.trails.values()) {
            trail.present = false;
         }

         for (Entity entity : mc.world.getEntities()) {
            TrailsModule.TrailData data = this.getTrail(entity);
            if (data != null) {
               TrailsModule.Trail trail = this.trails.computeIfAbsent(entity.getId(), id -> new TrailsModule.Trail(entity.getType()));
               trail.present = true;
               Vec3d sample = new Vec3d(entity.lastRenderX, entity.lastRenderY, entity.lastRenderZ);
               if (trail.last != null && entity.age > this.delay.getValue() && trail.last.squaredDistanceTo(sample) > 1.0E-4) {
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
   public void onRender3D(Camera camera, float partialTick) {
      if (mc.world != null && mc.player != null) {
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

         for (Entity entity : mc.world.getEntities()) {
            TrailsModule.TrailData data = this.getTrail(entity);
            if (data != null && entity.age > this.delay.getValue()) {
               TrailsModule.Trail trail = this.trails.get(entity.getId());
               if (trail != null && trail.last != null) {
                  this.draw(
                     trail.last, entity.getLerpedPos(partialTick), data.height.getValue(), data.color.getValue().getRGB(), data.width.getValue().floatValue()
                  );
               }
            }
         }
      }
   }

   private void draw(Vec3d from, Vec3d to, double height, int color, float width) {
      VisibilityConfigurable line = GizmoDrawing.line(from.add(0.0, height, 0.0), to.add(0.0, height, 0.0), color, width);
      if (this.throughWalls.getValue()) {
         line.ignoreOcclusion();
      }
   }

   private TrailsModule.TrailData getTrail(Entity entity) {
      if (entity == mc.player && !this.self.getValue()) {
         return null;
      } else {
         return this.entities.contains(entity.getType()) ? this.entities.get(entity.getType()) : null;
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
      final Vec3d from;
      final Vec3d to;
      final int lifeTime;
      int age;

      Segment(Vec3d from, Vec3d to, int lifeTime) {
         this.from = from;
         this.to = to;
         this.lifeTime = lifeTime;
      }
   }

   private static class Trail {
      final EntityType<?> type;
      final Deque<TrailsModule.Segment> segments = new ArrayDeque<>();
      Vec3d last;
      boolean present;

      Trail(EntityType<?> type) {
         this.type = type;
      }
   }

   public static class TrailData extends SettingGroup {
      public final Setting<Color> color;
      public final Setting<Double> height;
      public final Setting<Integer> length = this.register(new Setting<>("Length", 80, 5, 600)).describe("Ticks a segment stays on screen.");
      public final Setting<Double> width = this.register(new Setting<>("Width", 3.0, 0.1, 16.0));

      public TrailData(EntityType<?> type) {
         this.color = this.register(new Setting<>("Color", defaultColor(type)));
         this.height = this.register(new Setting<>("Height", defaultHeight(type), 0.0, 4.0))
            .describe("Blocks above the entity's feet that the trail is drawn at.");
      }

      private static Color defaultColor(EntityType<?> type) {
         if (type == EntityType.ENDER_PEARL) {
            return new Color(190, 110, 255, 200);
         } else {
            return type == EntityType.PLAYER ? new Color(255, 255, 255, 200) : new Color(120, 200, 255, 200);
         }
      }

      private static double defaultHeight(EntityType<?> type) {
         return type == EntityType.PLAYER ? 0.1 : 0.0;
      }
   }
}
