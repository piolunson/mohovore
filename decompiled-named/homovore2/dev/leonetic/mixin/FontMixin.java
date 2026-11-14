package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TextRenderer.class)
public class FontMixin {
   @Redirect(method = "getGlyph", at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Style;isObfuscated()Z"))
   private boolean homovore$obfuscation(Style style) {
      return style.isObfuscated() && !RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.OBFUSCATION);
   }
}
