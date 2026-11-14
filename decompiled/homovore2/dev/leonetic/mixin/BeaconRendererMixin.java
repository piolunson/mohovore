package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_822;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_822.class)
public class BeaconRendererMixin {
   @Inject(
      method = "method_3545(Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_2960;FFIIIFF)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private static void homovore$beaconBeams(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BEACON_BEAMS)) {
         ci.cancel();
      }
   }
}
