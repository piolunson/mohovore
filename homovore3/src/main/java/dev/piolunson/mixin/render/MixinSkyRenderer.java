package dev.piolunson.mixin.render;

import dev.piolunson.features.modules.render.SkyboxModule;
import dev.piolunson.util.render.SkyboxRenderer;
import java.awt.Color;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public class MixinSkyRenderer {
   @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
   private void homovore$replaceSky(int skyColor, CallbackInfo ci) {
      SkyboxModule module = SkyboxModule.getInstance();
      if (module != null && module.isEnabled()) {
         Color c = module.auroraColor.getValue();
         SkyboxRenderer.render(module.mode.getValue(), c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F);
         ci.cancel();
      }
   }

   @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
   private void homovore$cancelCelestial(
      MatrixStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
   ) {
      if (SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
   private void homovore$cancelSunrise(MatrixStack poseStack, float sunAngle, int color, CallbackInfo ci) {
      if (SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
   private void homovore$cancelDarkDisc(CallbackInfo ci) {
      if (SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }
}
