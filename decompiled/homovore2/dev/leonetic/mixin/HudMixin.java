package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.Render2DEvent;
import dev.leonetic.event.impl.render.RenderFeatureEvent;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.render.font.FontEngine;
import net.minecraft.class_2960;
import net.minecraft.class_329;
import net.minecraft.class_332;
import net.minecraft.class_9779;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_329.class)
public abstract class HudMixin {
   @Shadow
   protected abstract void method_31977(class_332 var1, class_2960 var2, float var3);

   @Inject(method = "method_1753", at = @At("TAIL"))
   private void homovore$render2d(class_332 graphics, class_9779 deltaTracker, CallbackInfo ci) {
      ModuleManager modules = Homovore2Client.modules();
      if (modules != null) {
         float partialTick = deltaTracker.method_60637(true);
         modules.onRender2D(graphics, partialTick);
         Homovore2Client.EVENT_BUS.post(new Render2DEvent(graphics, partialTick));
         FontEngine.flush();
      }
   }

   @Inject(method = "method_1746", at = @At("HEAD"), cancellable = true)
   private void homovore$portalOverlay(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.PORTAL_OVERLAY, ci);
   }

   @Inject(method = "method_32598", at = @At("HEAD"), cancellable = true)
   private void homovore$spyglassOverlay(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.SPYGLASS_OVERLAY, ci);
   }

   @Inject(method = "method_61980", at = @At("HEAD"), cancellable = true)
   private void homovore$nausea(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.NAUSEA, ci);
   }

   @Inject(method = "method_1735", at = @At("HEAD"), cancellable = true)
   private void homovore$vignette(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.VIGNETTE, ci);
   }

   @Inject(method = "method_1765", at = @At("HEAD"), cancellable = true)
   private void homovore$potionIcons(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.POTION_ICONS, ci);
   }

   @Inject(method = "method_1736", at = @At("HEAD"), cancellable = true)
   private void homovore$crosshair(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.CROSSHAIR, ci);
   }

   @Inject(method = "method_55801", at = @At("HEAD"), cancellable = true)
   private void homovore$title(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.TITLE, ci);
   }

   @Inject(method = "method_1749", at = @At("HEAD"), cancellable = true)
   private void homovore$heldItemName(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.HELD_ITEM_NAME, ci);
   }

   @Inject(method = "method_55803", at = @At("HEAD"), cancellable = true)
   private void homovore$scoreboard(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.SCOREBOARD, ci);
   }

   @Redirect(
      method = "method_55798",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_329;method_31977(Lnet/minecraft/class_332;Lnet/minecraft/class_2960;F)V", ordinal = 0)
   )
   private void homovore$pumpkinOverlay(class_329 hud, class_332 graphics, class_2960 texture, float alpha) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.PUMPKIN_OVERLAY)) {
         this.method_31977(graphics, texture, alpha);
      }
   }

   @Redirect(
      method = "method_55798",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_329;method_31977(Lnet/minecraft/class_332;Lnet/minecraft/class_2960;F)V", ordinal = 1)
   )
   private void homovore$powderedSnowOverlay(class_329 hud, class_332 graphics, class_2960 texture, float alpha) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.POWDERED_SNOW_OVERLAY)) {
         this.method_31977(graphics, texture, alpha);
      }
   }

   private static void homovore$cancelIfSuppressed(RenderFeatureEvent.Feature feature, CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(feature)) {
         ci.cancel();
      }
   }
}
