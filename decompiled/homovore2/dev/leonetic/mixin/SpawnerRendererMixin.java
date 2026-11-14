package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_839;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_839.class)
public class SpawnerRendererMixin {
   @Inject(method = "method_55253", at = @At("HEAD"), cancellable = true)
   private static void homovore$spawnerEntities(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.SPAWNER_ENTITIES)) {
         ci.cancel();
      }
   }
}
