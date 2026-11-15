package dev.piolunson.v2.mixin;

import dev.piolunson.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.block.entity.MobSpawnerBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobSpawnerBlockEntityRenderer.class)
public class SpawnerRendererMixin {
   @Inject(method = "renderDisplayEntity", at = @At("HEAD"), cancellable = true)
   private static void homovore$spawnerEntities(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.SPAWNER_ENTITIES)) {
         ci.cancel();
      }
   }
}
