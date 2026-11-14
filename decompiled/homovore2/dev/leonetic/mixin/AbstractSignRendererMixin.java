package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_10529;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_10529.class)
public class AbstractSignRendererMixin {
   @Inject(method = "method_65828", at = @At("HEAD"), cancellable = true)
   private void homovore$signText(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.SIGN_TEXT)) {
         ci.cancel();
      }
   }
}
