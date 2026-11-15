package dev.piolunson.v2.render;

import java.awt.Color;
import net.minecraft.client.gui.DrawContext;

public final class Hover {
   private static final float ATTACK = 0.03F;
   private static final float RELEASE = 0.19F;
   private static final float SPREAD = 0.05F;
   private static final float CUTOFF = 0.004F;
   private static final float FEATHER = 8.0F;
   private float weight;
   private float spread;
   private float origin = 0.5F;
   private boolean inside;

   public float update(boolean hovering, float mouseX, float x1, float x2) {
      if (hovering && !this.inside) {
         this.origin = Math.max(0.0F, Math.min(1.0F, (mouseX - x1) / Math.max(1.0F, x2 - x1)));
      }

      return this.advance(hovering);
   }

   public float update(boolean hovering) {
      this.origin = 0.5F;
      return this.advance(hovering);
   }

   private float advance(boolean hovering) {
      if (hovering && !this.inside) {
         this.spread = 0.0F;
      }

      this.inside = hovering;
      this.weight = Anim.towards(this.weight, hovering ? 1.0F : 0.0F, hovering ? 0.03F : 0.19F, 0.004F);
      if (hovering) {
         this.spread = Anim.towards(this.spread, 1.0F, 0.05F, 0.002F);
      }

      return this.weight;
   }

   public void decay() {
      this.inside = false;
      this.weight = Anim.towards(this.weight, 0.0F, 0.19F, 0.004F);
   }

   public float weight() {
      return this.weight;
   }

   public int mix(int resting, int hovered) {
      return Render2D.mix(resting, hovered, this.weight);
   }

   public void fill(DrawContext graphics, float x1, float y1, float x2, float y2, int color) {
      if (!(this.weight <= 0.004F)) {
         int tint = Render2D.scaleAlpha(color, this.weight);
         float span = x2 - x1;
         float centre = x1 + this.origin * span;
         float reach = this.spread * span * Math.max(this.origin, 1.0F - this.origin);
         float left = centre - reach;
         float right = centre + reach;
         if (left <= x1 && right >= x2) {
            Render2D.fill(graphics, x1, y1, x2, y2, tint);
         } else {
            left = Math.max(x1, left);
            right = Math.min(x2, right);
            if (!(right - left < 1.0F)) {
               float feather = Math.min(8.0F, (right - left) / 2.0F);
               Color solid = new Color(tint, true);
               Color clear = new Color(solid.getRed(), solid.getGreen(), solid.getBlue(), 0);
               if (left > x1) {
                  Render2D.horizontalGradient(graphics, left, y1, left + feather, y2, clear, solid);
                  left += feather;
               }

               if (right < x2) {
                  Render2D.horizontalGradient(graphics, right - feather, y1, right, y2, solid, clear);
                  right -= feather;
               }

               if (right > left) {
                  Render2D.fill(graphics, left, y1, right, y2, tint);
               }
            }
         }
      }
   }
}
