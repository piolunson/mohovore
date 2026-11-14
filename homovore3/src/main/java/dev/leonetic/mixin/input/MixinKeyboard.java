package dev.leonetic.mixin.input;

import dev.leonetic.event.impl.input.KeyInputEvent;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {
   @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
   private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
      if (action == 0 || action == 1) {
         if (action == 1 && Util.EVENT_BUS.post(new KeyInputEvent(input.key(), action))) {
            ci.cancel();
         } else if (action == 0) {
            Util.EVENT_BUS.post(new KeyInputEvent(input.key(), action));
         }
      }
   }
}
