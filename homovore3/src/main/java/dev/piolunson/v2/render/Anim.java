package dev.piolunson.v2.render;

public final class Anim {
   private static final float LN2 = 0.6931472F;
   private static long lastFrame = System.nanoTime();
   private static float delta;

   private Anim() {
   }

   public static void beginFrame() {
      long now = System.nanoTime();
      delta = Math.min(0.1F, (float)(now - lastFrame) / 1.0E9F);
      lastFrame = now;
   }

   public static float delta() {
      return delta;
   }

   public static float towards(float current, float target, float halfLife) {
      return towards(current, target, halfLife, 0.05F);
   }

   public static float towards(float current, float target, float halfLife, float epsilon) {
      if (halfLife <= 0.0F) {
         return target;
      }

      float factor = 1.0F - (float)Math.exp(-delta * (0.6931472F / halfLife));
      float next = current + (target - current) * factor;
      return Math.abs(target - next) < epsilon ? target : next;
   }
}
