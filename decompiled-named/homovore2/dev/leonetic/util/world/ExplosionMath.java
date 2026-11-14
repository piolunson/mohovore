package dev.leonetic.util.world;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class ExplosionMath {
   public static final double CRYSTAL_POWER = 6.0;
   public static final double EFFECT_RADIUS = 12.0;
   public static final double NO_POP_MARGIN = 1.0;
   private static final float WEAKEST_RAY = 4.2F;
   private static final double RAY_STEP = 0.3F;
   private static final float RAY_DECAY = 0.22500001F;
   private static final double WEAKEST_RAY_REACH = 5.6F;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   private ExplosionMath() {
      throw new AssertionError();
   }

   public static double damage(double distanceSquared, double exposure, double armor, double toughness, double resistance, int protection, int difficulty) {
      return damage(6.0, distanceSquared, exposure, armor, toughness, resistance, protection, difficulty);
   }

   public static double damage(
      double power, double distanceSquared, double exposure, double armor, double toughness, double resistance, int protection, int difficulty
   ) {
      double effectRadius = power * 2.0;
      if (!(power <= 0.0) && !(distanceSquared >= effectRadius * effectRadius)) {
         double impact = (1.0 - Math.sqrt(distanceSquared) / effectRadius) * exposure;
         if (impact <= 0.0) {
            return 0.0;
         }

         double raw = (impact * impact + impact) * (7.0 * power) + 1.0;

         raw = switch (difficulty) {
            case 0 -> 0.0;
            case 1 -> Math.min(raw * 0.5 + 1.0, raw);
            default -> raw;
            case 3 -> raw * 1.5;
         };
         double divisor = 2.0 + toughness * 0.25;
         double blocked = clamp(armor - raw / divisor, armor * 0.2, 20.0);
         raw *= 1.0 - blocked / 25.0;
         raw *= clamp(resistance, 0.0, 1.0);
         raw *= 1.0 - clamp(protection, 0.0, 20.0) / 25.0;
         return Math.max(0.0, raw);
      } else {
         return 0.0;
      }
   }

   public static boolean destroysBlock(World level, Vec3d source, BlockPos target) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(target, "target");
      double dx = target.getX() + 0.5 - source.x;
      double dy = target.getY() + 0.5 - source.y;
      double dz = target.getZ() + 0.5 - source.z;
      double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (length > 5.6F) {
         return false;
      }

      if (length < 1.0E-7) {
         return true;
      }

      dx /= length;
      dy /= length;
      dz /= length;
      double x = source.x;
      double y = source.y;
      double z = source.z;
      float strength = 4.2F;
      Mutable cursor = new Mutable();

      while (strength > 0.0F) {
         cursor.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
         if (!level.isInBuildLimit(cursor)) {
            return false;
         }

         BlockState state = level.getBlockState(cursor);
         FluidState fluid = level.getFluidState(cursor);
         if (!state.isAir() || !fluid.isEmpty()) {
            float resistance = Math.max(state.getBlock().getBlastResistance(), fluid.getBlastResistance());
            strength -= (resistance + 0.3F) * 0.3F;
         }

         if (strength > 0.0F && cursor.equals(target)) {
            return true;
         }

         x += dx * 0.3F;
         y += dy * 0.3F;
         z += dz * 0.3F;
         strength -= 0.22500001F;
      }

      return false;
   }

   public static boolean safeForSelf(double selfDamage, double maxSelfDamage, double health, boolean invulnerable) {
      return invulnerable || selfDamage <= maxSelfDamage && selfDamage + 1.0 < health;
   }

   public static double selfDamage(World level, PlayerEntity player, Vec3d source) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(player, "player");
      Objects.requireNonNull(source, "source");
      return player.getAbilities().invulnerable
         ? 0.0
         : damage(
            player.squaredDistanceTo(source),
            exposure(level, source, player),
            player.getArmor(),
            player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
            resistanceFactor(player),
            protectionPoints(player),
            level.getDifficulty().getId()
         );
   }

   public static float resistanceFactor(LivingEntity entity) {
      StatusEffectInstance resistance = Objects.requireNonNull(entity, "entity").getStatusEffect(StatusEffects.RESISTANCE);
      return resistance == null ? 1.0F : Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1));
   }

   public static int protectionPoints(PlayerEntity player) {
      int points = 0;

      for (EquipmentSlot slot : ARMOR_SLOTS) {
         ItemStack stack = Objects.requireNonNull(player, "player").getEquippedStack(slot);
         if (!stack.isEmpty()) {
            for (Entry<RegistryEntry<Enchantment>> enchantment : stack.getEnchantments().getEnchantmentEntries()) {
               int level = enchantment.getIntValue();
               if (((RegistryEntry)enchantment.getKey()).matchesKey(Enchantments.PROTECTION)) {
                  points += level;
               } else if (((RegistryEntry)enchantment.getKey()).matchesKey(Enchantments.BLAST_PROTECTION)) {
                  points += level * 2;
               }
            }
         }
      }

      return Math.min(points, 20);
   }

   public static float exposure(World level, Vec3d source, Entity entity) {
      return exposure(level, source, entity, null, null);
   }

   public static float exposureIgnoring(World level, Vec3d source, Entity entity, BlockPos ignored) {
      return exposure(level, source, entity, ignored, null);
   }

   private static float exposure(World level, Vec3d source, Entity entity, BlockPos ignored, BlockPos phantom) {
      Box box = Objects.requireNonNull(entity, "entity").getBoundingBox();
      double stepX = 1.0 / (box.getLengthX() * 2.0 + 1.0);
      double stepY = 1.0 / (box.getLengthY() * 2.0 + 1.0);
      double stepZ = 1.0 / (box.getLengthZ() * 2.0 + 1.0);
      if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
         ShapeContext collision = ShapeContext.of(entity);
         ExplosionMath.ExposureRayContext ray = new ExplosionMath.ExposureRayContext(level, source, collision, ignored, phantom);
         double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) * 0.5;
         double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) * 0.5;
         int visible = 0;
         int total = 0;

         for (double fx = 0.0; fx <= 1.0; fx += stepX) {
            for (double fy = 0.0; fy <= 1.0; fy += stepY) {
               for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                  Vec3d point = new Vec3d(
                     MathHelper.lerp(fx, box.minX, box.maxX) + offsetX,
                     MathHelper.lerp(fy, box.minY, box.maxY),
                     MathHelper.lerp(fz, box.minZ, box.maxZ) + offsetZ
                  );
                  ray.start = point;
                  if (!(Boolean)BlockView.raycast(point, source, ray, ExplosionMath::clipExposureBlock, ExplosionMath::exposureRayMiss)) {
                     visible++;
                  }

                  total++;
               }
            }
         }

         return total == 0 ? 0.0F : (float)visible / total;
      } else {
         return 0.0F;
      }
   }

   public static float exposure(World level, Vec3d source, Entity entity, BlockPos phantom) {
      return exposure(level, source, entity, null, phantom);
   }

   private static Boolean clipExposureBlock(ExplosionMath.ExposureRayContext ray, BlockPos pos) {
      VoxelShape shape = pos.equals(ray.ignored)
         ? VoxelShapes.empty()
         : (pos.equals(ray.phantom) ? VoxelShapes.fullCube() : ray.level.getBlockState(pos).getCollisionShape(ray.level, pos, ray.collision));
      return shape.raycast(ray.start, ray.source, pos) != null ? Boolean.TRUE : null;
   }

   private static Boolean exposureRayMiss(ExplosionMath.ExposureRayContext ignored) {
      return Boolean.FALSE;
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static final class ExposureRayContext {
      private final World level;
      private final Vec3d source;
      private final ShapeContext collision;
      private final BlockPos ignored;
      private final BlockPos phantom;
      private Vec3d start;

      private ExposureRayContext(World level, Vec3d source, ShapeContext collision, BlockPos ignored, BlockPos phantom) {
         this.level = level;
         this.source = source;
         this.collision = collision;
         this.ignored = ignored;
         this.phantom = phantom;
      }
   }
}
