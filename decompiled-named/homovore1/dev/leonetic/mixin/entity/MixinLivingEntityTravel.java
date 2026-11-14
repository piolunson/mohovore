package dev.leonetic.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.leonetic.Homovore;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.traits.Util;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntityTravel {
   @Unique
   private float homovore$origYaw;
   @Unique
   private float homovore$origPitch;
   @Unique
   private boolean homovore$applied;

   @Inject(method = "travel", at = @At("HEAD"))
   private void homovore$travelHead(Vec3d movementInput, CallbackInfo ci) {
      this.homovore$applied = false;
      LivingEntity self = (LivingEntity)this;
      if (self == Util.mc.player && this.homovore$spoofing()) {
         this.homovore$origYaw = self.getYaw();
         this.homovore$origPitch = self.getPitch();
         self.setYaw(Homovore.rotationManager.getRotationYaw());
         self.setPitch(Homovore.rotationManager.getRotationPitch());
         this.homovore$applied = true;
      }
   }

   @Inject(method = "travel", at = @At("RETURN"))
   private void homovore$travelReturn(Vec3d movementInput, CallbackInfo ci) {
      if (this.homovore$applied) {
         this.homovore$applied = false;
         LivingEntity self = (LivingEntity)this;
         self.setYaw(this.homovore$origYaw);
         self.setPitch(this.homovore$origPitch);
      }
   }

   @ModifyExpressionValue(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
   private float homovore$jumpYaw(float original) {
      return this == Util.mc.player && this.homovore$spoofing() ? Homovore.rotationManager.getRotationYaw() : original;
   }

   @Unique
   private boolean homovore$spoofing() {
      RotationManager rm = Homovore.rotationManager;
      return rm != null && rm.isMoveFixEnabled() && rm.isRotating();
   }

   @ModifyExpressionValue(method = "applyMovementInput", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;horizontalCollision:Z"))
   private boolean homovore$noScaffoldClimb(boolean original) {
      return this == Util.mc.player && original ? !((LivingEntity)this).getBlockStateAtPos().isOf(Blocks.SCAFFOLDING) : original;
   }
}
