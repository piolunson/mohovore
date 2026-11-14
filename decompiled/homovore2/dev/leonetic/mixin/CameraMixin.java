package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.BetterCamModule;
import dev.leonetic.feature.module.impl.BounceModule;
import dev.leonetic.feature.module.impl.FreecamModule;
import net.minecraft.class_1297;
import net.minecraft.class_1937;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_4184.class)
public abstract class CameraMixin {
   @Shadow
   protected abstract void method_19322(class_243 var1);

   @Shadow
   protected abstract void method_19325(float var1, float var2);

   @Inject(method = "method_19321", at = @At("TAIL"))
   private void homovore$freecamPlacement(class_1937 level, class_1297 entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
      FreecamModule freecam = FreecamModule.active();
      if (freecam != null) {
         this.method_19322(freecam.getCameraPosition(partialTick));
         this.method_19325(freecam.getCameraYaw(partialTick), freecam.getCameraPitch(partialTick));
      } else {
         BounceModule bounce = BounceModule.freeLooking();
         if (bounce != null) {
            this.method_19325(bounce.getCameraYaw(), bounce.getCameraPitch());
         }
      }
   }

   @Inject(method = "method_19333", at = @At("HEAD"), cancellable = true)
   private void homovore$freecamDetached(CallbackInfoReturnable<Boolean> cir) {
      if (FreecamModule.active() != null) {
         cir.setReturnValue(true);
      }
   }

   @ModifyVariable(method = "method_19318", at = @At("HEAD"), argsOnly = true)
   private float homovore$betterCamDistance(float cameraDist) {
      BetterCamModule betterCam = BetterCamModule.active();
      return betterCam == null ? cameraDist : betterCam.distance();
   }

   @Inject(method = "method_19318", at = @At("HEAD"), cancellable = true)
   private void homovore$betterCamClip(float cameraDist, CallbackInfoReturnable<Float> cir) {
      BetterCamModule betterCam = BetterCamModule.active();
      if (betterCam != null && betterCam.clips()) {
         cir.setReturnValue(betterCam.distance());
      }
   }
}
