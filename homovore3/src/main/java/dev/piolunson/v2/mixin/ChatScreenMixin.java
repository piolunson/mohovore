package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.ChatSendEvent;
import dev.piolunson.v2.feature.command.CommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
   @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
   private void homovore$runCommand(String message, boolean addToRecentChat, CallbackInfo ci) {
      CommandManager commands = Homovore2Client.commands();
      if (commands != null) {
         String input = message.trim();
         if (!commands.isCommand(input)) {
            if (input.isEmpty() || input.startsWith("/")) {
               return;
            }

            ChatSendEvent event = new ChatSendEvent(input);
            Homovore2Client.EVENT_BUS.post(event);
            if (!event.isCancelled()) {
               return;
            }
         } else {
            commands.dispatch(input);
         }

         if (addToRecentChat) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(input);
         }

         ci.cancel();
      }
   }
}
