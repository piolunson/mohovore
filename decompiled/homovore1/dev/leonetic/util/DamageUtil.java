package dev.leonetic.util;

import dev.leonetic.util.traits.Util;
import net.minecraft.class_1280;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_1893;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_5134;

public final class DamageUtil implements Util {
   private DamageUtil() {
      throw new AssertionError();
   }

   public static float crystalMaxSelfDamage(class_243 explosionPos) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         double dist = mc.field_1724.method_73189().method_1022(explosionPos);
         if (dist > 12.0) {
            return 0.0F;
         }

         double impact = 1.0 - dist / 12.0;
         if (impact <= 0.0) {
            return 0.0F;
         }

         float dmg = baseExplosionDamage(impact);
         float armor = (float)mc.field_1724.method_45325(class_5134.field_23724);
         float toughness = (float)mc.field_1724.method_45325(class_5134.field_23725);
         dmg = armorAbsorb(dmg, armor, toughness);
         class_1293 resistance = mc.field_1724.method_6112(class_1294.field_5907);
         if (resistance != null) {
            dmg *= 1.0F - 0.2F * (resistance.method_5578() + 1);
         }

         dmg = protectionAbsorb(dmg);
         return Math.max(dmg, 0.0F);
      } else {
         return 0.0F;
      }
   }

   private static float baseExplosionDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (mc.field_1687.method_8407()) {
         case field_5805:
            dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
            break;
         case field_5807:
            dmg *= 1.5F;
      }

      return dmg;
   }

   private static float armorAbsorb(float dmg, float armor, float toughness) {
      float i = 2.0F + toughness / 4.0F;
      float j = class_3532.method_15363(armor - dmg / i, armor * 0.2F, 20.0F);
      return dmg * (1.0F - j / 25.0F);
   }

   private static float protectionAbsorb(float dmg) {
      int epf = 0;

      for (class_1304 slot : new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166}) {
         class_1799 stack = mc.field_1724.method_6118(slot);
         if (!stack.method_7960()) {
            epf += EnchantmentUtil.getLevel(class_1893.field_9111, stack);
            epf += 2 * EnchantmentUtil.getLevel(class_1893.field_9107, stack);
         }
      }

      if (epf > 20) {
         epf = 20;
      }

      return class_1280.method_5497(dmg, epf);
   }
}
