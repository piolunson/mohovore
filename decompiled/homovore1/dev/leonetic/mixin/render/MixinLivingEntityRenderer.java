package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import dev.leonetic.manager.RotationManager;
import net.minecraft.class_10042;
import net.minecraft.class_1309;
import net.minecraft.class_310;
import net.minecraft.class_922;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_922.class)
public class MixinLivingEntityRenderer {
   @Inject(method = "method_62355(Lnet/minecraft/class_1309;Lnet/minecraft/class_10042;F)V", at = @At("TAIL"))
   private void homovore$renderMotionRotation(class_1309 entity, class_10042 state, float partialTicks, CallbackInfo ci) {
      if (entity == class_310.method_1551().field_1724) {
         RotationManager rm = Homovore.rotationManager;
         if (rm != null && rm.isRotating()) {
            state.field_53446 = rm.getRenderYaw(partialTicks);
            state.field_53447 = 0.0F;
            state.field_53448 = rm.getRenderPitch(partialTicks);
         }
      }
   }
}
