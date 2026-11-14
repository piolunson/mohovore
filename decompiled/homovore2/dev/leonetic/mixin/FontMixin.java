package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_2583;
import net.minecraft.class_327;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(class_327.class)
public class FontMixin {
   @Redirect(method = "method_72731", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2583;method_10987()Z"))
   private boolean homovore$obfuscation(class_2583 style) {
      return style.method_10987() && !RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.OBFUSCATION);
   }
}
