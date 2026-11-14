package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_243;
import net.minecraft.class_4970.class_4971;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_4971.class)
public class BlockStateBaseMixin {
   @Inject(method = "method_26226", at = @At("HEAD"), cancellable = true)
   private void homovore$textureRotations(CallbackInfoReturnable<class_243> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.TEXTURE_ROTATIONS)) {
         cir.setReturnValue(class_243.field_1353);
      }
   }
}
