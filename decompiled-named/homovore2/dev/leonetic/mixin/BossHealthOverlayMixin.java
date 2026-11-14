package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public class BossHealthOverlayMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void homovore$bossBar(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BOSS_BAR)) {
         ci.cancel();
      }
   }
}
