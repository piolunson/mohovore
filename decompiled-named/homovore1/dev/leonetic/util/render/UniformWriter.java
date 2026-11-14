package dev.leonetic.util.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import dev.leonetic.mixin.render.GLBufferAccessor;
import dev.leonetic.mixin.render.PostChainAccessor;
import dev.leonetic.mixin.render.PostPassAccessor;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gl.GlGpuBuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;

public class UniformWriter {
   private final Map<String, Integer> cache = new HashMap<>();

   public void setUniforms(PostEffectProcessor chain, Map<String, List<UniformValue>> configs) {
      if (!configs.isEmpty()) {
         for (PostEffectPass pass : ((PostChainAccessor)chain).homovore$getPasses()) {
            Map<String, GpuBuffer> uniformBuffers = ((PostPassAccessor)pass).homovore$getUniformBuffers();

            for (Entry<String, List<UniformValue>> entry : configs.entrySet()) {
               GpuBuffer dest = uniformBuffers.get(entry.getKey());
               if (dest instanceof GlGpuBuffer) {
                  int handle = ((GLBufferAccessor)dest).homovore$getHandle();
                  int size = this.getSize(entry.getValue());
                  int stagingId = this.createStagingBuffer(entry.getKey(), size);
                  MemoryStack stack = MemoryStack.stackPush();

                  try {
                     ByteBuffer buf = stack.malloc(size);
                     Std140Builder builder = Std140Builder.intoBuffer(buf);

                     for (UniformValue value : entry.getValue()) {
                        value.write(builder);
                     }

                     buf.flip();
                     GL31.glBindBuffer(36662, stagingId);
                     GL31.glBufferSubData(36662, 0L, buf);
                     GL31.glBindBuffer(36662, 0);
                  } catch (Throwable var19) {
                     if (stack != null) {
                        try {
                           stack.close();
                        } catch (Throwable var18) {
                           var19.addSuppressed(var18);
                        }
                     }

                     throw var19;
                  }

                  if (stack != null) {
                     stack.close();
                  }

                  GL31.glBindBuffer(36662, stagingId);
                  GL31.glBindBuffer(36663, handle);
                  GL31.glCopyBufferSubData(36662, 36663, 0L, 0L, Math.min(size, (int)dest.size()));
                  GL31.glBindBuffer(36662, 0);
                  GL31.glBindBuffer(36663, 0);
               }
            }
         }
      }
   }

   private int createStagingBuffer(String name, int size) {
      Integer cached = this.cache.get(name);
      if (cached != null) {
         return cached;
      }

      int id = GL31.glGenBuffers();
      GL31.glBindBuffer(36662, id);
      GL31.glBufferData(36662, size, 35048);
      GL31.glBindBuffer(36662, 0);
      this.cache.put(name, id);
      return id;
   }

   private int getSize(List<UniformValue> values) {
      Std140SizeCalculator calc = new Std140SizeCalculator();

      for (UniformValue v : values) {
         v.addSize(calc);
      }

      return Math.max(calc.get(), 16);
   }
}
