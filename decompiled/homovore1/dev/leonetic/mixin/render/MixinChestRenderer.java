package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_11659;
import net.minecraft.class_11959;
import net.minecraft.class_12075;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_826;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_826.class)
public class MixinChestRenderer {
   @Inject(
      method = "method_74367(Lnet/minecraft/class_11959;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_12075;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$tileEntityRange(class_11959 state, class_4587 matrices, class_11659 collector, class_12075 camera, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.getInstance();
      if (module != null && module.isEnabled() && module.tileEntity.getValue() >= 2) {
         class_243 cameraPos = class_310.method_1551().method_1561().field_4686.method_71156();
         double distanceSquared = state.field_62673.method_19770(cameraPos);
         double maxDistance = Math.pow(module.tileEntity.getValue().intValue(), 2.0);
         if (distanceSquared > maxDistance) {
            ci.cancel();
         }
      }
   }
}
