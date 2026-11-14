package dev.leonetic.v2.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
   @Accessor("life")
   int homovore$getLife();

   @Accessor("shooter")
   LivingEntity homovore$getAttachedToEntity();
}
