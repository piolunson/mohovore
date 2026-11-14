package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.module.impl.FullbrightModule;
import net.minecraft.class_2874;
import net.minecraft.class_765;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_765.class)
public class LightmapRenderStateExtractorMixin {
   @Inject(method = "method_23284(Lnet/minecraft/class_2874;I)F", at = @At("HEAD"), cancellable = true)
   private static void homovore$dimensionBrightness(class_2874 dimension, int lightLevel, CallbackInfoReturnable<Float> cir) {
      if (homovore$isFullbright()) {
         cir.setReturnValue(1.0F);
      }
   }

   @Inject(method = "method_62226(FI)F", at = @At("HEAD"), cancellable = true)
   private static void homovore$ambientBrightness(float ambientLight, int lightLevel, CallbackInfoReturnable<Float> cir) {
      if (homovore$isFullbright()) {
         cir.setReturnValue(1.0F);
      }
   }

   private static boolean homovore$isFullbright() {
      ModuleManager modules = Homovore2Client.modules();
      if (modules == null) {
         return false;
      }

      FullbrightModule module = modules.get(FullbrightModule.class);
      return module != null && module.isEnabled();
   }
}
