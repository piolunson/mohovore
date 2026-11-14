package dev.leonetic.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.leonetic.Homovore;
import dev.leonetic.event.Stage;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.entity.player.UpdateWalkingPlayerEvent;
import dev.leonetic.features.modules.movement.NoSlowModule;
import dev.leonetic.features.modules.movement.SprintModule;
import dev.leonetic.features.modules.movement.VelocityModule;
import dev.leonetic.features.modules.render.NoRenderModule;
import dev.leonetic.features.modules.world.ScaffoldModule;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_437;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_746.class)
public class MixinClientPlayerEntity {
   @Inject(method = "method_5773", at = @At("HEAD"))
   private void preTickHook(CallbackInfo ci) {
      Util.EVENT_BUS.post(new PreTickEvent());
   }

   @Inject(method = "method_5773", at = @At("TAIL"))
   private void tickHook(CallbackInfo ci) {
      Util.EVENT_BUS.post(new TickEvent());
   }

   @Inject(method = "method_5773", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_742;method_5773()V", shift = Shift.AFTER))
   private void tickHook2(CallbackInfo ci) {
      Util.EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.PRE));
   }

   @Inject(method = "method_5773", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_746;method_3136()V", shift = Shift.AFTER))
   private void tickHook3(CallbackInfo ci) {
      Util.EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.POST));
   }

   @Inject(method = "method_6007", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_744;method_3129()V", shift = Shift.AFTER))
   private void scaffold$suppressSneakAfterInputTick(CallbackInfo ci) {
      ScaffoldModule scaffold = Homovore.moduleManager.getModuleByClass(ScaffoldModule.class);
      if (scaffold != null && scaffold.isEnabled()) {
         class_746 self = (class_746)this;
         class_10185 kp = self.field_3913.field_54155;
         if (kp.comp_3164()) {
            self.field_3913.field_54155 = new class_10185(kp.comp_3159(), kp.comp_3160(), kp.comp_3161(), kp.comp_3162(), kp.comp_3163(), false, kp.comp_3165());
         }
      }
   }

   @Inject(method = "method_6007", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_744;method_3129()V", shift = Shift.AFTER))
   private void sprint$applyBeforeJump(CallbackInfo ci) {
      SprintModule sprint = Homovore.moduleManager.getModuleByClass(SprintModule.class);
      if (sprint != null && sprint.wantsSprint()) {
         ((class_746)this).method_5728(true);
      }
   }

   @Inject(method = "method_6007", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_744;method_3129()V", shift = Shift.AFTER))
   private void homovore$moveFixAfterInputTick(CallbackInfo ci) {
      RotationManager rotation = Homovore.rotationManager;
      if (rotation != null && rotation.isRotating() && rotation.isMoveFixEnabled()) {
         class_746 self = (class_746)this;
         class_10185 real = self.field_3913.field_54155;
         class_10185 fixed = rotation.computeMoveFixInput(real, self.method_36454());
         if (fixed != real) {
            self.field_3913.field_54155 = fixed;
            float leftImpulse = fixed.comp_3161() == fixed.comp_3162() ? 0.0F : (fixed.comp_3161() ? 1.0F : -1.0F);
            float forwardImpulse = fixed.comp_3159() == fixed.comp_3160() ? 0.0F : (fixed.comp_3159() ? 1.0F : -1.0F);
            self.field_3913.field_55868 = new class_241(leftImpulse, forwardImpulse).method_35581();
         }
      }
   }

   @Inject(method = "method_30673", at = @At("HEAD"), cancellable = true)
   private void cancelBlockPush(double x, double z, CallbackInfo ci) {
      VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
      if (velocity != null && velocity.isEnabled() && velocity.blockPush.getValue() && velocity.phaseConditionMet()) {
         ci.cancel();
      }
   }

   @ModifyExpressionValue(method = "method_67270", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_746;method_75410()F"))
   private float noSlow$eatSpeed(float original) {
      return NoSlowModule.shouldCancelConsumeSlow() ? 1.0F : original;
   }

   @Inject(method = "method_20303", at = @At("HEAD"), cancellable = true)
   private void fastCrawl(CallbackInfoReturnable<Boolean> cir) {
      class_746 self = (class_746)this;
      if (self.method_20448() && NoSlowModule.isActive(m -> m.crawl.getValue())) {
         cir.setReturnValue(false);
      }
   }

   @Redirect(method = "method_60887", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_437;method_73217()Z"))
   private boolean homovore$portalGui(class_437 screen) {
      return NoRenderModule.isActive(m -> m.portalGui.getValue()) ? true : screen.method_73217();
   }
}
