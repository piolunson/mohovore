package dev.leonetic.util.render;

import net.minecraft.class_332;

public class ScissorUtil {
   private static int X_1;
   private static int Y_1;
   private static int X_2;
   private static int Y_2;

   public static void enable(class_332 context, int x1, int y1, int x2, int y2) {
      context.method_44379(x1, y1, x2, y2);
      X_1 = x1;
      Y_1 = y1;
      X_2 = x2;
      Y_2 = y2;
   }

   public static void disable(class_332 context) {
      context.method_44380();
   }

   public static void enable(class_332 context) {
      context.method_44379(X_1, Y_1, X_2, Y_2);
   }
}
