package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.Render2DEvent;
import dev.piolunson.v2.event.impl.render.RenderFeatureEvent;
import dev.piolunson.v2.feature.module.ModuleManager;
import dev.piolunson.v2.render.font.FontEngine;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class HudMixin {
   @Shadow
   protected abstract void renderOverlay(DrawContext var1, Identifier var2, float var3);

   @Inject(method = "render", at = @At("TAIL"))
   private void homovore$render2d(DrawContext graphics, RenderTickCounter deltaTracker, CallbackInfo ci) {
      ModuleManager modules = Homovore2Client.modules();
      if (modules != null) {
         float partialTick = deltaTracker.getTickProgress(true);
         modules.onRender2D(graphics, partialTick);
         Homovore2Client.EVENT_BUS.post(new Render2DEvent(graphics, partialTick));
         FontEngine.flush();
      }
   }

   @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$portalOverlay(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.PORTAL_OVERLAY, ci);
   }

   @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$spyglassOverlay(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.SPYGLASS_OVERLAY, ci);
   }

   @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$nausea(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.NAUSEA, ci);
   }

   @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$vignette(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.VIGNETTE, ci);
   }

   @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$potionIcons(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.POTION_ICONS, ci);
   }

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void homovore$crosshair(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.CROSSHAIR, ci);
   }

   @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
   private void homovore$title(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.TITLE, ci);
   }

   @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
   private void homovore$heldItemName(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.HELD_ITEM_NAME, ci);
   }

   @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
   private void homovore$scoreboard(CallbackInfo ci) {
      homovore$cancelIfSuppressed(RenderFeatureEvent.Feature.SCOREBOARD, ci);
   }

   @Redirect(
      method = "renderMiscOverlays",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 0)
   )
   private void homovore$pumpkinOverlay(InGameHud hud, DrawContext graphics, Identifier texture, float alpha) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.PUMPKIN_OVERLAY)) {
         this.renderOverlay(graphics, texture, alpha);
      }
   }

   @Redirect(
      method = "renderMiscOverlays",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V", ordinal = 1)
   )
   private void homovore$powderedSnowOverlay(InGameHud hud, DrawContext graphics, Identifier texture, float alpha) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.POWDERED_SNOW_OVERLAY)) {
         this.renderOverlay(graphics, texture, alpha);
      }
   }

   private static void homovore$cancelIfSuppressed(RenderFeatureEvent.Feature feature, CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(feature)) {
         ci.cancel();
      }
   }
}
