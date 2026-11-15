package dev.piolunson.mixin.entity;

import net.minecraft.particle.TintedParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TintedParticleEffect.class)
public interface ColorParticleOptionAccessor {
   @Accessor("color")
   int homovore$getColor();
}
