package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkOcclusionDataBuilder.class)
public class VisGraphMixin {
   @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
   private void homovore$caveCulling(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.CAVE_CULLING)) {
         ci.cancel();
      }
   }
}
