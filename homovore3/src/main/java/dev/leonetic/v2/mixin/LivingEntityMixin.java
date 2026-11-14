package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import dev.leonetic.v2.feature.module.impl.BounceModule;
import dev.leonetic.v2.feature.module.impl.NoClimbModule;
import dev.leonetic.v2.feature.module.impl.NoJumpDelayModule;
import dev.leonetic.v2.feature.module.impl.ViewModelModule;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
   @Shadow
   private int jumpingCooldown;

   @Shadow
   private int getHandSwingDuration() {
      throw new AssertionError();
   }

   @Inject(method = "spawnItemParticles", at = @At("HEAD"), cancellable = true)
   private void homovore$eatingParticles(ItemStack stack, int count, CallbackInfo ci) {
      if (stack.getComponents().contains(DataComponentTypes.FOOD) && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.EATING_PARTICLES)) {
         ci.cancel();
      }
   }

   @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
   private void homovore$swingSpeed(CallbackInfoReturnable<Integer> cir) {
      ViewModelModule module = this.homovore$viewModel();
      if (module != null) {
         cir.setReturnValue(Math.max(1, (int)Math.round(((Integer)cir.getReturnValue()).intValue() / module.swingSpeed.getValue())));
      }
   }

   @Redirect(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getHandSwingDuration()I"))
   private int homovore$swingDelay(LivingEntity instance) {
      ViewModelModule module = this.homovore$viewModel();
      return module == null ? this.getHandSwingDuration() : module.swingDelay.getValue() * 2;
   }

   @Redirect(method = "applyMovementInput", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;horizontalCollision:Z", opcode = 180))
   private boolean homovore$noScaffoldClimb(LivingEntity instance) {
      if (!instance.horizontalCollision) {
         return false;
      } else {
         return instance instanceof ClientPlayerEntity && NoClimbModule.isActive() ? !instance.getBlockStateAtPos().isOf(Blocks.SCAFFOLDING) : true;
      }
   }

   @Redirect(method = "tickMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;jumpingCooldown:I", opcode = 180))
   private int homovore$noJumpDelay(LivingEntity instance) {
      return instance instanceof ClientPlayerEntity && (NoJumpDelayModule.isActive() || BounceModule.flying() != null) ? 0 : this.jumpingCooldown;
   }

   @Inject(method = "isGliding", at = @At("HEAD"), cancellable = true)
   private void homovore$bounceGlide(CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof ClientPlayerEntity && BounceModule.isSpoofingGlideNow()) {
         cir.setReturnValue(true);
      }
   }

   private ViewModelModule homovore$viewModel() {
      return !((Object)this instanceof ClientPlayerEntity) ? null : ViewModelModule.active();
   }
}
