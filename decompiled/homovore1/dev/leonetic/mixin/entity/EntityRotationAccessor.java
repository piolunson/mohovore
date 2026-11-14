package dev.leonetic.mixin.entity;

import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_746.class)
public interface EntityRotationAccessor {
   @Accessor("field_3916")
   float homovore$getXBob();

   @Accessor("field_3916")
   void homovore$setXBob(float var1);

   @Accessor("field_3914")
   float homovore$getXBobO();

   @Accessor("field_3914")
   void homovore$setXBobO(float var1);

   @Accessor("field_3932")
   float homovore$getYBob();

   @Accessor("field_3932")
   void homovore$setYBob(float var1);

   @Accessor("field_3931")
   float homovore$getYBobO();

   @Accessor("field_3931")
   void homovore$setYBobO(float var1);

   @Accessor("field_3920")
   boolean homovore$getLastOnGround();
}
