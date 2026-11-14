package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerBlockEntityRenderer.class)
public class BannerRendererMixin {
   @Inject(
      method = "render(Lnet/minecraft/client/render/block/entity/state/BannerBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$banner(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BANNERS)) {
         ci.cancel();
      }
   }

   @Inject(
      method = "render(Lnet/minecraft/client/render/block/entity/state/BannerBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/block/entity/BannerBlockEntityRenderer;render(Lnet/minecraft/client/texture/SpriteHolder;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIFLnet/minecraft/client/render/block/entity/model/BannerBlockModel;Lnet/minecraft/client/render/block/entity/model/BannerFlagBlockModel;FLnet/minecraft/util/DyeColor;Lnet/minecraft/component/type/BannerPatternsComponent;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;I)V"
      ),
      cancellable = true
   )
   private void homovore$bannerPatterns(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BANNER_PATTERNS)) {
         ci.cancel();
      }
   }
}
