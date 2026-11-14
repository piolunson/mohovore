package dev.leonetic.mixin.entity;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.movement.VelocityModule;
import dev.leonetic.features.modules.render.ShadersModule;
import dev.leonetic.manager.RotationManager;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1297.class)
public class MixinEntity {
   @Inject(method = "method_5720", at = @At("HEAD"), cancellable = true)
   private void homovore$spoofLookAngle(CallbackInfoReturnable<class_243> cir) {
      class_1297 self = (class_1297)this;
      if (self == class_310.method_1551().field_1724) {
         RotationManager rm = Homovore.rotationManager;
         if (rm != null && rm.isMoveFixEnabled() && rm.isRotating()) {
            cir.setReturnValue(self.method_5631(rm.getRotationPitch(), rm.getRotationYaw()));
         }
      }
   }

   @Inject(method = "method_5851", at = @At("RETURN"), cancellable = true)
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      class_1297 self = (class_1297)this;
      ShadersModule shaders = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "method_22861", at = @At("RETURN"), cancellable = true)
   private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
      class_1297 self = (class_1297)this;
      ShadersModule shaders = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
         cir.setReturnValue(shaders.getRgbFor(self));
      }
   }

   @Inject(method = "method_5697(Lnet/minecraft/class_1297;)V", at = @At("HEAD"), cancellable = true)
   private void cancelEntityPush(class_1297 other, CallbackInfo ci) {
      VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
      if (velocity != null && velocity.isEnabled() && velocity.entityPush.getValue() && velocity.phaseConditionMet()) {
         class_746 localPlayer = class_310.method_1551().field_1724;
         if (localPlayer != null) {
            Object self = this;
            if (self == localPlayer || other == localPlayer) {
               ci.cancel();
            }
         }
      }
   }
}
