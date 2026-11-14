package dev.leonetic.mixin.render;

import dev.leonetic.event.impl.render.RenderBlockOutlineEvent;
import dev.leonetic.features.modules.render.NoRenderModule;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class MixinLevelRenderer {
   @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
   public void renderBlockOutline(Immediate bufferSource, MatrixStack poseStack, boolean bl, WorldRenderState levelRenderState, CallbackInfo ci) {
      if (Util.EVENT_BUS.post(new RenderBlockOutlineEvent())) {
         ci.cancel();
      }
   }

   @Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
   private void homovore$noDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
      if (NoRenderModule.isActive(m -> m.noDarkness.getValue())) {
         cir.setReturnValue(false);
      }
   }
}
