package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.BlockAttackEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftAttackMixin {
   @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
   private void homovore$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
      MinecraftClient client = (MinecraftClient)(Object)this;
      if (client.crosshairTarget instanceof BlockHitResult hit) {
         BlockAttackEvent var5 = new BlockAttackEvent(hit.getBlockPos(), hit.getSide(), true);
         if (Homovore2Client.EVENT_BUS.post(var5)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
   private void homovore$onContinueAttack(boolean attacking, CallbackInfo ci) {
      if (attacking) {
         MinecraftClient client = (MinecraftClient)(Object)this;
         if (client.crosshairTarget instanceof BlockHitResult hit
            && Homovore2Client.EVENT_BUS.post(new BlockAttackEvent(hit.getBlockPos(), hit.getSide(), false))) {
            ci.cancel();
         }
      }
   }
}
