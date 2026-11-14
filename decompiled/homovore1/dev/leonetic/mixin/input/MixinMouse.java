package dev.leonetic.mixin.input;

import dev.leonetic.event.impl.input.MouseInputEvent;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_11910;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_312.class)
public class MixinMouse {
   @Inject(method = "method_1601", at = @At("HEAD"), cancellable = true)
   private void onMouseButton(long window, class_11910 input, int action, CallbackInfo ci) {
      if (Util.EVENT_BUS.post(new MouseInputEvent(input.comp_4801(), action))) {
         ci.cancel();
      }
   }
}
