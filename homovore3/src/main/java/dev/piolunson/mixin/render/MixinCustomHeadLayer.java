package dev.piolunson.mixin.render;

import dev.piolunson.features.modules.render.NoRenderModule;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeadFeatureRenderer.class)
public class MixinCustomHeadLayer<S extends LivingEntityRenderState, M extends EntityModel<S> & ModelWithHead> {
   @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
   private void homovore$noPlayerHead(
      MatrixStack poseStack, OrderedRenderCommandQueue collector, int packedLight, S renderState, float f, float g, CallbackInfo ci
   ) {
      if (NoRenderModule.isActive(m -> m.noArmor.getValue()) && renderState.entityType == EntityType.PLAYER) {
         ci.cancel();
      }
   }
}
