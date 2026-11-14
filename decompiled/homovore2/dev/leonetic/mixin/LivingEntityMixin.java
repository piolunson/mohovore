package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import dev.leonetic.feature.module.impl.BounceModule;
import dev.leonetic.feature.module.impl.NoClimbModule;
import dev.leonetic.feature.module.impl.NoJumpDelayModule;
import dev.leonetic.feature.module.impl.ViewModelModule;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_2246;
import net.minecraft.class_746;
import net.minecraft.class_9334;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1309.class)
public class LivingEntityMixin {
   @Shadow
   private int field_6228;

   @Shadow
   private int method_6028() {
      throw new AssertionError();
   }

   @Inject(method = "method_6037", at = @At("HEAD"), cancellable = true)
   private void homovore$eatingParticles(class_1799 stack, int count, CallbackInfo ci) {
      if (stack.method_57353().method_57832(class_9334.field_50075) && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.EATING_PARTICLES)) {
         ci.cancel();
      }
   }

   @Inject(method = "method_6028", at = @At("RETURN"), cancellable = true)
   private void homovore$swingSpeed(CallbackInfoReturnable<Integer> cir) {
      ViewModelModule module = this.homovore$viewModel();
      if (module != null) {
         cir.setReturnValue(Math.max(1, (int)Math.round(((Integer)cir.getReturnValue()).intValue() / module.swingSpeed.getValue())));
      }
   }

   @Redirect(method = "method_23667(Lnet/minecraft/class_1268;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1309;method_6028()I"))
   private int homovore$swingDelay(class_1309 instance) {
      ViewModelModule module = this.homovore$viewModel();
      return module == null ? this.method_6028() : module.swingDelay.getValue() * 2;
   }

   @Redirect(method = "method_26318", at = @At(value = "FIELD", target = "Lnet/minecraft/class_1309;field_5976:Z", opcode = 180))
   private boolean homovore$noScaffoldClimb(class_1309 instance) {
      if (!instance.field_5976) {
         return false;
      } else {
         return instance instanceof class_746 && NoClimbModule.isActive() ? !instance.method_55667().method_27852(class_2246.field_16492) : true;
      }
   }

   @Redirect(method = "method_6007", at = @At(value = "FIELD", target = "Lnet/minecraft/class_1309;field_6228:I", opcode = 180))
   private int homovore$noJumpDelay(class_1309 instance) {
      return instance instanceof class_746 && (NoJumpDelayModule.isActive() || BounceModule.flying() != null) ? 0 : this.field_6228;
   }

   @Inject(method = "method_6128", at = @At("HEAD"), cancellable = true)
   private void homovore$bounceGlide(CallbackInfoReturnable<Boolean> cir) {
      if (this instanceof class_746 && BounceModule.isSpoofingGlideNow()) {
         cir.setReturnValue(true);
      }
   }

   private ViewModelModule homovore$viewModel() {
      return !(this instanceof class_746) ? null : ViewModelModule.active();
   }
}
