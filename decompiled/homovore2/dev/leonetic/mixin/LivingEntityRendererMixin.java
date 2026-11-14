package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_10042;
import net.minecraft.class_11659;
import net.minecraft.class_12075;
import net.minecraft.class_4587;
import net.minecraft.class_4608;
import net.minecraft.class_922;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_922.class)
public class LivingEntityRendererMixin {
   @Inject(
      method = "method_4054(Lnet/minecraft/class_10042;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_12075;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$deadEntities(class_10042 state, class_4587 poseStack, class_11659 collector, class_12075 camera, CallbackInfo ci) {
      if (state.field_53449 > 0.0F && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.DEAD_ENTITIES)) {
         ci.cancel();
      }
   }

   @Inject(method = "method_23622", at = @At("HEAD"), cancellable = true)
   private static void homovore$hurt(class_10042 state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
      if (state.field_53460 && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.HURT)) {
         cir.setReturnValue(class_4608.method_23625(class_4608.method_23210(whiteOverlayProgress), class_4608.method_23212(false)));
      }
   }
}
