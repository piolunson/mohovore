package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameOverlayRenderer.class)
public class ScreenEffectRendererMixin {
   @Invoker("renderFireOverlay")
   private static void homovore$invokeRenderFire(MatrixStack poseStack, VertexConsumerProvider buffers, Sprite sprite) {
      throw new AssertionError();
   }

   @Invoker("renderUnderwaterOverlay")
   private static void homovore$invokeRenderWater(MinecraftClient client, MatrixStack poseStack, VertexConsumerProvider buffers) {
      throw new AssertionError();
   }

   @Invoker("renderInWallOverlay")
   private static void homovore$invokeRenderTex(Sprite sprite, MatrixStack poseStack, VertexConsumerProvider buffers) {
      throw new AssertionError();
   }

   @Redirect(
      method = "renderOverlays",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V"
      )
   )
   private static void homovore$fireOverlay(MatrixStack poseStack, VertexConsumerProvider buffers, Sprite sprite) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.FIRE_OVERLAY)) {
         homovore$invokeRenderFire(poseStack, buffers, sprite);
      }
   }

   @Redirect(
      method = "renderOverlays",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderUnderwaterOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"
      )
   )
   private static void homovore$liquidOverlay(MinecraftClient client, MatrixStack poseStack, VertexConsumerProvider buffers) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.LIQUID_OVERLAY)) {
         homovore$invokeRenderWater(client, poseStack, buffers);
      }
   }

   @Redirect(
      method = "renderOverlays",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/InGameOverlayRenderer;renderInWallOverlay(Lnet/minecraft/client/texture/Sprite;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"
      )
   )
   private static void homovore$inWallOverlay(Sprite sprite, MatrixStack poseStack, VertexConsumerProvider buffers) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.IN_WALL_OVERLAY)) {
         homovore$invokeRenderTex(sprite, poseStack, buffers);
      }
   }
}
