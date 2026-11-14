package dev.leonetic.mixin.entity;

import net.minecraft.class_9381;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_9381.class)
public interface ColorParticleOptionAccessor {
   @Accessor("field_49910")
   int homovore$getColor();
}
