package dev.piolunson.util;

import dev.piolunson.util.traits.Util;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class DamageUtil implements Util {
   private DamageUtil() {
      throw new AssertionError();
   }

   public static float crystalMaxSelfDamage(Vec3d explosionPos) {
      if (mc.player != null && mc.world != null) {
         double dist = mc.player.getEntityPos().distanceTo(explosionPos);
         if (dist > 12.0) {
            return 0.0F;
         }

         double impact = 1.0 - dist / 12.0;
         if (impact <= 0.0) {
            return 0.0F;
         }

         float dmg = baseExplosionDamage(impact);
         float armor = (float)mc.player.getAttributeValue(EntityAttributes.ARMOR);
         float toughness = (float)mc.player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
         dmg = armorAbsorb(dmg, armor, toughness);
         StatusEffectInstance resistance = mc.player.getStatusEffect(StatusEffects.RESISTANCE);
         if (resistance != null) {
            dmg *= 1.0F - 0.2F * (resistance.getAmplifier() + 1);
         }

         dmg = protectionAbsorb(dmg);
         return Math.max(dmg, 0.0F);
      } else {
         return 0.0F;
      }
   }

   private static float baseExplosionDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (mc.world.getDifficulty()) {
         case EASY:
            dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
            break;
         case HARD:
            dmg *= 1.5F;
      }

      return dmg;
   }

   private static float armorAbsorb(float dmg, float armor, float toughness) {
      float i = 2.0F + toughness / 4.0F;
      float j = MathHelper.clamp(armor - dmg / i, armor * 0.2F, 20.0F);
      return dmg * (1.0F - j / 25.0F);
   }

   private static float protectionAbsorb(float dmg) {
      int epf = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = mc.player.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            epf += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            epf += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
         }
      }

      if (epf > 20) {
         epf = 20;
      }

      return net.minecraft.entity.DamageUtil.getInflictedDamage(dmg, epf);
   }
}
