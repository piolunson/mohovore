package dev.leonetic.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.leonetic.Homovore;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_1309;
import net.minecraft.class_2246;
import net.minecraft.class_243;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_1309.class)
public class MixinLivingEntityTravel {
   @Unique
   private float homovore$origYaw;
   @Unique
   private float homovore$origPitch;
   @Unique
   private boolean homovore$applied;

   @Inject(method = "method_6091", at = @At("HEAD"))
   private void homovore$travelHead(class_243 movementInput, CallbackInfo ci) {
      this.homovore$applied = false;
      class_1309 self = (class_1309)this;
      if (self == Util.mc.field_1724 && this.homovore$spoofing()) {
         this.homovore$origYaw = self.method_36454();
         this.homovore$origPitch = self.method_36455();
         self.method_36456(Homovore.rotationManager.getRotationYaw());
         self.method_36457(Homovore.rotationManager.getRotationPitch());
         this.homovore$applied = true;
      }
   }

   @Inject(method = "method_6091", at = @At("RETURN"))
   private void homovore$travelReturn(class_243 movementInput, CallbackInfo ci) {
      if (this.homovore$applied) {
         this.homovore$applied = false;
         class_1309 self = (class_1309)this;
         self.method_36456(this.homovore$origYaw);
         self.method_36457(this.homovore$origPitch);
      }
   }

   @ModifyExpressionValue(method = "method_6043", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1309;method_36454()F"))
   private float homovore$jumpYaw(float original) {
      return this == Util.mc.field_1724 && this.homovore$spoofing() ? Homovore.rotationManager.getRotationYaw() : original;
   }

   @Unique
   private boolean homovore$spoofing() {
      RotationManager rm = Homovore.rotationManager;
      return rm != null && rm.isMoveFixEnabled() && rm.isRotating();
   }

   @ModifyExpressionValue(method = "method_26318", at = @At(value = "FIELD", target = "Lnet/minecraft/class_1309;field_5976:Z"))
   private boolean homovore$noScaffoldClimb(boolean original) {
      return this == Util.mc.field_1724 && original ? !((class_1309)this).method_55667().method_27852(class_2246.field_16492) : original;
   }
}
