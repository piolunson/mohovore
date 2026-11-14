package dev.leonetic.features.modules.render;

import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_2338.class_2339;

public class QuadSupremeModule extends Module {
   public Setting<Integer> radius = this.num("Radius", 5, 2, 16);
   public Setting<Integer> depth = this.num("Depth", 3, 1, 8);
   public Setting<Float> yOffset = this.num("Height", 0.02F, 0.0F, 1.0F);
   public Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F);
   public Setting<Color> color = this.color("Color", 0, 200, 255, 200);
   public Setting<Float> fadeStart = this.num("FadeStart", 0.25F, 0.0F, 1.0F);
   public Setting<Boolean> pulse = this.bool("Pulse", true);
   public Setting<Float> pulseSpeed = this.num("PulseSpeed", 3.0F, 0.0F, 12.0F);
   private final class_2339 cursor = new class_2339();
   private final Set<Long> drawnEdges = new HashSet<>();

   public QuadSupremeModule() {
      super("QuadSupreme", "Holographic block grid that drapes over the terrain at your feet.", Module.Category.RENDER);
      this.pulseSpeed.setVisibility(v -> this.pulse.getValue());
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         float partial = event.getDelta();
         double cx = class_3532.method_16436(partial, mc.field_1724.field_6038, mc.field_1724.method_23317());
         double cz = class_3532.method_16436(partial, mc.field_1724.field_5989, mc.field_1724.method_23321());
         int feetY = class_3532.method_15357(class_3532.method_16436(partial, mc.field_1724.field_5971, mc.field_1724.method_23318()));
         int r = this.radius.getValue();
         double rSq = (double)r * r;
         Color base = this.color.getValue();
         float width = this.lineWidth.getValue();
         float offset = this.yOffset.getValue();
         double phase = this.pulse.getValue() ? System.currentTimeMillis() % 100000L / 1000.0 * this.pulseSpeed.getValue().floatValue() : 0.0;
         this.drawnEdges.clear();
         int minX = class_3532.method_15357(cx) - r;
         int maxX = class_3532.method_15357(cx) + r;
         int minZ = class_3532.method_15357(cz) - r;
         int maxZ = class_3532.method_15357(cz) + r;

         for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
               double ddx = bx + 0.5 - cx;
               double ddz = bz + 0.5 - cz;
               if (!(ddx * ddx + ddz * ddz > rSq)) {
                  int top = this.surfaceTop(bx, bz, feetY);
                  if (top != Integer.MIN_VALUE) {
                     float y = top + offset;
                     this.edge(bx, bz, bx + 1, bz, y, top, cx, cz, r, base, width, phase);
                     this.edge(bx, bz + 1, bx + 1, bz + 1, y, top, cx, cz, r, base, width, phase);
                     this.edge(bx, bz, bx, bz + 1, y, top, cx, cz, r, base, width, phase);
                     this.edge(bx + 1, bz, bx + 1, bz + 1, y, top, cx, cz, r, base, width, phase);
                  }
               }
            }
         }
      }
   }

   private int surfaceTop(int bx, int bz, int feetY) {
      int range = this.depth.getValue();
      int best = Integer.MIN_VALUE;
      int bestDist = Integer.MAX_VALUE;

      for (int y = feetY + range; y >= feetY - range; y--) {
         if (this.isSolid(bx, y, bz)) {
            int surface = y + 1;
            int dist = Math.abs(surface - feetY);
            if (dist < bestDist) {
               bestDist = dist;
               best = surface;
            }
         }
      }

      return best;
   }

   private boolean isSolid(int x, int y, int z) {
      this.cursor.method_10103(x, y, z);
      return !mc.field_1687.method_8320(this.cursor).method_26220(mc.field_1687, this.cursor).method_1110();
   }

   private void edge(int x1, int z1, int x2, int z2, float y, int top, double cx, double cz, int r, Color base, float width, double phase) {
      if (this.drawnEdges.add(edgeKey(x1, z1, x2, z2, top))) {
         double midX = (x1 + x2) * 0.5;
         double midZ = (z1 + z2) * 0.5;
         double dist = Math.sqrt((midX - cx) * (midX - cx) + (midZ - cz) * (midZ - cz));
         Color c = this.shade(base, dist, r, phase);
         if (c.getAlpha() > 2) {
            RenderUtil.drawLine(new class_243(x1, y, z1), new class_243(x2, y, z2), c, width);
         }
      }
   }

   private static long edgeKey(int x1, int z1, int x2, int z2, int y) {
      int ax = Math.min(x1, x2);
      int az = Math.min(z1, z2);
      int bx = Math.max(x1, x2);
      int bz = Math.max(z1, z2);
      long k = ax & 1048575L;
      k = k * 1048576L + (az & 1048575L);
      k = k * 2L + (ax == bx ? 0 : 1);
      return k * 1048576L + (y & 1048575L);
   }

   private Color shade(Color base, double dist, int r, double phase) {
      float t = (float)(dist / r);
      if (t > 1.0F) {
         t = 1.0F;
      }

      float start = this.fadeStart.getValue();
      float fade = t <= start ? 1.0F : 1.0F - (t - start) / (1.0F - start);
      fade = class_3532.method_15363(fade, 0.0F, 1.0F);
      fade = fade * fade * (3.0F - 2.0F * fade);
      if (this.pulse.getValue()) {
         float ripple = 0.65F + 0.35F * (float)Math.sin(phase - dist * 1.2);
         fade *= ripple;
      }

      int alpha = class_3532.method_15340(Math.round(base.getAlpha() * fade), 0, 255);
      return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
   }
}
