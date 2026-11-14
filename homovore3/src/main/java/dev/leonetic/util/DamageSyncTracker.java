package dev.leonetic.util;

import dev.leonetic.mixin.entity.ColorParticleOptionAccessor;
import dev.leonetic.mixin.entity.LivingEntityEffectParticlesAccessor;
import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.TintedParticleEffect;

public final class DamageSyncTracker {
   private static final int RESISTANCE_COLOR = ((StatusEffect)StatusEffects.RESISTANCE.value()).getColor() & 16777215;
   private static final int SLOWNESS_COLOR = ((StatusEffect)StatusEffects.SLOWNESS.value()).getColor() & 16777215;
   private static final int STRENGTH_COLOR = ((StatusEffect)StatusEffects.STRENGTH.value()).getColor() & 16777215;
   private static final int FULL_HIT_THRESHOLD = 10;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   private DamageSyncTracker() {
   }

   private static boolean hasEffectColor(LivingEntity entity, int rgb) {
      List<ParticleEffect> particles = (List<ParticleEffect>)entity.getDataTracker().get(LivingEntityEffectParticlesAccessor.homovore$getDataEffectParticles());
      if (particles.isEmpty()) {
         return false;
      }

      int i = 0;

      for (int n = particles.size(); i < n; i++) {
         if (particles.get(i) instanceof TintedParticleEffect color && (((ColorParticleOptionAccessor)color).homovore$getColor() & 16777215) == rgb) {
            return true;
         }
      }

      return false;
   }

   public static boolean hasResistance(LivingEntity entity) {
      return hasEffectColor(entity, RESISTANCE_COLOR);
   }

   public static boolean hasSlowness(LivingEntity entity) {
      return hasEffectColor(entity, SLOWNESS_COLOR);
   }

   public static boolean hasStrength(LivingEntity entity) {
      return hasEffectColor(entity, STRENGTH_COLOR);
   }

   public static boolean isTurtleMaster(LivingEntity entity) {
      return hasResistance(entity) && hasSlowness(entity);
   }

   public static boolean hasArmorGap(LivingEntity entity) {
      for (EquipmentSlot slot : ARMOR_SLOTS) {
         if (entity.getEquippedStack(slot).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   public static boolean inImmunityWindow(LivingEntity entity) {
      return entity.timeUntilRegen > 10;
   }

   public static boolean shouldHold(LivingEntity entity) {
      return isTurtleMaster(entity) && !hasArmorGap(entity) && inImmunityWindow(entity);
   }
}
