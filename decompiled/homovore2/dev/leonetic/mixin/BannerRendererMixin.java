package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_823;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_823.class)
public class BannerRendererMixin {
   @Inject(
      method = "method_3546(Lnet/minecraft/class_11949;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_12075;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$banner(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BANNERS)) {
         ci.cancel();
      }
   }

   @Inject(
      method = "method_3546(Lnet/minecraft/class_11949;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_12075;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_823;method_65555(Lnet/minecraft/class_11701;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;IIFLnet/minecraft/class_10378;Lnet/minecraft/class_10377;FLnet/minecraft/class_1767;Lnet/minecraft/class_9307;Lnet/minecraft/class_11683$class_11792;I)V"
      ),
      cancellable = true
   )
   private void homovore$bannerPatterns(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BANNER_PATTERNS)) {
         ci.cancel();
      }
   }
}
