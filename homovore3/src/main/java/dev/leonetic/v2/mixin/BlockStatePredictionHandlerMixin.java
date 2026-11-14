package dev.leonetic.v2.mixin;

import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import net.minecraft.client.network.PendingUpdateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PendingUpdateManager.class)
public abstract class BlockStatePredictionHandlerMixin {
   @Shadow
   private int sequence;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void homovore$restartSessionSequence(CallbackInfo ci) {
      NetworkThreadExecutor.resetSequence();
   }

   @Inject(method = "incrementSequence", at = @At("HEAD"))
   private void homovore$shareSessionSequence(CallbackInfoReturnable<PendingUpdateManager> cir) {
      int reserved = NetworkThreadExecutor.reserveSequence();
      if (reserved > 0) {
         this.sequence = reserved - 1;
      }
   }
}
