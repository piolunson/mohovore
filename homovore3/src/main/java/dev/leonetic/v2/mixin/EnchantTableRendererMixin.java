package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.block.entity.EnchantingTableBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantingTableBlockEntityRenderer.class)
public class EnchantTableRendererMixin {
   @Inject(
      method = "render(Lnet/minecraft/client/render/block/entity/state/EnchantingTableBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$enchantTableBook(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.ENCHANTMENT_TABLE_BOOK)) {
         ci.cancel();
      }
   }
}
