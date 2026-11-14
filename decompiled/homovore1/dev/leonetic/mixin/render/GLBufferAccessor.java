package dev.leonetic.mixin.render;

import net.minecraft.class_10859;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_10859.class)
public interface GLBufferAccessor {
   @Accessor("field_57842")
   int homovore$getHandle();
}
