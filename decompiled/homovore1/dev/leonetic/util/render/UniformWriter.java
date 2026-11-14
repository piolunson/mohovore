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
import net.minecraft.class_10859;
import net.minecraft.class_11287;
import net.minecraft.class_279;
import net.minecraft.class_283;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;

public class UniformWriter {
   private final Map<String, Integer> cache = new HashMap<>();

   public void setUniforms(class_279 chain, Map<String, List<class_11287>> configs) {
      if (!configs.isEmpty()) {
         for (class_283 pass : ((PostChainAccessor)chain).homovore$getPasses()) {
            Map<String, GpuBuffer> uniformBuffers = ((PostPassAccessor)pass).homovore$getUniformBuffers();

            for (Entry<String, List<class_11287>> entry : configs.entrySet()) {
               GpuBuffer dest = uniformBuffers.get(entry.getKey());
               if (dest instanceof class_10859) {
                  int handle = ((GLBufferAccessor)dest).homovore$getHandle();
                  int size = this.getSize(entry.getValue());
                  int stagingId = this.createStagingBuffer(entry.getKey(), size);
                  MemoryStack stack = MemoryStack.stackPush();

                  try {
                     ByteBuffer buf = stack.malloc(size);
                     Std140Builder builder = Std140Builder.intoBuffer(buf);

                     for (class_11287 value : entry.getValue()) {
                        value.method_71131(builder);
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

   private int getSize(List<class_11287> values) {
      Std140SizeCalculator calc = new Std140SizeCalculator();

      for (class_11287 v : values) {
         v.method_71132(calc);
      }

      return Math.max(calc.get(), 16);
   }
}
