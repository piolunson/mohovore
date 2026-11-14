package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.SkyboxModule;
import dev.leonetic.util.render.SkyboxRenderer;
import java.awt.Color;
import net.minecraft.class_12131;
import net.minecraft.class_4587;
import net.minecraft.class_9975;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_9975.class)
public class MixinSkyRenderer {
   @Inject(method = "method_62302", at = @At("HEAD"), cancellable = true)
   private void homovore$replaceSky(int skyColor, CallbackInfo ci) {
      SkyboxModule module = SkyboxModule.getInstance();
      if (module != null && module.isEnabled()) {
         Color c = module.auroraColor.getValue();
         SkyboxRenderer.render(module.mode.getValue(), c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F);
         ci.cancel();
      }
   }

   @Inject(method = "method_62307", at = @At("HEAD"), cancellable = true)
   private void homovore$cancelCelestial(
      class_4587 poseStack,
      float sunAngle,
      float moonAngle,
      float starAngle,
      class_12131 moonPhase,
      float rainBrightness,
      float starBrightness,
      CallbackInfo ci
   ) {
      if (SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_62306", at = @At("HEAD"), cancellable = true)
   private void homovore$cancelSunrise(class_4587 poseStack, float sunAngle, int color, CallbackInfo ci) {
      if (SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_62305", at = @At("HEAD"), cancellable = true)
   private void homovore$cancelDarkDisc(CallbackInfo ci) {
      if (SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }
}
