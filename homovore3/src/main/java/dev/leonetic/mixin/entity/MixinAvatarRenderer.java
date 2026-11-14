package dev.leonetic.mixin.entity;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.NametagsModule;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class MixinAvatarRenderer {
   @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
   private void cancelVanillaNameTag(CallbackInfo ci) {
      NametagsModule nametags = Homovore.moduleManager.getModuleByClass(NametagsModule.class);
      if (nametags != null && nametags.isEnabled()) {
         ci.cancel();
      }
   }
}
