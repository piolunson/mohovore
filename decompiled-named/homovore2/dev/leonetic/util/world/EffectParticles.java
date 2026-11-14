package dev.leonetic.util.world;

import dev.leonetic.mixin.LivingEntityEffectParticlesAccessor;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.entry.RegistryEntry;

public final class EffectParticles {
   private static final int RESISTANCE_COLOR = color(StatusEffects.RESISTANCE);
   private static final int SLOWNESS_COLOR = color(StatusEffects.SLOWNESS);
   private static final int STRENGTH_COLOR = color(StatusEffects.STRENGTH);
   private static final int COLOR_TOLERANCE = 2;

   private EffectParticles() {
      throw new AssertionError();
   }

   public static boolean hasResistance(LivingEntity entity) {
      return hasColor(entity, RESISTANCE_COLOR);
   }

   public static boolean hasSlowness(LivingEntity entity) {
      return hasColor(entity, SLOWNESS_COLOR);
   }

   public static boolean hasStrength(LivingEntity entity) {
      return hasColor(entity, STRENGTH_COLOR);
   }

   public static boolean isTurtleMaster(LivingEntity entity) {
      List<ParticleEffect> particles = particles(entity);
      if (particles.size() < 2) {
         return false;
      }

      boolean resistance = false;
      boolean slowness = false;

      for (int i = 0; i < particles.size(); i++) {
         if (particles.get(i) instanceof TintedParticleEffect option) {
            int rgb = rgb(option);
            resistance |= matches(rgb, RESISTANCE_COLOR);
            slowness |= matches(rgb, SLOWNESS_COLOR);
            if (resistance && slowness) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean hasColor(LivingEntity entity, int rgb) {
      List<ParticleEffect> particles = particles(entity);

      for (int i = 0; i < particles.size(); i++) {
         if (particles.get(i) instanceof TintedParticleEffect option && matches(rgb(option), rgb)) {
            return true;
         }
      }

      return false;
   }

   private static List<ParticleEffect> particles(LivingEntity entity) {
      return (List<ParticleEffect>)entity.getDataTracker().get(LivingEntityEffectParticlesAccessor.homovore$getDataEffectParticles());
   }

   private static boolean matches(int first, int second) {
      return Math.abs((first >> 16 & 0xFF) - (second >> 16 & 0xFF)) <= 2
         && Math.abs((first >> 8 & 0xFF) - (second >> 8 & 0xFF)) <= 2
         && Math.abs((first & 0xFF) - (second & 0xFF)) <= 2;
   }

   private static int rgb(TintedParticleEffect option) {
      return (Math.round(option.getRed() * 255.0F) & 0xFF) << 16
         | (Math.round(option.getGreen() * 255.0F) & 0xFF) << 8
         | Math.round(option.getBlue() * 255.0F) & 0xFF;
   }

   private static int color(RegistryEntry<StatusEffect> effect) {
      return ((StatusEffect)effect.value()).getColor() & 16777215;
   }
}
