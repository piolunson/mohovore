package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherRendering.class)
public class WeatherEffectRendererMixin {
   @Inject(method = "buildPrecipitationPieces", at = @At("TAIL"))
   private void homovore$weather(World level, int ticks, float partialTick, Vec3d cameraPos, WeatherRenderState state, CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.WEATHER)) {
         state.intensity = 0.0F;
      }
   }
}
