package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_758;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(class_758.class)
public class FogRendererMixin {
   @Shadow
   private static boolean field_54018;

   @Redirect(method = "method_71109", at = @At(value = "FIELD", target = "Lnet/minecraft/class_758;field_54018:Z"))
   private static boolean homovore$fog() {
      return field_54018 && !RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.FOG);
   }
}
