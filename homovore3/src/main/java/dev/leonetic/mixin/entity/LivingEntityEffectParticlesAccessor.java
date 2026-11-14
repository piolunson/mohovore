package dev.leonetic.mixin.entity;

import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityEffectParticlesAccessor {
   @Accessor("POTION_SWIRLS")
   static TrackedData<List<ParticleEffect>> homovore$getDataEffectParticles() {
      throw new AssertionError();
   }
}
