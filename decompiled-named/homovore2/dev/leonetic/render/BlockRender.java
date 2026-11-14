package dev.leonetic.render;

import java.awt.Color;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

public final class BlockRender {
   private BlockRender() {
   }

   public static void cuboid(BlockPos pos, Color lineColor, Color sideColor, float lineWidth, float alpha, boolean throughWalls) {
      DrawStyle style = DrawStyle.filledAndStroked(fade(lineColor, alpha), lineWidth, fade(sideColor, alpha));
      VisibilityConfigurable gizmo = GizmoDrawing.box(pos, style);
      if (throughWalls) {
         gizmo.ignoreOcclusion();
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
