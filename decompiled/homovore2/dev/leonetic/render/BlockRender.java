package dev.leonetic.render;

import java.awt.Color;
import net.minecraft.class_12178;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_2338;

public final class BlockRender {
   private BlockRender() {
   }

   public static void cuboid(class_2338 pos, Color lineColor, Color sideColor, float lineWidth, float alpha, boolean throughWalls) {
      class_12179 style = class_12179.method_75537(fade(lineColor, alpha), lineWidth, fade(sideColor, alpha));
      class_12178 gizmo = class_12180.method_75550(pos, style);
      if (throughWalls) {
         gizmo.method_75533();
      }
   }

   public static float alpha(long startedAt, long now, double fadeSeconds) {
      double duration = fadeSeconds * 1000.0;
      return duration <= 0.0 ? 0.0F : (float)Math.clamp(1.0 - (now - startedAt) / duration, 0.0, 1.0);
   }

   public static boolean expired(long startedAt, long now, double fadeSeconds) {
      return now - startedAt >= fadeSeconds * 1000.0;
   }

   private static int fade(Color color, float multiplier) {
      int alpha = Math.clamp(Math.round(color.getAlpha() * multiplier), 0, 255);
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha).getRGB();
   }
}
