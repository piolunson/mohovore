package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.block.AbstractBlock.AbstractBlockState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlockState.class)
public class BlockStateBaseMixin {
   @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true)
   private void homovore$textureRotations(CallbackInfoReturnable<Vec3d> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.TEXTURE_ROTATIONS)) {
         cir.setReturnValue(Vec3d.ZERO);
      }
   }
}
