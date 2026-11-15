package dev.piolunson.v2.mixin;

import dev.piolunson.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net/minecraft/client/gui/hud/ChatHud$1")
public class ChatLineConsumerMixin {
   @Redirect(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;comp_897()Lnet/minecraft/client/gui/hud/MessageIndicator;"))
   private MessageIndicator homovore$messageSignatureIndicator(Visible line) {
      return RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.MESSAGE_SIGNATURE_INDICATOR) ? null : line.indicator();
   }
}
