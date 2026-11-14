package dev.leonetic.util;

import dev.leonetic.util.traits.Util;
import java.util.function.BiFunction;
import net.minecraft.class_1280;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_1893;
import net.minecraft.class_1922;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_5134;
import net.minecraft.class_9285;
import net.minecraft.class_9334;

public final class AnchorDamageUtil implements Util {
   public static final double DIAMETER = 10.0;

   private AnchorDamageUtil() {
      throw new AssertionError();
   }

   public static float maxDamage(AnchorDamageUtil.Target target, class_243 explosionPos) {
      double dist = target.pos().method_1022(explosionPos);
      return dist > 10.0 ? 0.0F : targetDamage(target, dist, 1.0);
   }

   public static float damage(AnchorDamageUtil.Target target, class_243 explosionPos, class_2338 anchorPos) {
      double dist = target.pos().method_1022(explosionPos);
      return dist > 10.0 ? 0.0F : targetDamage(target, dist, exposure(explosionPos, target.box(), anchorPos));
   }

   public static float selfDamage(class_243 explosionPos, class_2338 anchorPos) {
      double dist = mc.field_1724.method_73189().method_1022(explosionPos);
      if (dist > 10.0) {
         return 0.0F;
      }

      double exposure = exposure(explosionPos, mc.field_1724.method_5829(), anchorPos);
      if (exposure <= 0.0) {
         return 0.0F;
      }

      double impact = (1.0 - dist / 10.0) * exposure;
      if (impact <= 0.0) {
         return 0.0F;
      }

      float dmg = baseDamage(impact);
      float armor = (float)mc.field_1724.method_45325(class_5134.field_23724);
      float toughness = (float)mc.field_1724.method_45325(class_5134.field_23725);
      dmg = armorAbsorb(dmg, armor, toughness);
      class_1293 resistance = mc.field_1724.method_6112(class_1294.field_5907);
      if (resistance != null) {
         dmg *= 1.0F - 0.2F * (resistance.method_5578() + 1);
      }

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

      dmg = class_1280.method_5497(dmg, epf);
      return Math.max(dmg, 0.0F);
   }

   private static float targetDamage(AnchorDamageUtil.Target target, double dist, double exposure) {
      if (exposure <= 0.0) {
         return 0.0F;
      }

      double impact = (1.0 - dist / 10.0) * exposure;
      if (impact <= 0.0) {
         return 0.0F;
      }

      float dmg = baseDamage(impact);
      dmg = armorAbsorb(dmg, target.armor(), target.toughness());
      dmg *= target.resistMult();
      dmg = class_1280.method_5497(dmg, target.protPoints());
      return Math.max(dmg, 0.0F);
   }

   private static float baseDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 10.0 + 1.0);
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

   private static double exposure(class_243 source, class_238 box, class_2338 anchorPos) {
      double dx = box.method_17939();
      double dy = box.method_17940();
      double dz = box.method_17941();
      int steps = 2;
      int total = 0;
      int unblocked = 0;
      BiFunction<class_243[], class_2338, class_3965> tester = (ctxx, pos) -> {
         if (anchorPos != null && pos.equals(anchorPos)) {
            return null;
         }

         class_2680 state = mc.field_1687.method_8320(pos);
         if (state.method_26215()) {
            return null;
         }

         class_265 shape = state.method_26220(mc.field_1687, pos);
         return shape.method_1110() ? null : shape.method_1092(ctxx[0], ctxx[1], pos);
      };

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               class_243 point = new class_243(box.field_1323 + dx * xi / steps, box.field_1322 + dy * yi / steps, box.field_1321 + dz * zi / steps);
               class_243[] ctx = new class_243[]{point, source};
               if (class_1922.method_17744(point, source, ctx, tester, c -> null) == null) {
                  unblocked++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unblocked / total;
   }

   public record Target(class_1309 entity, class_243 pos, class_238 box, float armor, float toughness, float resistMult, int protPoints) {
      public static AnchorDamageUtil.Target of(class_1309 living) {
         float armor = 0.0F;
         float toughness = 0.0F;
         int protPoints = 0;

         for (class_1304 slot : new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166}) {
            class_1799 stack = living.method_6118(slot);
            if (!stack.method_7960()) {
               class_9285 modifiers = (class_9285)stack.method_58695(class_9334.field_49636, class_9285.field_49326);
               armor = (float)modifiers.method_57481(class_5134.field_23724, armor, slot);
               toughness = (float)modifiers.method_57481(class_5134.field_23725, toughness, slot);
               protPoints += slot != class_1304.field_6172 && slot != class_1304.field_6166 ? 4 : 8;
            }
         }

         if (protPoints > 20) {
            protPoints = 20;
         }

         class_1293 resistance = living.method_6112(class_1294.field_5907);
         float resistMult = resistance != null ? Math.max(0.0F, 1.0F - 0.2F * (resistance.method_5578() + 1)) : 1.0F;
         return new AnchorDamageUtil.Target(living, living.method_73189(), living.method_5829(), armor, toughness, resistMult, protPoints);
      }
   }
}
