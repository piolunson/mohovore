package dev.leonetic.mixin.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
   @Shadow
   @Final
   private GpuBuffer emptyBuffer;
   @Shadow
   @Final
   private static int FOG_UBO_SIZE;

   @Inject(method = "getFogBuffer", at = @At("HEAD"), cancellable = true)
   private void homovore$noFog(FogType mode, CallbackInfoReturnable<GpuBufferSlice> cir) {
      if (NoRenderModule.isActive(m -> m.noFog.getValue())) {
         cir.setReturnValue(this.emptyBuffer.slice(0L, FOG_UBO_SIZE));
      }
   }
}
