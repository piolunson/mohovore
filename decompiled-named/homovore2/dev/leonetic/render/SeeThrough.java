package dev.leonetic.render;

import java.util.Locale;
import net.minecraft.client.render.RenderLayer;

public final class SeeThrough {
   public static final int SEED_ORDER = 100000;
   public static final int COLOR_ORDER = 200000;
   public static boolean active;
   public static boolean cloning;
   public static int subOrder;

   private SeeThrough() {
   }

   public static void begin() {
      active = true;
      subOrder = 0;
   }

   public static void end() {
      active = false;
   }

   public static boolean isGlint(RenderLayer type) {
      if (type == null) {
         return false;
      }

      String name = type.toString().toLowerCase(Locale.ROOT);
      return name.contains("glint") || name.contains("foil");
   }

   public static RenderLayer seed(RenderLayer original) {
      return original;
   }

   public static RenderLayer color(RenderLayer original) {
      return original;
   }
}
