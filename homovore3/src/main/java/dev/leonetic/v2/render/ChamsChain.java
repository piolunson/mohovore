package dev.leonetic.v2.render;

import java.util.Set;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

public final class ChamsChain {
   private ChamsChain() {
   }

   public static PostEffectProcessor get(Set<Identifier> externalTargets, ChamsChain.Style style) {
      return null;
   }

   public static void cleanup() {
   }

   public enum Fill {
      SOLID("post/chams_composite"),
      PHANTOM("post/chams_composite_phantom"),
      TURBULENCE("post/chams_composite_turbulence"),
      TRANS("post/chams_composite_trans");

      private final String path;

      Fill(String path) {
         this.path = path;
      }
   }

   public record Style(int outlineWidth, float outlineAlpha, int glowRadius, float glowIntensity, float fillAlpha, ChamsChain.Fill fill, float fillSpeed) {
      public int reach() {
         return Math.max(this.outlineWidth, this.glowRadius);
      }

      public boolean isVisible() {
         return this.fillAlpha > 0.0F || this.outlineWidth > 0 && this.outlineAlpha > 0.0F || this.glowRadius > 0 && this.glowIntensity > 0.0F;
      }
   }
}
