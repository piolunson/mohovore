package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.network.NetworkThreadExecutor;
import dev.piolunson.v2.util.world.ExplosionMath;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

public final class BasePlaceModule extends Module {
   private static final int SCAN_RADIUS = 6;
   private static final double NO_POP_MARGIN = 1.0;
   public final Setting<Double> enemyRange = this.number("EnemyRange", (double)7.0, (double)0.1, (double)15.0)
      .describe("How close a player has to be before a base is planned against them.");
   public final Setting<Double> minDamage = this.number("MinDamage", (double)5.0, (double)0.0, (double)36.0)
      .describe("Damage a new base must deal; Crystal Placer's own minimum still applies.");
   private volatile BasePlaceModule.Candidate candidate;

   public BasePlaceModule() {
      super("BasePlace", Category.COMBAT, "Places obsidian for Crystal Placer where no existing base does as much damage.");
   }

   @Override
   public void onDisable() {
      this.candidate = null;
   }

   @Override
   public void onTick() {
      this.candidate = this.plan();
   }

   public BasePlaceModule.Candidate candidate() {
      return this.isEnabled() ? this.candidate : null;
   }

   private BasePlaceModule.Candidate plan() {
      CrystalPlacerModule placer = Homovore2Client.modules().get(CrystalPlacerModule.class);
      if (mc.player != null
         && mc.world != null
         && placer != null
         && placer.isEnabled()
         && placer.place.getValue()
         && NetworkThreadExecutor.isConnected()
         && InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.HOTBAR_SCOPE).found()
         && InventoryUtil.find(Items.END_CRYSTAL, InventoryUtil.HOTBAR_SCOPE).found()) {
         PlayerEntity target = this.findTarget();
         if (target == null) {
            return null;
         }

         Vec3d eye = mc.player.getEyePos();
         Vec3d feet = mc.player.getEntityPos();
         Vec3d targetFeet = target.getEntityPos();
         double placeRange = 4.5;
         double breakRange = 3.0;
         double reach = 5.154;
         double requiredDamage = Math.max(this.minDamage.getValue(), placer.minDamage.getValue());
         double maxSelfDamage = placer.maxSelfDamage.getValue();
         double health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
         boolean invulnerable = mc.player.getAbilities().invulnerable;
         int difficulty = mc.world.getDifficulty().getId();
         double targetArmor = target.getArmor();
         double targetToughness = target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
         double targetResistance = ExplosionMath.resistanceFactor(target);
         int targetProtection = ExplosionMath.protectionPoints(target);
         double selfArmor = mc.player.getArmor();
         double selfToughness = mc.player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
         double selfResistance = ExplosionMath.resistanceFactor(mc.player);
         int selfProtection = ExplosionMath.protectionPoints(mc.player);
         BlockPos origin = mc.player.getBlockPos();
         BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
         Mutable above = new Mutable();
         int radiusSquared = 36;
         int top = target.getBlockPos().getY() - 1;
         int bottom = origin.getY() - 6;

         for (int y = top; y >= bottom; y--) {
            int dy = y - origin.getY();
            if (dy * dy <= radiusSquared) {
               BlockPos layerBest = null;
               double layerDamage = 0.0;

               for (int dx = -6; dx <= 6; dx++) {
                  for (int dz = -6; dz <= 6; dz++) {
                     if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        int x = origin.getX() + dx;
                        int z = origin.getZ() + dz;
                        double cx = x + 0.5;
                        double cz = z + 0.5;
                        double cy = y + 1.0;
                        if (!(squaredDistance(feet.x, feet.y, feet.z, cx, y + 0.5, cz) > radiusSquared)
                           && !(squaredDistance(eye.x, eye.y, eye.z, cx, y + 0.5, cz) > reach * reach)
                           && !(distanceSquaredToBox(eye, x, y, z, x + 1.0, y + 1.0, z + 1.0) > placeRange * placeRange)
                           && !(distanceSquaredToBox(eye, cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0) > breakRange * breakRange)) {
                           double targetDistance = squaredDistance(targetFeet.x, targetFeet.y, targetFeet.z, cx, cy, cz);
                           double bound = ExplosionMath.damage(
                              targetDistance, 1.0, targetArmor, targetToughness, targetResistance, targetProtection, difficulty
                           );
                           if (!(bound < requiredDamage) && !(bound <= layerDamage)) {
                              BlockPos base = new BlockPos(x, y, z);
                              if (this.placeable(base, obsidian, above)) {
                                 Vec3d source = new Vec3d(cx, cy, cz);
                                 if (!invulnerable) {
                                    double selfDamage = ExplosionMath.damage(
                                       squaredDistance(feet.x, feet.y, feet.z, cx, cy, cz),
                                       ExplosionMath.exposure(mc.world, source, mc.player, base),
                                       selfArmor,
                                       selfToughness,
                                       selfResistance,
                                       selfProtection,
                                       difficulty
                                    );
                                    if (selfDamage > maxSelfDamage || selfDamage + 1.0 >= health) {
                                       continue;
                                    }
                                 }

                                 double damage = ExplosionMath.damage(
                                    targetDistance,
                                    ExplosionMath.exposure(mc.world, source, target, base),
                                    targetArmor,
                                    targetToughness,
                                    targetResistance,
                                    targetProtection,
                                    difficulty
                                 );
                                 if (!(damage < requiredDamage) && !(damage <= layerDamage)) {
                                    layerDamage = damage;
                                    layerBest = base;
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               if (layerBest != null) {
                  return new BasePlaceModule.Candidate(layerBest, target.getId(), layerDamage);
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private boolean placeable(BlockPos base, BlockState obsidian, Mutable above) {
      if (!mc.world.isOutOfHeightLimit(base)
         && !mc.world.isOutOfHeightLimit(base.up())
         && mc.world.getBlockState(base).isReplaceable()
         && mc.world.getBlockState(above.set(base, 0, 1, 0)).isAir()
         && !mc.player.getBoundingBox().intersects(new Box(base))
         && mc.world.canPlace(obsidian, base, ShapeContext.absent())) {
         Box space = new Box(base.getX(), base.getY() + 1.0, base.getZ(), base.getX() + 1.0, base.getY() + 3.0, base.getZ() + 1.0);
         return mc.world.getOtherEntities(mc.player, space, entity -> !(entity instanceof ItemEntity) && !entity.isSpectator()).isEmpty();
      } else {
         return false;
      }
   }

   private PlayerEntity findTarget() {
      double rangeSquared = this.enemyRange.getValue() * this.enemyRange.getValue();
      PlayerEntity best = null;
      double bestDistance = Double.MAX_VALUE;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player && !player.isDead() && !player.isSpectator() && !Homovore2Client.friends().isFriend(player)) {
            double distance = mc.player.squaredDistanceTo(player);
            if (!(distance > rangeSquared) && !(distance >= bestDistance)) {
               bestDistance = distance;
               best = player;
            }
         }
      }

      return best;
   }

   private static double distanceSquaredToBox(Vec3d point, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = point.x < minX ? minX - point.x : (point.x > maxX ? point.x - maxX : 0.0);
      double dy = point.y < minY ? minY - point.y : (point.y > maxY ? point.y - maxY : 0.0);
      double dz = point.z < minZ ? minZ - point.z : (point.z > maxZ ? point.z - maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private static double squaredDistance(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = ax - bx;
      double dy = ay - by;
      double dz = az - bz;
      return dx * dx + dy * dy + dz * dz;
   }

   public record Candidate(BlockPos base, int targetId, double damage) {
   }
}
