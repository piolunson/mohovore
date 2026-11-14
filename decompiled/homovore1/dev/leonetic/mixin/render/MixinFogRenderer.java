package dev.leonetic.mixin.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_758;
import net.minecraft.class_758.class_4596;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_758.class)
public class MixinFogRenderer {
   @Shadow
   @Final
   private GpuBuffer field_60097;
   @Shadow
   @Final
   private static int field_60096;

   @Inject(method = "method_71109", at = @At("HEAD"), cancellable = true)
   private void homovore$noFog(class_4596 mode, CallbackInfoReturnable<GpuBufferSlice> cir) {
      if (NoRenderModule.isActive(m -> m.noFog.getValue())) {
         cir.setReturnValue(this.field_60097.slice(0L, field_60096));
      }
   }
}
