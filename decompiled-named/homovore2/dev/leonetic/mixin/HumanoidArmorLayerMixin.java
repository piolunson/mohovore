package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class HumanoidArmorLayerMixin {
   @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
   private void homovore$armor(
      MatrixStack poseStack, OrderedRenderCommandQueue collector, int lightCoords, BipedEntityRenderState state, float yRot, float xRot, CallbackInfo ci
   ) {
      if (state instanceof PlayerEntityRenderState && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.ARMOR)) {
         ci.cancel();
      }
   }
}
