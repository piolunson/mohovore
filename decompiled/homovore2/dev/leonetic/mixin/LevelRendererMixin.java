package dev.leonetic.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.Render3DEvent;
import dev.leonetic.event.impl.render.RenderFeatureEvent;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.module.impl.ChamsModule;
import dev.leonetic.render.ChamsChain;
import java.util.Set;
import net.minecraft.class_10151;
import net.minecraft.class_279;
import net.minecraft.class_2960;
import net.minecraft.class_4184;
import net.minecraft.class_761;
import net.minecraft.class_9779;
import net.minecraft.class_9922;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_761.class)
public class LevelRendererMixin {
   @Inject(method = "method_22710", at = @At("HEAD"))
   private void homovore$render3d(
      class_9922 allocator,
      class_9779 deltaTracker,
      boolean renderBlockOutline,
      class_4184 camera,
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
         float partialTick = deltaTracker.method_60637(true);
         modules.onRender3D(camera, partialTick);
         Homovore2Client.EVENT_BUS.post(new Render3DEvent(camera, partialTick));
      }
   }

   @Inject(method = "method_74922", at = @At("HEAD"), cancellable = true)
   private void homovore$blockBreakOverlay(CallbackInfo ci) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BLOCK_BREAK_OVERLAY)) {
         ci.cancel();
      }
   }

   @Redirect(
      method = "method_22710",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_10151;method_62941(Lnet/minecraft/class_2960;Ljava/util/Set;)Lnet/minecraft/class_279;")
   )
   private class_279 homovore$chamsChain(class_10151 shaders, class_2960 id, Set<class_2960> externalTargets) {
      ChamsModule module = ChamsModule.active();
      if (module != null) {
         class_279 chams = ChamsChain.get(externalTargets, module.style());
         if (chams != null) {
            return chams;
         }
      } else {
         ChamsChain.cleanup();
      }

      return shaders.method_62941(id, externalTargets);
   }
}
