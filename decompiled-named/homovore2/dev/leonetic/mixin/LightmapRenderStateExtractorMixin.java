package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.module.impl.FullbrightModule;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightmapTextureManager.class)
public class LightmapRenderStateExtractorMixin {
   @Inject(method = "getBrightness(Lnet/minecraft/world/dimension/DimensionType;I)F", at = @At("HEAD"), cancellable = true)
   private static void homovore$dimensionBrightness(DimensionType dimension, int lightLevel, CallbackInfoReturnable<Float> cir) {
      if (homovore$isFullbright()) {
         cir.setReturnValue(1.0F);
      }
   }

   @Inject(method = "getBrightness(FI)F", at = @At("HEAD"), cancellable = true)
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
