package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.PlayerInputEvent;
import dev.piolunson.v2.feature.module.impl.BounceModule;
import dev.piolunson.v2.feature.module.impl.FreecamModule;
import dev.piolunson.v2.feature.module.impl.VelocityModule;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class LocalPlayerMixin {
   @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = Shift.AFTER))
   private void homovore$fixMovementInput(CallbackInfo ci) {
      ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
      FreecamModule.suppressPlayerInput(player);
      BounceModule.suppressMovementInput(player);
      Homovore2Client.EVENT_BUS.post(new PlayerInputEvent());
      Homovore2Client.rotations().beginMovementUpdate(player);
      Homovore2Client.rotations().fixMovementInput(player);
      VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
      if (velocity != null) {
         velocity.applyGrimCounterInput(player);
      }
   }

   @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
   private void homovore$cancelBlockPush(double x, double z, CallbackInfo ci) {
      VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
      if (velocity != null && velocity.shouldCancelBlockPush()) {
         ci.cancel();
      }
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void homovore$endMovementUpdate(CallbackInfo ci) {
      Homovore2Client.rotations().endMovementUpdate((ClientPlayerEntity)(Object)this);
   }
}
