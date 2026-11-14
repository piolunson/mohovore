package dev.leonetic.mixin;

import dev.leonetic.util.network.NetworkThreadExecutor;
import net.minecraft.class_7202;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_7202.class)
public abstract class BlockStatePredictionHandlerMixin {
   @Shadow
   private int field_37954;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void homovore$restartSessionSequence(CallbackInfo ci) {
      NetworkThreadExecutor.resetSequence();
   }

   @Inject(method = "method_41937", at = @At("HEAD"))
   private void homovore$shareSessionSequence(CallbackInfoReturnable<class_7202> cir) {
      int reserved = NetworkThreadExecutor.reserveSequence();
      if (reserved > 0) {
         this.field_37954 = reserved - 1;
      }
   }
}
