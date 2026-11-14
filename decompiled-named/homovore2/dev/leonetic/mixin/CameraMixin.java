package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.BetterCamModule;
import dev.leonetic.feature.module.impl.BounceModule;
import dev.leonetic.feature.module.impl.FreecamModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   protected abstract void setPos(Vec3d var1);

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Inject(method = "update", at = @At("TAIL"))
   private void homovore$freecamPlacement(World level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
      FreecamModule freecam = FreecamModule.active();
      if (freecam != null) {
         this.setPos(freecam.getCameraPosition(partialTick));
         this.setRotation(freecam.getCameraYaw(partialTick), freecam.getCameraPitch(partialTick));
      } else {
         BounceModule bounce = BounceModule.freeLooking();
         if (bounce != null) {
            this.setRotation(bounce.getCameraYaw(), bounce.getCameraPitch());
         }
      }
   }

   @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
   private void homovore$freecamDetached(CallbackInfoReturnable<Boolean> cir) {
      if (FreecamModule.active() != null) {
         cir.setReturnValue(true);
      }
   }

   @ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
   private float homovore$betterCamDistance(float cameraDist) {
      BetterCamModule betterCam = BetterCamModule.active();
      return betterCam == null ? cameraDist : betterCam.distance();
   }

   @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
   private void homovore$betterCamClip(float cameraDist, CallbackInfoReturnable<Float> cir) {
      BetterCamModule betterCam = BetterCamModule.active();
      if (betterCam != null && betterCam.clips()) {
         cir.setReturnValue(betterCam.distance());
      }
   }
}
