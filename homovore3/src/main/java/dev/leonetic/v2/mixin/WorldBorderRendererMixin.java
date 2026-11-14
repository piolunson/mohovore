package dev.leonetic.v2.mixin;

import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.WorldBorderRendering;
import net.minecraft.client.render.state.WorldBorderRenderState;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRendering.class)
public class WorldBorderRendererMixin {
   @Inject(method = "updateRenderState", at = @At("TAIL"))
   private void homovore$worldBorder(
      WorldBorder border, float partialTick, Vec3d cameraPos, double renderDistance, WorldBorderRenderState state, CallbackInfo ci
   ) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.WORLD_BORDER)) {
         state.alpha = 0.0;
      }
   }
}
