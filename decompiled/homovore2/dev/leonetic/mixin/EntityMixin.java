package dev.leonetic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.render.RenderFeatureEvent;
import dev.leonetic.feature.module.impl.BounceModule;
import dev.leonetic.feature.module.impl.ChamsModule;
import dev.leonetic.feature.module.impl.VelocityModule;
import net.minecraft.class_1297;
import net.minecraft.class_310;
import net.minecraft.class_4050;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1297.class)
public class EntityMixin {
   @Inject(method = "method_18376", at = @At("HEAD"), cancellable = true)
   private void homovore$bounceStandingPose(CallbackInfoReturnable<class_4050> cir) {
      if (this.homovore$isBouncing()) {
         cir.setReturnValue(class_4050.field_18076);
      }
   }

   @Inject(method = "method_5624", at = @At("HEAD"), cancellable = true)
   private void homovore$bounceSprinting(CallbackInfoReturnable<Boolean> cir) {
      if (this.homovore$isBouncing()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "method_5697(Lnet/minecraft/class_1297;)V", at = @At("HEAD"), cancellable = true)
   private void homovore$bouncePush(class_1297 entity, CallbackInfo ci) {
      if (this.homovore$isBouncing() && entity != this) {
         ci.cancel();
      }
   }

   @WrapOperation(method = "method_5697(Lnet/minecraft/class_1297;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1297;method_5762(DDD)V"))
   private void homovore$velocityEntityPush(class_1297 pushed, double x, double y, double z, Operation<Void> original) {
      if (pushed == class_310.method_1551().field_1724) {
         VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
         if (velocity != null && velocity.shouldCancelEntityPush()) {
            return;
         }
      }

      original.call(new Object[]{pushed, x, y, z});
   }

   private boolean homovore$isBouncing() {
      return this == class_310.method_1551().field_1724 && BounceModule.flying() != null;
   }

   @Inject(method = "method_5756", at = @At("HEAD"), cancellable = true)
   private void homovore$invisibility(CallbackInfoReturnable<Boolean> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.INVISIBILITY)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "method_5851", at = @At("HEAD"), cancellable = true)
   private void homovore$glowing(CallbackInfoReturnable<Boolean> cir) {
      ChamsModule chams = ChamsModule.active();
      if (chams != null && chams.shades((class_1297)this)) {
         cir.setReturnValue(true);
      } else if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.GLOWING)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "method_22861", at = @At("HEAD"), cancellable = true)
   private void homovore$teamColor(CallbackInfoReturnable<Integer> cir) {
      ChamsModule chams = ChamsModule.active();
      if (chams != null && chams.shades((class_1297)this)) {
         cir.setReturnValue(chams.colorOf((class_1297)this));
      }
   }
}
