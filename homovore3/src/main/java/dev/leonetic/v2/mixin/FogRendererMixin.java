package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
   @Shadow
   private static boolean fogEnabled;

   @Redirect(method = "getFogBuffer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/fog/FogRenderer;fogEnabled:Z"))
   private static boolean homovore$fog() {
      return fogEnabled && !RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.FOG);
   }
}
