package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_10034;
import net.minecraft.class_11659;
import net.minecraft.class_1299;
import net.minecraft.class_4587;
import net.minecraft.class_572;
import net.minecraft.class_970;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_970.class)
public class MixinHumanoidArmorLayer<S extends class_10034, M extends class_572<S>, A extends class_572<S>> {
   @Inject(method = "method_17157(Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;ILnet/minecraft/class_10034;FF)V", at = @At("HEAD"), cancellable = true)
   private void homovore$noArmor(class_4587 poseStack, class_11659 collector, int packedLight, S renderState, float f, float g, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noArmor.getValue()) && renderState.field_58171 == class_1299.field_6097) {
         ci.cancel();
      }
   }
}
