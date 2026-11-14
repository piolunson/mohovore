package dev.leonetic.mixin;

import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_746.class)
public interface LocalPlayerAccessor {
   @Accessor("field_3923")
   void homovore$setPositionReminder(int var1);

   @Accessor("field_3941")
   void homovore$setYRotLast(float var1);

   @Accessor("field_3925")
   void homovore$setXRotLast(float var1);
}
