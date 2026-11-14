package dev.leonetic.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.util.memory.ObjectPool;

public final class HandShaderRender {
   private static SimpleFramebuffer handTarget;
   private static int width;
   private static int height;
   private static OutlineVertexConsumerProvider outline;
   private static boolean captureOpen;
   private static boolean hasCapture;
   private static final ObjectPool RESOURCE_POOL = new ObjectPool(3);
   public static boolean capturePaused;

   private HandShaderRender() {
   }

   private static boolean ensureTarget() {
      MinecraftClient mc = MinecraftClient.getInstance();
      int w = mc.getWindow().getFramebufferWidth();
      int h = mc.getWindow().getFramebufferHeight();
      if (w > 0 && h > 0) {
         if (handTarget == null) {
            handTarget = new SimpleFramebuffer("homovore_hand_shader", w, h, true);
            width = w;
            height = h;
         } else if (w != width || h != height) {
            handTarget.resize(w, h);
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

      RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(handTarget.getColorAttachment(), 0, handTarget.getDepthAttachment(), 1.0);
      if (outline == null) {
         outline = new OutlineVertexConsumerProvider();
      }

      captureOpen = true;
      hasCapture = false;
      return new HandSilhouetteCollector(outline, rgb);
   }

   public static void flush() {
      if (captureOpen && outline != null && handTarget != null) {
         RenderSystem.outputColorTextureOverride = handTarget.getColorAttachmentView();
         RenderSystem.outputDepthTextureOverride = handTarget.getDepthAttachmentView();

         try {
            outline.draw();
            hasCapture = true;
         } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            captureOpen = false;
         }
      }
   }

   public static void composite(PostEffectProcessor chain) {
      if (hasCapture && handTarget != null && chain != null) {
         hasCapture = false;
         chain.render(handTarget, RESOURCE_POOL);
         handTarget.drawBlit(MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView());
         RESOURCE_POOL.decrementLifespan();
      }
   }
}
