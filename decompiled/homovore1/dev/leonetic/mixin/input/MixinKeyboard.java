package dev.leonetic.mixin.input;

import dev.leonetic.event.impl.input.KeyInputEvent;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_11908;
import net.minecraft.class_309;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_309.class)
public class MixinKeyboard {
   @Inject(method = "method_1466", at = @At("HEAD"), cancellable = true)
   private void onKey(long window, int action, class_11908 input, CallbackInfo ci) {
      if (action == 0 || action == 1) {
         if (action == 1 && Util.EVENT_BUS.post(new KeyInputEvent(input.comp_4795(), action))) {
            ci.cancel();
         } else if (action == 0) {
            Util.EVENT_BUS.post(new KeyInputEvent(input.comp_4795(), action));
         }
      }
   }
}
