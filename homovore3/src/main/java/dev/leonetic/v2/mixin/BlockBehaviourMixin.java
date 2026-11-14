package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public class BlockBehaviourMixin {
   @Inject(method = "getRenderingSeed", at = @At("HEAD"), cancellable = true)
   private void homovore$textureRotations(CallbackInfoReturnable<Long> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.TEXTURE_ROTATIONS)) {
         cir.setReturnValue(0L);
      }
   }
}
