package dev.leonetic.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.class_279;
import net.minecraft.class_310;
import net.minecraft.class_4618;
import net.minecraft.class_6367;
import net.minecraft.class_9920;

public final class HandShaderRender {
   private static class_6367 handTarget;
   private static int width;
   private static int height;
   private static class_4618 outline;
   private static boolean captureOpen;
   private static boolean hasCapture;
   private static final class_9920 RESOURCE_POOL = new class_9920(3);
   public static boolean capturePaused;

   private HandShaderRender() {
   }

   private static boolean ensureTarget() {
      class_310 mc = class_310.method_1551();
      int w = mc.method_22683().method_4489();
      int h = mc.method_22683().method_4506();
      if (w > 0 && h > 0) {
         if (handTarget == null) {
            handTarget = new class_6367("homovore_hand_shader", w, h, true);
            width = w;
            height = h;
         } else if (w != width || h != height) {
            handTarget.method_1234(w, h);
            width = w;
            height = h;
         }

         return true;
      } else {
         return false;
      }
   }

   public static HandSilhouetteCollector beginCapture(int rgb) {
      if (!ensureTarget()) {
         return null;
      }

      RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(handTarget.method_30277(), 0, handTarget.method_30278(), 1.0);
      if (outline == null) {
         outline = new class_4618();
      }

      captureOpen = true;
      hasCapture = false;
      return new HandSilhouetteCollector(outline, rgb);
   }

   public static void flush() {
      if (captureOpen && outline != null && handTarget != null) {
         RenderSystem.outputColorTextureOverride = handTarget.method_71639();
         RenderSystem.outputDepthTextureOverride = handTarget.method_71640();

         try {
            outline.method_23285();
            hasCapture = true;
         } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            captureOpen = false;
         }
      }
   }

   public static void composite(class_279 chain) {
      if (hasCapture && handTarget != null && chain != null) {
         hasCapture = false;
         chain.method_1258(handTarget, RESOURCE_POOL);
         handTarget.method_68445(class_310.method_1551().method_1522().method_71639());
         RESOURCE_POOL.method_61947();
      }
   }
}
