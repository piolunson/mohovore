package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_828;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_828.class)
public class EnchantTableRendererMixin {
   @Inject(
      method = "method_3571(Lnet/minecraft/class_11964;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_12075;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$enchantTableBook(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.ENCHANTMENT_TABLE_BOOK)) {
         ci.cancel();
      }
   }
}
