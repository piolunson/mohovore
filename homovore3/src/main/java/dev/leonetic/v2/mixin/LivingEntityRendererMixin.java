package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
   @Inject(
      method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$deadEntities(
      LivingEntityRenderState state, MatrixStack poseStack, OrderedRenderCommandQueue collector, CameraRenderState camera, CallbackInfo ci
   ) {
      if (state.deathTime > 0.0F && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.DEAD_ENTITIES)) {
         ci.cancel();
      }
   }

   @Inject(method = "getOverlay", at = @At("HEAD"), cancellable = true)
   private static void homovore$hurt(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
      if (state.hurt && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.HURT)) {
         cir.setReturnValue(OverlayTexture.packUv(OverlayTexture.getU(whiteOverlayProgress), OverlayTexture.getV(false)));
      }
   }
}
