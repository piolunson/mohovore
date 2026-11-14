package dev.leonetic.v2.render;

import net.minecraft.client.gui.DrawContext;

public final class Marquee {
   private static final float SPEED = 0.03F;
   private static final long PAUSE = 600L;
   private long startTime;

   public void draw(DrawContext graphics, String text, float x, float y, int color, int maxWidth, boolean hovering) {
      int textWidth = Render2D.width(text);
      if (textWidth <= maxWidth) {
         this.startTime = 0L;
         Render2D.text(graphics, text, x, y, color);
      } else if (!hovering) {
         this.startTime = 0L;
         Render2D.text(graphics, Render2D.trim(text, maxWidth), x, y, color);
      } else {
         if (this.startTime == 0L) {
            this.startTime = System.currentTimeMillis();
         }

         float overflow = textWidth - maxWidth;
         long duration = (long)(overflow / 0.03F);
         long cycle = 600L + duration + 600L + duration;
         long t = (System.currentTimeMillis() - this.startTime) % Math.max(1L, cycle);
         float offset;
         if (t < 600L) {
            offset = 0.0F;
         } else if (t < 600L + duration) {
            offset = (float)(t - 600L) * 0.03F;
         } else if (t < 600L + duration + 600L) {
            offset = overflow;
         } else {
            offset = overflow - (float)(t - 600L - duration - 600L) * 0.03F;
         }

         float pad = Render2D.lineHeight() * 2.0F;
         Render2D.scissor(graphics, x, y - pad, x + maxWidth + 2.0F, y + pad);
         Render2D.text(graphics, text, x - offset, y, color);
         Render2D.unscissor(graphics);
      }
   }

   public void reset() {
      this.startTime = 0L;
   }
}
