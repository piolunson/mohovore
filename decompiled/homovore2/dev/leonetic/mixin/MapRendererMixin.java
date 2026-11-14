package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import java.util.List;
import net.minecraft.class_10090;
import net.minecraft.class_330;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_330.class)
public class MapRendererMixin {
   @Inject(method = "method_1773", at = @At("HEAD"), cancellable = true)
   private void homovore$mapContents(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.MAP_CONTENTS)) {
         ci.cancel();
      }
   }

   @Redirect(method = "method_1773", at = @At(value = "FIELD", target = "Lnet/minecraft/class_10090;field_53639:Ljava/util/List;"))
   private List<?> homovore$mapMarkers(class_10090 state) {
      return RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.MAP_MARKERS) ? List.of() : state.field_53639;
   }
}
