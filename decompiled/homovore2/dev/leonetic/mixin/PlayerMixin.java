package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.FixesModule;
import net.minecraft.class_1657;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_1657.class)
public abstract class PlayerMixin {
   @Inject(
      method = "method_75122",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1657;method_18799(Lnet/minecraft/class_243;)V"),
      cancellable = true
   )
   private void homovore$keepAttackVelocity(CallbackInfo ci) {
      if (this == class_310.method_1551().field_1724 && FixesModule.cancelsAttackVelocity()) {
         ci.cancel();
      }
   }
}
