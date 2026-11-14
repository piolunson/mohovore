package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_4970;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_4970.class)
public class BlockBehaviourMixin {
   @Inject(method = "method_9535", at = @At("HEAD"), cancellable = true)
   private void homovore$textureRotations(CallbackInfoReturnable<Long> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.TEXTURE_ROTATIONS)) {
         cir.setReturnValue(0L);
      }
   }
}
