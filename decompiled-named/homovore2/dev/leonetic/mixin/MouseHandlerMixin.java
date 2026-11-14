package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.BindPressEvent;
import dev.leonetic.feature.module.impl.BetterCamModule;
import dev.leonetic.feature.module.impl.BounceModule;
import dev.leonetic.feature.module.impl.FreecamModule;
import dev.leonetic.util.input.InputTimes;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseHandlerMixin {
   @Redirect(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
   private void homovore$freecamTurn(ClientPlayerEntity player, double yaw, double pitch) {
      FreecamModule freecam = FreecamModule.active();
      if (freecam != null) {
         freecam.turnCamera(yaw, pitch);
      } else {
         BounceModule bounce = BounceModule.freeLooking();
         if (bounce != null) {
            bounce.turnCamera(yaw, pitch);
         } else {
            player.changeLookDirection(yaw, pitch);
         }
      }
   }

   @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
   private void homovore$betterCamScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
      BetterCamModule betterCam = BetterCamModule.active();
      if (betterCam != null && betterCam.onScroll(yOffset)) {
         ci.cancel();
      }
   }

   @Inject(method = "onMouseButton", at = @At("HEAD"))
   private void homovore$stampPress(long handle, MouseInput info, int action, CallbackInfo ci) {
      if (action == 1) {
         int key = 1000 + info.button();
         InputTimes.pressed(key);
         Homovore2Client.EVENT_BUS.post(new BindPressEvent(key));
      }
   }
}
