package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.world.ExplosionMath;
import net.minecraft.class_1542;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3726;
import net.minecraft.class_5134;
import net.minecraft.class_2338.class_2339;

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
      if (mc.field_1724 != null
         && mc.field_1687 != null
         && placer != null
         && placer.isEnabled()
         && placer.place.getValue()
         && NetworkThreadExecutor.isConnected()
         && InventoryUtil.find(class_1802.field_8281, InventoryUtil.HOTBAR_SCOPE).found()
         && InventoryUtil.find(class_1802.field_8301, InventoryUtil.HOTBAR_SCOPE).found()) {
         class_1657 target = this.findTarget();
         if (target == null) {
            return null;
         }

         class_243 eye = mc.field_1724.method_33571();
         class_243 feet = mc.field_1724.method_73189();
         class_243 targetFeet = target.method_73189();
         double placeRange = 4.5;
         double breakRange = 3.0;
         double reach = 5.154;
         double requiredDamage = Math.max(this.minDamage.getValue(), placer.minDamage.getValue());
         double maxSelfDamage = placer.maxSelfDamage.getValue();
         double health = mc.field_1724.method_6032() + mc.field_1724.method_6067();
         boolean invulnerable = mc.field_1724.method_31549().field_7480;
         int difficulty = mc.field_1687.method_8407().method_5461();
         double targetArmor = target.method_6096();
         double targetToughness = target.method_45325(class_5134.field_23725);
         double targetResistance = ExplosionMath.resistanceFactor(target);
         int targetProtection = ExplosionMath.protectionPoints(target);
         double selfArmor = mc.field_1724.method_6096();
         double selfToughness = mc.field_1724.method_45325(class_5134.field_23725);
         double selfResistance = ExplosionMath.resistanceFactor(mc.field_1724);
         int selfProtection = ExplosionMath.protectionPoints(mc.field_1724);
         class_2338 origin = mc.field_1724.method_24515();
         class_2680 obsidian = class_2246.field_10540.method_9564();
         class_2339 above = new class_2339();
         int radiusSquared = 36;
         int top = target.method_24515().method_10264() - 1;
         int bottom = origin.method_10264() - 6;

         for (int y = top; y >= bottom; y--) {
            int dy = y - origin.method_10264();
            if (dy * dy <= radiusSquared) {
               class_2338 layerBest = null;
               double layerDamage = 0.0;

               for (int dx = -6; dx <= 6; dx++) {
                  for (int dz = -6; dz <= 6; dz++) {
                     if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        int x = origin.method_10263() + dx;
                        int z = origin.method_10260() + dz;
                        double cx = x + 0.5;
                        double cz = z + 0.5;
                        double cy = y + 1.0;
                        if (!(squaredDistance(feet.field_1352, feet.field_1351, feet.field_1350, cx, y + 0.5, cz) > radiusSquared)
                           && !(squaredDistance(eye.field_1352, eye.field_1351, eye.field_1350, cx, y + 0.5, cz) > reach * reach)
                           && !(distanceSquaredToBox(eye, x, y, z, x + 1.0, y + 1.0, z + 1.0) > placeRange * placeRange)
                           && !(distanceSquaredToBox(eye, cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0) > breakRange * breakRange)) {
                           double targetDistance = squaredDistance(targetFeet.field_1352, targetFeet.field_1351, targetFeet.field_1350, cx, cy, cz);
                           double bound = ExplosionMath.damage(
                              targetDistance, 1.0, targetArmor, targetToughness, targetResistance, targetProtection, difficulty
                           );
                           if (!(bound < requiredDamage) && !(bound <= layerDamage)) {
                              class_2338 base = new class_2338(x, y, z);
                              if (this.placeable(base, obsidian, above)) {
                                 class_243 source = new class_243(cx, cy, cz);
                                 if (!invulnerable) {
                                    double selfDamage = ExplosionMath.damage(
                                       squaredDistance(feet.field_1352, feet.field_1351, feet.field_1350, cx, cy, cz),
                                       ExplosionMath.exposure(mc.field_1687, source, mc.field_1724, base),
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
                                    ExplosionMath.exposure(mc.field_1687, source, target, base),
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
                  return new BasePlaceModule.Candidate(layerBest, target.method_5628(), layerDamage);
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private boolean placeable(class_2338 base, class_2680 obsidian, class_2339 above) {
      if (!mc.field_1687.method_31606(base)
         && !mc.field_1687.method_31606(base.method_10084())
         && mc.field_1687.method_8320(base).method_45474()
         && mc.field_1687.method_8320(above.method_25504(base, 0, 1, 0)).method_26215()
         && !mc.field_1724.method_5829().method_994(new class_238(base))
         && mc.field_1687.method_8628(obsidian, base, class_3726.method_16194())) {
         class_238 space = new class_238(
            base.method_10263(),
            base.method_10264() + 1.0,
            base.method_10260(),
            base.method_10263() + 1.0,
            base.method_10264() + 3.0,
            base.method_10260() + 1.0
         );
         return mc.field_1687.method_8333(mc.field_1724, space, entity -> !(entity instanceof class_1542) && !entity.method_7325()).isEmpty();
      } else {
         return false;
      }
   }

   private class_1657 findTarget() {
      double rangeSquared = this.enemyRange.getValue() * this.enemyRange.getValue();
      class_1657 best = null;
      double bestDistance = Double.MAX_VALUE;

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player != mc.field_1724 && !player.method_29504() && !player.method_7325() && !Homovore2Client.friends().isFriend(player)) {
            double distance = mc.field_1724.method_5858(player);
            if (!(distance > rangeSquared) && !(distance >= bestDistance)) {
               bestDistance = distance;
               best = player;
            }
         }
      }

      return best;
   }

   private static double distanceSquaredToBox(class_243 point, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = point.field_1352 < minX ? minX - point.field_1352 : (point.field_1352 > maxX ? point.field_1352 - maxX : 0.0);
      double dy = point.field_1351 < minY ? minY - point.field_1351 : (point.field_1351 > maxY ? point.field_1351 - maxY : 0.0);
      double dz = point.field_1350 < minZ ? minZ - point.field_1350 : (point.field_1350 > maxZ ? point.field_1350 - maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private static double squaredDistance(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = ax - bx;
      double dy = ay - by;
      double dz = az - bz;
      return dx * dx + dy * dy + dz * dz;
   }

   public record Candidate(class_2338 base, int targetId, double damage) {
   }
}
