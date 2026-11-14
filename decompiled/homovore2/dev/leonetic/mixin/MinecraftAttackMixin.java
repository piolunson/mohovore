package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.BlockAttackEvent;
import net.minecraft.class_310;
import net.minecraft.class_3965;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_310.class)
public abstract class MinecraftAttackMixin {
   @Inject(method = "method_1536", at = @At("HEAD"), cancellable = true)
   private void homovore$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
      class_310 client = (class_310)this;
      if (client.field_1765 instanceof class_3965 hit) {
         BlockAttackEvent var5 = new BlockAttackEvent(hit.method_17777(), hit.method_17780(), true);
         if (Homovore2Client.EVENT_BUS.post(var5)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(method = "method_1590", at = @At("HEAD"), cancellable = true)
   private void homovore$onContinueAttack(boolean attacking, CallbackInfo ci) {
      if (attacking) {
         class_310 client = (class_310)this;
         if (client.field_1765 instanceof class_3965 hit && Homovore2Client.EVENT_BUS.post(new BlockAttackEvent(hit.method_17777(), hit.method_17780(), false))
            )
          {
            ci.cancel();
         }
      }
   }
}
