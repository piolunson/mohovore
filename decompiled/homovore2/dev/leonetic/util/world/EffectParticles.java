package dev.leonetic.util.world;

import dev.leonetic.mixin.LivingEntityEffectParticlesAccessor;
import java.util.List;
import net.minecraft.class_1291;
import net.minecraft.class_1294;
import net.minecraft.class_1309;
import net.minecraft.class_2394;
import net.minecraft.class_6880;
import net.minecraft.class_9381;

public final class EffectParticles {
   private static final int RESISTANCE_COLOR = color(class_1294.field_5907);
   private static final int SLOWNESS_COLOR = color(class_1294.field_5909);
   private static final int STRENGTH_COLOR = color(class_1294.field_5910);
   private static final int COLOR_TOLERANCE = 2;

   private EffectParticles() {
      throw new AssertionError();
   }

   public static boolean hasResistance(class_1309 entity) {
      return hasColor(entity, RESISTANCE_COLOR);
   }

   public static boolean hasSlowness(class_1309 entity) {
      return hasColor(entity, SLOWNESS_COLOR);
   }

   public static boolean hasStrength(class_1309 entity) {
      return hasColor(entity, STRENGTH_COLOR);
   }

   public static boolean isTurtleMaster(class_1309 entity) {
      List<class_2394> particles = particles(entity);
      if (particles.size() < 2) {
         return false;
      }

      boolean resistance = false;
      boolean slowness = false;

      for (int i = 0; i < particles.size(); i++) {
         if (particles.get(i) instanceof class_9381 option) {
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

   private static boolean hasColor(class_1309 entity, int rgb) {
      List<class_2394> particles = particles(entity);

      for (int i = 0; i < particles.size(); i++) {
         if (particles.get(i) instanceof class_9381 option && matches(rgb(option), rgb)) {
            return true;
         }
      }

      return false;
   }

   private static List<class_2394> particles(class_1309 entity) {
      return (List<class_2394>)entity.method_5841().method_12789(LivingEntityEffectParticlesAccessor.homovore$getDataEffectParticles());
   }

   private static boolean matches(int first, int second) {
      return Math.abs((first >> 16 & 0xFF) - (second >> 16 & 0xFF)) <= 2
         && Math.abs((first >> 8 & 0xFF) - (second >> 8 & 0xFF)) <= 2
         && Math.abs((first & 0xFF) - (second & 0xFF)) <= 2;
   }

   private static int rgb(class_9381 option) {
      return (Math.round(option.method_58259() * 255.0F) & 0xFF) << 16
         | (Math.round(option.method_58263() * 255.0F) & 0xFF) << 8
         | Math.round(option.method_58264() * 255.0F) & 0xFF;
   }

   private static int color(class_6880<class_1291> effect) {
      return ((class_1291)effect.comp_349()).method_5556() & 16777215;
   }
}
