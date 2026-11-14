package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_10042;
import net.minecraft.class_11659;
import net.minecraft.class_1299;
import net.minecraft.class_3882;
import net.minecraft.class_4587;
import net.minecraft.class_583;
import net.minecraft.class_976;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_976.class)
public class MixinCustomHeadLayer<S extends class_10042, M extends class_583<S> & class_3882> {
   @Inject(method = "method_17159(Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;ILnet/minecraft/class_10042;FF)V", at = @At("HEAD"), cancellable = true)
   private void homovore$noPlayerHead(class_4587 poseStack, class_11659 collector, int packedLight, S renderState, float f, float g, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noArmor.getValue()) && renderState.field_58171 == class_1299.field_6097) {
         ci.cancel();
      }
   }
}
