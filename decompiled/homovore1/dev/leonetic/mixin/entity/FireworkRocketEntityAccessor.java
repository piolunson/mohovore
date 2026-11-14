package dev.leonetic.mixin.entity;

import net.minecraft.class_1309;
import net.minecraft.class_1671;
import net.minecraft.class_1799;
import net.minecraft.class_2940;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_1671.class)
public interface FireworkRocketEntityAccessor {
   @Accessor("field_7613")
   int homovore$getLife();

   @Accessor("field_7612")
   int homovore$getLifetime();

   @Accessor("field_7616")
   class_1309 homovore$getAttachedToEntity();

   @Accessor("field_7614")
   static class_2940<class_1799> homovore$getFireworksItemData() {
      throw new AssertionError();
   }
}
