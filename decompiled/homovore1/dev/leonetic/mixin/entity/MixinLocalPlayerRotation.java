package dev.leonetic.mixin.entity;

import dev.leonetic.Homovore;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_3532;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = class_746.class, priority = Integer.MAX_VALUE)
public class MixinLocalPlayerRotation {
   @Shadow
   private float field_3925;
   @Unique
   private float homovore$savedYaw;
   @Unique
   private float homovore$savedPitch;
   @Unique
   private boolean homovore$spoofed;

   @Inject(method = "method_3136", at = @At("HEAD"))
   private void homovore$spoofRotationHead(CallbackInfo ci) {
      this.homovore$spoofed = false;
      RotationManager rm = Homovore.rotationManager;
      if (rm != null) {
         boolean motion = rm.isRotating();
         boolean silent = rm.isSilentSyncRequired();
         if (motion || silent) {
            this.homovore$savedYaw = Util.mc.field_1724.method_36454();
            this.homovore$savedPitch = Util.mc.field_1724.method_36455();
            float outYaw = this.homovore$savedYaw;
            float outPitch = this.homovore$savedPitch;
            if (motion) {
               outYaw = rm.getRotationYaw();
               outPitch = rm.getRotationPitch();
               rm.setServerDeltaYaw(outYaw - rm.getServerYaw());
               rm.setServerYaw(outYaw);
               rm.setServerPitch(outPitch);
            } else {
               this.field_3925 -= 4.0F;
               float f = (float)((Math.random() * 2.0 - 1.0) * 0.001F);
               outPitch = class_3532.method_15363(outPitch + f, -90.0F, 90.0F);
            }

            Util.mc.field_1724.method_36456(outYaw);
            Util.mc.field_1724.method_36457(outPitch);
            this.homovore$spoofed = true;
         }
      }
   }

   @Inject(method = "method_3136", at = @At("TAIL"))
   private void homovore$spoofRotationTail(CallbackInfo ci) {
      RotationManager rm = Homovore.rotationManager;
      if (rm != null) {
         if (rm.isRotating()) {
            rm.setServerYaw(Util.mc.field_1724.method_36454());
            rm.setServerPitch(Util.mc.field_1724.method_36455());
         }

         if (this.homovore$spoofed) {
            Util.mc.field_1724.method_36456(this.homovore$savedYaw);
            Util.mc.field_1724.method_36457(this.homovore$savedPitch);
            this.homovore$spoofed = false;
         }

         rm.setSilentSyncRequired(false);
         rm.resetSilentTick();
      }
   }
}
