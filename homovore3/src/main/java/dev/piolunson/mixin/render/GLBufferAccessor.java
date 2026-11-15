package dev.piolunson.mixin.render;

import net.minecraft.client.gl.GlGpuBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlGpuBuffer.class)
public interface GLBufferAccessor {
   @Accessor("id")
   int homovore$getHandle();
}
