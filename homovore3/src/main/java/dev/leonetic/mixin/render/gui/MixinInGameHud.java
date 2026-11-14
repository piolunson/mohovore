package dev.leonetic.mixin.render.gui;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.render.NoRenderModule;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud {
   @Shadow
   @Final
   private static Identifier POWDER_SNOW_OUTLINE;

   @Shadow
   protected abstract void renderOverlay(DrawContext var1, Identifier var2, float var3);

   @Inject(method = "render", at = @At("RETURN"))
   public void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (!MinecraftClient.getInstance().inGameHud.getDebugHud().shouldShowDebugHud()) {
         Render2DEvent event = new Render2DEvent(context, tickCounter.getTickProgress(true));
         Util.EVENT_BUS.post(event);
      }
   }

   @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$noPotions(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noPotIcon.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void homovore$noCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noCrosshair.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
   private void homovore$noItemName(DrawContext context, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noItemName.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$noPortal(DrawContext context, float alpha, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noPortal.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$noVignette(DrawContext context, Entity entity, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noVignette.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
   private void homovore$noNausea(DrawContext context, float distortionStrength, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noNausea.getValue())) {
         ci.cancel();
      }
   }

   @Redirect(
      method = "renderMiscOverlays",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V")
   )
   private void homovore$noCameraOverlay(InGameHud instance, DrawContext context, Identifier texture, float alpha) {
      if (!NoRenderModule.isActive(m -> m.noPowderedSnow.getValue()) || !POWDER_SNOW_OUTLINE.equals(texture)) {
         if (!NoRenderModule.isActive(m -> m.noPumpkin.getValue()) || !texture.getPath().contains("pumpkin")) {
            this.renderOverlay(context, texture, alpha);
         }
      }
   }
}
