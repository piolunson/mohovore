package dev.leonetic.mixin.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
   @Accessor("life")
   int homovore$getLife();

   @Accessor("lifeTime")
   int homovore$getLifetime();

   @Accessor("shooter")
   LivingEntity homovore$getAttachedToEntity();

   @Accessor("ITEM")
   static TrackedData<ItemStack> homovore$getFireworksItemData() {
      throw new AssertionError();
   }
}
