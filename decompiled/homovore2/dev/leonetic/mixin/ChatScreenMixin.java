package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.ChatSendEvent;
import dev.leonetic.feature.command.CommandManager;
import net.minecraft.class_310;
import net.minecraft.class_408;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_408.class)
public class ChatScreenMixin {
   @Inject(method = "method_44056", at = @At("HEAD"), cancellable = true)
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
            class_310.method_1551().field_1705.method_1743().method_1803(input);
         }

         ci.cancel();
      }
   }
}
