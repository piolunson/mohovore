package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_7591;
import net.minecraft.class_303.class_7590;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net/minecraft/class_338$1")
public class ChatLineConsumerMixin {
   @Redirect(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_303$class_7590;comp_897()Lnet/minecraft/class_7591;"))
   private class_7591 homovore$messageSignatureIndicator(class_7590 line) {
      return RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.MESSAGE_SIGNATURE_INDICATOR) ? null : line.comp_897();
   }
}
