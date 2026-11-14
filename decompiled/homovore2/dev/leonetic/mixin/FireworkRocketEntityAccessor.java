package dev.leonetic.mixin;

import net.minecraft.class_1309;
import net.minecraft.class_1671;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_1671.class)
public interface FireworkRocketEntityAccessor {
   @Accessor("field_7613")
   int homovore$getLife();

   @Accessor("field_7616")
   class_1309 homovore$getAttachedToEntity();
}
