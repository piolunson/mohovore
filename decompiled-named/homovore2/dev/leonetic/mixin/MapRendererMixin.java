package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import java.util.List;
import net.minecraft.client.render.MapRenderState;
import net.minecraft.client.render.MapRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public class MapRendererMixin {
   @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
   private void homovore$mapContents(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.MAP_CONTENTS)) {
         ci.cancel();
      }
   }

   @Redirect(method = "draw", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/MapRenderState;decorations:Ljava/util/List;"))
   private List<?> homovore$mapMarkers(MapRenderState state) {
      return RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.MAP_MARKERS) ? List.of() : state.decorations;
   }
}
