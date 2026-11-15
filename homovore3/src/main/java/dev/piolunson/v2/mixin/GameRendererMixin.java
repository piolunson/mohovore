package dev.piolunson.v2.mixin;

import dev.piolunson.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
   @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
   private void homovore$totemAnimation(ItemStack stack, CallbackInfo ci) {
      if (stack.isOf(Items.TOTEM_OF_UNDYING) && RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.TOTEM_ANIMATION)) {
         ci.cancel();
      }
   }

   @Redirect(method = "renderWorld", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
   private float homovore$nauseaWarp(float a, float b) {
      return RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.NAUSEA) ? 0.0F : Math.max(a, b);
   }
}
