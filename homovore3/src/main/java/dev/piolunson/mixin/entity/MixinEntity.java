package dev.piolunson.mixin.entity;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.movement.VelocityModule;
import dev.piolunson.features.modules.render.ShadersModule;
import dev.piolunson.manager.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {
   @Inject(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
   private void homovore$spoofLookAngle(CallbackInfoReturnable<Vec3d> cir) {
      Entity self = (Entity)(Object)this;
      if (self == MinecraftClient.getInstance().player) {
         RotationManager rm = Homovore.rotationManager;
         if (rm != null && rm.isMoveFixEnabled() && rm.isRotating()) {
            cir.setReturnValue(self.getRotationVector(rm.getRotationPitch(), rm.getRotationYaw()));
         }
      }
   }

   @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      Entity self = (Entity)(Object)this;
      ShadersModule shaders = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
   private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
      Entity self = (Entity)(Object)this;
      ShadersModule shaders = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
         cir.setReturnValue(shaders.getRgbFor(self));
      }
   }

   @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void cancelEntityPush(Entity other, CallbackInfo ci) {
      VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
      if (velocity != null && velocity.isEnabled() && velocity.entityPush.getValue() && velocity.phaseConditionMet()) {
         ClientPlayerEntity localPlayer = MinecraftClient.getInstance().player;
         if (localPlayer != null) {
            Object self = this;
            if (self == localPlayer || other == localPlayer) {
               ci.cancel();
            }
         }
      }
   }
}
