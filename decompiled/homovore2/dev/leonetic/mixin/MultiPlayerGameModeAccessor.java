package dev.leonetic.mixin;

import net.minecraft.class_636;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_636.class)
public interface MultiPlayerGameModeAccessor {
   @Accessor("field_3721")
   void homovore$setCarriedIndex(int var1);
}
