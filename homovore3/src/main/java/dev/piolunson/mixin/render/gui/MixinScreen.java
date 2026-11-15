package dev.piolunson.mixin.render.gui;

import dev.piolunson.features.modules.render.NoRenderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {
   @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
   private void homovore$noBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (MinecraftClient.getInstance().world != null && NoRenderModule.isActive(m -> m.noBackground.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
   private void homovore$noTransparentBackground(DrawContext context, CallbackInfo ci) {
      if (MinecraftClient.getInstance().world != null && NoRenderModule.isActive(m -> m.noBackground.getValue())) {
         ci.cancel();
      }
   }
}
