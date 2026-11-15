package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.BindPressEvent;
import dev.piolunson.v2.util.input.InputTimes;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardHandlerMixin {
   @Inject(method = "onKey", at = @At("HEAD"))
   private void homovore$stampPress(long handle, int action, KeyInput event, CallbackInfo ci) {
      if (action == 1) {
         InputTimes.pressed(event.key());
         Homovore2Client.EVENT_BUS.post(new BindPressEvent(event.key()));
      }
   }
}
