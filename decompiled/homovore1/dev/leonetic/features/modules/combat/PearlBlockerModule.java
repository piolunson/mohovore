package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1684;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3532;

public class PearlBlockerModule extends Module {
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 0, 0, 44).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 0, 0, 44).setPage("Render");
   private final Map<Integer, class_2338> tracked = new HashMap<>();
   private final Map<class_2338, Long> renderMap = new HashMap<>();
   private static final int MAX_FORWARD_TICKS = 60;
   private static final int SUB_SAMPLES = 8;
   private static final double GRAVITY = 0.03;
   private static final double AIR_DRAG = 0.99;
   private static final double WATER_DRAG = 0.8;

   public PearlBlockerModule() {
      super("PearlBlocker", "Catches enemy pearls by placing obsidian in their flight path.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.tracked.clear();
      this.renderMap.clear();
   }

   @Override
   public void onDisable() {
      this.tracked.clear();
      this.renderMap.clear();
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         Result obs = InventoryUtil.find(class_1802.field_8281, InventoryUtil.PLACE_SCOPE);
         if (obs.found() && obs.type() != ResultType.OFFHAND) {
            int obsSlot = obs.slot();
            long now = System.currentTimeMillis();

            for (class_1297 e : mc.field_1687.method_18112()) {
               if (e instanceof class_1684 pearl
                  && !pearl.method_31481()
                  && pearl.method_24921() instanceof class_1657 owner
                  && owner != mc.field_1724
                  && !Homovore.friendManager.isFriend(owner)
                  && (!pearl.method_5799() || !(pearl.method_18798().method_1033() < 0.015))) {
                  int id = pearl.method_5628();
                  class_2338 prev = this.tracked.get(id);
                  if (prev == null || mc.field_1687.method_8320(prev).method_45474()) {
                     class_2338 target = this.findPlaceablePosInPath(pearl);
                     if (target != null && PlaceUtil.canPlace(target)) {
                        if (Homovore.placementManager.enqueue(target, class_1802.field_8281)) {
                           this.renderMap.put(target.method_10062(), now);
                        }

                        this.tracked.put(id, target);
                     }
                  }
               }
            }

            this.tracked.entrySet().removeIf(entry -> {
               class_1297 found = mc.field_1687.method_8469(entry.getKey());
               return found == null || found.method_31481();
            });
            long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
            this.renderMap.entrySet().removeIf(ex -> now - (Long)ex.getValue() > fadeMs);
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<class_2338, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               double t = age / fadeMs;
               Color fc = this.fillColor.getValue();
               Color oc = this.outlineColor.getValue();
               RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
               RenderUtil.drawBox(event.getMatrix(), entry.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
            }
         }
      }
   }

   private class_2338 findPlaceablePosInPath(class_1684 pearl) {
      class_243 pos = pearl.method_73189();
      class_243 vel = pearl.method_18798();
      boolean inWater = pearl.method_5799();
      class_243 eye = mc.field_1724.method_33571();

      for (int t = 0; t < 60; t++) {
         class_243 next = pos.method_1019(vel);

         for (int s = 1; s <= 8; s++) {
            class_243 sample = pos.method_1019(next.method_1020(pos).method_1021(s / 8.0));
            class_2338 blockPos = class_2338.method_49638(sample);
            if (eye.method_1022(sample) <= 5.1 && mc.field_1687.method_8320(blockPos).method_45474() && PlaceUtil.canPlace(blockPos)) {
               return blockPos;
            }
         }

         pos = next;
         vel = stepVelocity(vel, inWater);
         if (pos.field_1351 < mc.field_1687.method_31607() - 5) {
            break;
         }
      }

      return null;
   }

   private static class_243 stepVelocity(class_243 vel, boolean inWater) {
      double drag = inWater ? 0.8 : 0.99;
      return new class_243(vel.field_1352 * drag, vel.field_1351 * drag - 0.03, vel.field_1350 * drag);
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), class_3532.method_15340(a, 0, 255));
   }
}
