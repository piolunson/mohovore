package dev.piolunson.mixin.render;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.render.ShadersModule;
import dev.piolunson.util.render.WorldChamsChain;
import java.util.Set;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class MixinShaderLevelRenderer {
   @Shadow
   private Framebuffer entityOutlineFramebuffer;

   @Inject(method = "canDrawEntityOutlines", at = @At("HEAD"), cancellable = true)
   private void homovore$forceOutlines(CallbackInfoReturnable<Boolean> cir) {
      ShadersModule shaders = Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.wantsOutlines()) {
         cir.setReturnValue(true);
      }
   }

   @Redirect(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderLoader;loadPostEffect(Lnet/minecraft/util/Identifier;Ljava/util/Set;)Lnet/minecraft/client/gl/PostEffectProcessor;")
   )
   private PostEffectProcessor homovore$swapEntityOutlineChain(ShaderLoader sm, Identifier id, Set<Identifier> externalTargets) {
      ShadersModule shaders = Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.mode.getValue() == ShadersModule.Mode.Default) {
         PostEffectProcessor optimised = WorldChamsChain.get(
            externalTargets, shaders.getGlowRadius(), shaders.getGlowIntensity(), shaders.getFillTint(), shaders.getFillAlpha()
         );
         if (optimised != null) {
            return optimised;
         }
      }

      return sm.loadPostEffect(id, externalTargets);
   }
}
