package dev.piolunson.mixin.entity;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.render.NametagsModule;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class MixinAvatarRenderer {
   @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
   private void cancelVanillaNameTag(CallbackInfo ci) {
      NametagsModule nametags = Homovore.moduleManager.getModuleByClass(NametagsModule.class);
      if (nametags != null && nametags.isEnabled()) {
         ci.cancel();
      }
   }
}
