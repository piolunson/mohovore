package dev.leonetic.util;

import dev.leonetic.mixin.entity.ColorParticleOptionAccessor;
import dev.leonetic.mixin.entity.LivingEntityEffectParticlesAccessor;
import java.util.List;
import net.minecraft.class_1291;
import net.minecraft.class_1294;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_2394;
import net.minecraft.class_9381;

public final class DamageSyncTracker {
   private static final int RESISTANCE_COLOR = ((class_1291)class_1294.field_5907.comp_349()).method_5556() & 16777215;
   private static final int SLOWNESS_COLOR = ((class_1291)class_1294.field_5909.comp_349()).method_5556() & 16777215;
   private static final int STRENGTH_COLOR = ((class_1291)class_1294.field_5910.comp_349()).method_5556() & 16777215;
   private static final int FULL_HIT_THRESHOLD = 10;
   private static final class_1304[] ARMOR_SLOTS = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};

   private DamageSyncTracker() {
   }

   private static boolean hasEffectColor(class_1309 entity, int rgb) {
      List<class_2394> particles = (List<class_2394>)entity.method_5841().method_12789(LivingEntityEffectParticlesAccessor.homovore$getDataEffectParticles());
      if (particles.isEmpty()) {
         return false;
      }

      int i = 0;

      for (int n = particles.size(); i < n; i++) {
         if (particles.get(i) instanceof class_9381 color && (((ColorParticleOptionAccessor)color).homovore$getColor() & 16777215) == rgb) {
            return true;
         }
      }

      return false;
   }

   public static boolean hasResistance(class_1309 entity) {
      return hasEffectColor(entity, RESISTANCE_COLOR);
   }

   public static boolean hasSlowness(class_1309 entity) {
      return hasEffectColor(entity, SLOWNESS_COLOR);
   }

   public static boolean hasStrength(class_1309 entity) {
      return hasEffectColor(entity, STRENGTH_COLOR);
   }

   public static boolean isTurtleMaster(class_1309 entity) {
      return hasResistance(entity) && hasSlowness(entity);
   }

   public static boolean hasArmorGap(class_1309 entity) {
      for (class_1304 slot : ARMOR_SLOTS) {
         if (entity.method_6118(slot).method_7960()) {
            return true;
         }
      }

      return false;
   }

   public static boolean inImmunityWindow(class_1309 entity) {
      return entity.field_6008 > 10;
   }

   public static boolean shouldHold(class_1309 entity) {
      return isTurtleMaster(entity) && !hasArmorGap(entity) && inImmunityWindow(entity);
   }
}
