package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_1058;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_4603;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_4603.class)
public class MixinScreenEffectRenderer {
   @Inject(method = "method_23070", at = @At("HEAD"), cancellable = true)
   private static void homovore$noFire(class_4587 matrices, class_4597 vertexConsumers, class_1058 sprite, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noFire.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_23069", at = @At("HEAD"), cancellable = true)
   private static void homovore$noWater(class_310 client, class_4587 matrices, class_4597 vertexConsumers, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noLiquid.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_23068", at = @At("HEAD"), cancellable = true)
   private static void homovore$noWall(class_1058 sprite, class_4587 matrices, class_4597 vertexConsumers, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noWall.getValue())) {
         ci.cancel();
      }
   }
}
