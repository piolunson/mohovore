package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_12078;
import net.minecraft.class_243;
import net.minecraft.class_2784;
import net.minecraft.class_9978;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_9978.class)
public class WorldBorderRendererMixin {
   @Inject(method = "method_74927", at = @At("TAIL"))
   private void homovore$worldBorder(class_2784 border, float partialTick, class_243 cameraPos, double renderDistance, class_12078 state, CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.WORLD_BORDER)) {
         state.field_63110 = 0.0;
      }
   }
}
