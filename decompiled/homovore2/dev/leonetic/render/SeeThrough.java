package dev.leonetic.render;

import java.util.Locale;
import net.minecraft.class_1921;

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

   public static boolean isGlint(class_1921 type) {
      if (type == null) {
         return false;
      }

      String name = type.toString().toLowerCase(Locale.ROOT);
      return name.contains("glint") || name.contains("foil");
   }

   public static class_1921 seed(class_1921 original) {
      return original;
   }

   public static class_1921 color(class_1921 original) {
      return original;
   }
}
