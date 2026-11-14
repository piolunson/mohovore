package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.BindPressEvent;
import dev.leonetic.util.input.InputTimes;
import net.minecraft.class_11908;
import net.minecraft.class_309;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_309.class)
public class KeyboardHandlerMixin {
   @Inject(method = "method_1466", at = @At("HEAD"))
   private void homovore$stampPress(long handle, int action, class_11908 event, CallbackInfo ci) {
      if (action == 1) {
         InputTimes.pressed(event.comp_4795());
         Homovore2Client.EVENT_BUS.post(new BindPressEvent(event.comp_4795()));
      }
   }
}
