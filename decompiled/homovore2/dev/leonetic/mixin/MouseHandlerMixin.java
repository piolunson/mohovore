package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.BindPressEvent;
import dev.leonetic.feature.module.impl.BetterCamModule;
import dev.leonetic.feature.module.impl.BounceModule;
import dev.leonetic.feature.module.impl.FreecamModule;
import dev.leonetic.util.input.InputTimes;
import net.minecraft.class_11910;
import net.minecraft.class_312;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_312.class)
public class MouseHandlerMixin {
   @Redirect(method = "method_1606", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_746;method_5872(DD)V"))
   private void homovore$freecamTurn(class_746 player, double yaw, double pitch) {
      FreecamModule freecam = FreecamModule.active();
      if (freecam != null) {
         freecam.turnCamera(yaw, pitch);
      } else {
         BounceModule bounce = BounceModule.freeLooking();
         if (bounce != null) {
            bounce.turnCamera(yaw, pitch);
         } else {
            player.method_5872(yaw, pitch);
         }
      }
   }

   @Inject(method = "method_1598", at = @At("HEAD"), cancellable = true)
   private void homovore$betterCamScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
      BetterCamModule betterCam = BetterCamModule.active();
      if (betterCam != null && betterCam.onScroll(yOffset)) {
         ci.cancel();
      }
   }

   @Inject(method = "method_1601", at = @At("HEAD"))
   private void homovore$stampPress(long handle, class_11910 info, int action, CallbackInfo ci) {
      if (action == 1) {
         int key = 1000 + info.comp_4801();
         InputTimes.pressed(key);
         Homovore2Client.EVENT_BUS.post(new BindPressEvent(key));
      }
   }
}
