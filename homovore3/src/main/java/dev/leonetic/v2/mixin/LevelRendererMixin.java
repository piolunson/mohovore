package dev.leonetic.v2.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.Render3DEvent;
import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import dev.leonetic.v2.feature.module.ModuleManager;
import dev.leonetic.v2.feature.module.impl.ChamsModule;
import dev.leonetic.v2.render.ChamsChain;
import java.util.Set;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class LevelRendererMixin {
   @Inject(method = "render", at = @At("HEAD"))
   private void homovore$render3d(
      ObjectAllocator allocator,
      RenderTickCounter deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f frustumMatrix,
      Matrix4f projectionMatrix,
      Matrix4f modelViewMatrix,
      GpuBufferSlice fog,
      Vector4f fogColor,
      boolean renderSky,
      CallbackInfo ci
   ) {
      ModuleManager modules = Homovore2Client.modules();
      if (modules != null) {
         float partialTick = deltaTracker.getTickProgress(true);
         modules.onRender3D(camera, partialTick);
         Homovore2Client.EVENT_BUS.post(new Render3DEvent(camera, partialTick));
      }
   }

   @Inject(method = "fillBlockBreakingProgressRenderState", at = @At("HEAD"), cancellable = true)
   private void homovore$blockBreakOverlay(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BLOCK_BREAK_OVERLAY)) {
         ci.cancel();
      }
   }

   @Redirect(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderLoader;loadPostEffect(Lnet/minecraft/util/Identifier;Ljava/util/Set;)Lnet/minecraft/client/gl/PostEffectProcessor;")
   )
   private PostEffectProcessor homovore$chamsChain(ShaderLoader shaders, Identifier id, Set<Identifier> externalTargets) {
      ChamsModule module = ChamsModule.active();
      if (module != null) {
         PostEffectProcessor chams = ChamsChain.get(externalTargets, module.style());
         if (chams != null) {
            return chams;
         }
      } else {
         ChamsChain.cleanup();
      }

      return shaders.loadPostEffect(id, externalTargets);
   }
}
