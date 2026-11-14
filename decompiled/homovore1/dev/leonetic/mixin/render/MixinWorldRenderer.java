package dev.leonetic.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.util.render.MatrixCapture;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_3695;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_761;
import net.minecraft.class_7833;
import net.minecraft.class_9779;
import net.minecraft.class_9922;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_761.class)
public class MixinWorldRenderer {
   @Inject(method = "method_22710", at = @At("RETURN"))
   private void render(
      class_9922 allocator,
      class_9779 tickCounter,
      boolean renderBlockOutline,
      class_4184 camera,
      Matrix4f positionMatrix,
      Matrix4f matrix4f,
      Matrix4f projectionMatrix,
      GpuBufferSlice fogBuffer,
      Vector4f fogColor,
      boolean renderSky,
      CallbackInfo ci,
      @Local class_3695 profiler
   ) {
      MatrixCapture.projection = new Matrix4f(projectionMatrix);
      MatrixCapture.view = new Matrix4f(positionMatrix);
      class_4587 stack = new class_4587();
      stack.method_22903();
      stack.method_22907(class_7833.field_40714.rotationDegrees(Util.mc.field_1773.method_19418().method_19329()));
      stack.method_22907(class_7833.field_40716.rotationDegrees(Util.mc.field_1773.method_19418().method_19330() + 180.0F));
      profiler.method_15396("homovore-render-3d");
      Render3DEvent event = new Render3DEvent(stack, tickCounter.method_60637(true));
      Util.EVENT_BUS.post(event);
      stack.method_22909();
      profiler.method_15407();
   }
}
