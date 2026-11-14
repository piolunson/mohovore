package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_757;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_757.class)
public class GameRendererMixin {
   @Inject(method = "method_3189", at = @At("HEAD"), cancellable = true)
   private void homovore$totemAnimation(class_1799 stack, CallbackInfo ci) {
      if (stack.method_31574(class_1802.field_8288) && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.TOTEM_ANIMATION)) {
         ci.cancel();
      }
   }

   @Redirect(method = "method_3188", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
   private float homovore$nauseaWarp(float a, float b) {
      return RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.NAUSEA) ? 0.0F : Math.max(a, b);
   }
}
