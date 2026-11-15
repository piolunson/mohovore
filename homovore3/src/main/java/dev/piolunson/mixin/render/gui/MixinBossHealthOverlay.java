package dev.piolunson.mixin.render.gui;

import dev.piolunson.features.modules.render.NoRenderModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public class MixinBossHealthOverlay {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void homovore$noBossBar(DrawContext context, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBossBar.getValue())) {
         ci.cancel();
      }
   }
}
