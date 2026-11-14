package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_852;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_852.class)
public class VisGraphMixin {
   @Inject(method = "method_3682", at = @At("HEAD"), cancellable = true)
   private void homovore$caveCulling(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.CAVE_CULLING)) {
         ci.cancel();
      }
   }
}
