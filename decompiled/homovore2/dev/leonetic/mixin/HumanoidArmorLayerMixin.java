package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_10034;
import net.minecraft.class_10055;
import net.minecraft.class_11659;
import net.minecraft.class_4587;
import net.minecraft.class_970;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_970.class)
public class HumanoidArmorLayerMixin {
   @Inject(method = "method_17157(Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;ILnet/minecraft/class_10034;FF)V", at = @At("HEAD"), cancellable = true)
   private void homovore$armor(class_4587 poseStack, class_11659 collector, int lightCoords, class_10034 state, float yRot, float xRot, CallbackInfo ci) {
      if (state instanceof class_10055 && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.ARMOR)) {
         ci.cancel();
      }
   }
}
