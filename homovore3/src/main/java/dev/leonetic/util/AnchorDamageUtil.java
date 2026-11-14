package dev.leonetic.util;

import dev.leonetic.util.traits.Util;
import java.util.function.BiFunction;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public final class AnchorDamageUtil implements Util {
   public static final double DIAMETER = 10.0;

   private AnchorDamageUtil() {
      throw new AssertionError();
   }

   public static float maxDamage(AnchorDamageUtil.Target target, Vec3d explosionPos) {
      double dist = target.pos().distanceTo(explosionPos);
      return dist > 10.0 ? 0.0F : targetDamage(target, dist, 1.0);
   }

   public static float damage(AnchorDamageUtil.Target target, Vec3d explosionPos, BlockPos anchorPos) {
      double dist = target.pos().distanceTo(explosionPos);
      return dist > 10.0 ? 0.0F : targetDamage(target, dist, exposure(explosionPos, target.box(), anchorPos));
   }

   public static float selfDamage(Vec3d explosionPos, BlockPos anchorPos) {
      double dist = mc.player.getEntityPos().distanceTo(explosionPos);
      if (dist > 10.0) {
         return 0.0F;
      }

      double exposure = exposure(explosionPos, mc.player.getBoundingBox(), anchorPos);
      if (exposure <= 0.0) {
         return 0.0F;
      }

      double impact = (1.0 - dist / 10.0) * exposure;
      if (impact <= 0.0) {
         return 0.0F;
      }

      float dmg = baseDamage(impact);
      float armor = (float)mc.player.getAttributeValue(EntityAttributes.ARMOR);
      float toughness = (float)mc.player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
      dmg = armorAbsorb(dmg, armor, toughness);
      StatusEffectInstance resistance = mc.player.getStatusEffect(StatusEffects.RESISTANCE);
      if (resistance != null) {
         dmg *= 1.0F - 0.2F * (resistance.getAmplifier() + 1);
      }

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

      dmg = net.minecraft.entity.DamageUtil.getInflictedDamage(dmg, epf);
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
      dmg = net.minecraft.entity.DamageUtil.getInflictedDamage(dmg, target.protPoints());
      return Math.max(dmg, 0.0F);
   }

   private static float baseDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 10.0 + 1.0);
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

   private static double exposure(Vec3d source, Box box, BlockPos anchorPos) {
      double dx = box.getLengthX();
      double dy = box.getLengthY();
      double dz = box.getLengthZ();
      int steps = 2;
      int total = 0;
      int unblocked = 0;
      BiFunction<Vec3d[], BlockPos, BlockHitResult> tester = (ctxx, pos) -> {
         if (anchorPos != null && pos.equals(anchorPos)) {
            return null;
         }

         BlockState state = mc.world.getBlockState(pos);
         if (state.isAir()) {
            return null;
         }

         VoxelShape shape = state.getCollisionShape(mc.world, pos);
         return shape.isEmpty() ? null : shape.raycast(ctxx[0], ctxx[1], pos);
      };

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               Vec3d point = new Vec3d(box.minX + dx * xi / steps, box.minY + dy * yi / steps, box.minZ + dz * zi / steps);
               Vec3d[] ctx = new Vec3d[]{point, source};
               if (BlockView.raycast(point, source, ctx, tester, c -> null) == null) {
                  unblocked++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unblocked / total;
   }

   public record Target(LivingEntity entity, Vec3d pos, Box box, float armor, float toughness, float resistMult, int protPoints) {
      public static AnchorDamageUtil.Target of(LivingEntity living) {
         float armor = 0.0F;
         float toughness = 0.0F;
         int protPoints = 0;

         for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = living.getEquippedStack(slot);
            if (!stack.isEmpty()) {
               AttributeModifiersComponent modifiers = (AttributeModifiersComponent)stack.getOrDefault(
                  DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT
               );
               armor = (float)modifiers.applyOperations(EntityAttributes.ARMOR, armor, slot);
               toughness = (float)modifiers.applyOperations(EntityAttributes.ARMOR_TOUGHNESS, toughness, slot);
               protPoints += slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET ? 4 : 8;
            }
         }

         if (protPoints > 20) {
            protPoints = 20;
         }

         StatusEffectInstance resistance = living.getStatusEffect(StatusEffects.RESISTANCE);
         float resistMult = resistance != null ? Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1)) : 1.0F;
         return new AnchorDamageUtil.Target(living, living.getEntityPos(), living.getBoundingBox(), armor, toughness, resistMult, protPoints);
      }
   }
}
