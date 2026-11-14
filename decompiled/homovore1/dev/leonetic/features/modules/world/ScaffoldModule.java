package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3532;

public class ScaffoldModule extends Module {
   private final Setting<Double> radius = this.num("Radius", 1.0, 1.0, 5.0);
   private final Setting<Integer> depth = this.num("Depth", 1, 1, 5);
   private final Setting<Double> lookahead = this.num("Lookahead", 1.0, 0.0, 5.0);
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.5F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 85, 255, 85, 180).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 85, 255, 85, 35).setPage("Render");
   private final Map<class_2338, Long> renderMap = new LinkedHashMap<>();

   public ScaffoldModule() {
      super("Scaffold", "Places blocks beneath you as you walk, through the PlacementManager. No rotations.", Module.Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.renderMap.clear();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (!mc.field_1724.method_7325()) {
            class_1792 block = this.findHotbarBlock();
            if (block != null) {
               class_243 eye = mc.field_1724.method_33571();
               class_243 vel = mc.field_1724.method_18798();
               double centerX = mc.field_1724.method_23317() + vel.field_1352 * this.lookahead.getValue();
               double centerZ = mc.field_1724.method_23321() + vel.field_1350 * this.lookahead.getValue();
               int baseX = class_3532.method_15357(centerX);
               int baseZ = class_3532.method_15357(centerZ);
               int feetY = mc.field_1724.method_24515().method_10264();
               int drop = mc.field_1690.field_1832.method_1434() ? 1 : 0;
               double r = this.radius.getValue() - 1.0;
               double rSq = r * r;
               int scanR = (int)Math.ceil(r);
               int d = this.depth.getValue();
               List<class_2338> candidates = new ArrayList<>();

               for (int x = -scanR; x <= scanR; x++) {
                  for (int z = -scanR; z <= scanR; z++) {
                     if (!(x * x + z * z > rSq)) {
                        for (int dy = 1; dy <= d; dy++) {
                           class_2338 pos = new class_2338(baseX + x, feetY - dy - drop, baseZ + z);
                           class_2680 state = mc.field_1687.method_8320(pos);
                           if (state.method_45474() && PlaceUtil.canPlace(pos)) {
                              candidates.add(pos.method_10062());
                           }
                        }
                     }
                  }
               }

               candidates.sort(Comparator.comparingDouble(p -> eye.method_1025(class_243.method_24953(p))));
               long now = System.currentTimeMillis();

               for (class_2338 pos : Homovore.placementManager.placeBatch(candidates, block)) {
                  this.renderMap.put(pos, now);
               }

               long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
               this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
            }
         }
      }
   }

   private class_1792 findHotbarBlock() {
      int selected = mc.field_1724.method_31548().method_67532();
      if (mc.field_1724.method_31548().method_5438(selected).method_7909() instanceof class_1747 held) {
         return held;
      } else {
         for (int i = 0; i < 9; i++) {
            class_1799 stack = mc.field_1724.method_31548().method_5438(i);
            if (stack.method_7909() instanceof class_1747 block) {
               return block;
            }
         }

         return null;
      }
   }

   @Subscribe
   private void onRenderTargets(Render3DEvent event) {
      if (!nullCheck() && this.render.getValue()) {
         Color line = this.lineColor.getValue();
         Color fill = this.fillColor.getValue();
         float lw = this.lineWidth.getValue();
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<class_2338, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               float t = (float)(1.0 - age / fadeMs);
               class_2338 pos = entry.getKey();
               if (fill.getAlpha() > 0) {
                  RenderUtil.drawBoxFilled(event.getMatrix(), pos, withAlpha(fill, (int)(fill.getAlpha() * t)));
               }

               if (line.getAlpha() > 0) {
                  RenderUtil.drawBox(event.getMatrix(), pos, withAlpha(line, (int)(line.getAlpha() * t)), lw);
               }
            }
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), class_3532.method_15340(a, 0, 255));
   }
}
