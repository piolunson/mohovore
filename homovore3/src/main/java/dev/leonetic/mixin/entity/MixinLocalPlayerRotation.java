package dev.leonetic.mixin.entity;

import dev.leonetic.Homovore;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPlayerEntity.class, priority = Integer.MAX_VALUE)
public class MixinLocalPlayerRotation {
   @Shadow
   private float lastPitchClient;
   @Unique
   private float homovore$savedYaw;
   @Unique
   private float homovore$savedPitch;
   @Unique
   private boolean homovore$spoofed;

   @Inject(method = "sendMovementPackets", at = @At("HEAD"))
   private void homovore$spoofRotationHead(CallbackInfo ci) {
      this.homovore$spoofed = false;
      RotationManager rm = Homovore.rotationManager;
      if (rm != null) {
         boolean motion = rm.isRotating();
         boolean silent = rm.isSilentSyncRequired();
         if (motion || silent) {
            this.homovore$savedYaw = Util.mc.player.getYaw();
            this.homovore$savedPitch = Util.mc.player.getPitch();
            float outYaw = this.homovore$savedYaw;
            float outPitch = this.homovore$savedPitch;
            if (motion) {
               outYaw = rm.getRotationYaw();
               outPitch = rm.getRotationPitch();
               rm.setServerDeltaYaw(outYaw - rm.getServerYaw());
               rm.setServerYaw(outYaw);
               rm.setServerPitch(outPitch);
            } else {
               this.lastPitchClient -= 4.0F;
               float f = (float)((Math.random() * 2.0 - 1.0) * 0.001F);
               outPitch = MathHelper.clamp(outPitch + f, -90.0F, 90.0F);
            }

            Util.mc.player.setYaw(outYaw);
            Util.mc.player.setPitch(outPitch);
            this.homovore$spoofed = true;
         }
      }
   }

   @Inject(method = "sendMovementPackets", at = @At("TAIL"))
   private void homovore$spoofRotationTail(CallbackInfo ci) {
      RotationManager rm = Homovore.rotationManager;
      if (rm != null) {
         if (rm.isRotating()) {
            rm.setServerYaw(Util.mc.player.getYaw());
            rm.setServerPitch(Util.mc.player.getPitch());
         }

         if (this.homovore$spoofed) {
            Util.mc.player.setYaw(this.homovore$savedYaw);
            Util.mc.player.setPitch(this.homovore$savedPitch);
            this.homovore$spoofed = false;
         }

         rm.setSilentSyncRequired(false);
         rm.resetSilentTick();
      }
   }
}
