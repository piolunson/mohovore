package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_12077;
import net.minecraft.class_1937;
import net.minecraft.class_243;
import net.minecraft.class_9976;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_9976.class)
public class WeatherEffectRendererMixin {
   @Inject(method = "method_62315", at = @At("TAIL"))
   private void homovore$weather(class_1937 level, int ticks, float partialTick, class_243 cameraPos, class_12077 state, CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.WEATHER)) {
         state.field_63103 = 0.0F;
      }
   }
}
