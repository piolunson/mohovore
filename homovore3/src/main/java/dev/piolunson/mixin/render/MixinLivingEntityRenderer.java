package dev.piolunson.mixin.render;

import dev.piolunson.Homovore;
import dev.piolunson.manager.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
   @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
   private void homovore$renderMotionRotation(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
      if (entity == MinecraftClient.getInstance().player) {
         RotationManager rm = Homovore.rotationManager;
         if (rm != null && rm.isRotating()) {
            state.bodyYaw = rm.getRenderYaw(partialTicks);
            state.relativeHeadYaw = 0.0F;
            state.pitch = rm.getRenderPitch(partialTicks);
         }
      }
   }
}
