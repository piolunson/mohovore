package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.mixin.ClientInputAccessor;
import dev.leonetic.util.network.NetworkThreadExecutor;
import net.minecraft.class_10185;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_241;
import net.minecraft.class_243;
import net.minecraft.class_2846;
import net.minecraft.class_746;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_2350.class_2352;
import net.minecraft.class_2828.class_2830;
import net.minecraft.class_2846.class_2847;

public final class VelocityModule extends Module {
   private static final class_2350[] PUSH_DIRECTIONS = new class_2350[]{
      class_2350.field_11039, class_2350.field_11034, class_2350.field_11043, class_2350.field_11035
   };
   private static final double GRIM_PUSH_BUDGET = 0.145;
   public final Setting<VelocityModule.Mode> mode = this.mode("Mode", VelocityModule.Mode.NORMAL);
   public final Setting<Boolean> phaseOnly = this.bool("PhaseOnly", true);
   public final Setting<Boolean> knockback = this.bool("Knockback", true);
   public final Setting<Boolean> explosions = this.bool("Explosions", false);
   public final Setting<Boolean> entityPush = this.bool("EntityPush", true);
   public final Setting<Boolean> blockPush = this.bool("BlockPush", true);
   private class_243 counterDirection = class_243.field_1353;
   private int counterTicks;
   private boolean staticCancelVelocity;

   public VelocityModule() {
      super("No Velocity", Category.MOVEMENT, "Cancels knockback, explosions, and entity/block push.");
   }

   @Override
   public void onEnable() {
      this.clearCounter();
      this.staticCancelVelocity = false;
   }

   @Override
   public void onDisable() {
      this.sendStaticCorrection();
      this.clearCounter();
      this.staticCancelVelocity = false;
   }

   public boolean shouldCancelKnockback() {
      if (this.isEnabled() && this.knockback.getValue()) {
         if (this.mode.getValue() == VelocityModule.Mode.NORMAL) {
            return this.phaseConditionMet();
         } else if (this.mode.getValue() == VelocityModule.Mode.STATIC && this.staticConditionMet()) {
            this.staticCancelVelocity = true;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public class_243 adjustGrimKnockback(class_243 movement) {
      if (!this.isEnabled() || !this.knockback.getValue() || this.mode.getValue() != VelocityModule.Mode.GRIM) {
         return null;
      }

      if (!this.phaseConditionMet()) {
         return null;
      }

      double horizontal = Math.hypot(movement.field_1352, movement.field_1350);
      if (horizontal > 1.0E-6) {
         this.counterDirection = new class_243(movement.field_1352 / horizontal, 0.0, movement.field_1350 / horizontal);
         this.counterTicks = 6;
      } else {
         this.clearCounter();
      }

      if (!this.blockPush.getValue()) {
         return null;
      }

      class_243 budget = this.suffocationPushBudget();
      double x = reduceTowardZero(movement.field_1352, budget.field_1352);
      double z = reduceTowardZero(movement.field_1350, budget.field_1350);
      return x == movement.field_1352 && z == movement.field_1350 ? null : new class_243(x, movement.field_1351, z);
   }

   public void applyGrimCounterInput(class_746 player) {
      if (this.staticCancelVelocity) {
         this.sendStaticCorrection();
         this.staticCancelVelocity = false;
      }

      if (this.counterTicks > 0 && this.isEnabled() && this.mode.getValue() == VelocityModule.Mode.GRIM && this.phaseConditionMet()) {
         class_243 movement = player.method_18798();
         double horizontalSq = movement.field_1352 * movement.field_1352 + movement.field_1350 * movement.field_1350;
         if (!(horizontalSq < 9.0E-6)
            && !(movement.field_1352 * this.counterDirection.field_1352 + movement.field_1350 * this.counterDirection.field_1350 <= 0.0)) {
            double radians = Math.toRadians(player.method_36454());
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            int bestSideways = 0;
            int bestForward = 0;
            double bestDot = Double.MAX_VALUE;

            for (int sideways = -1; sideways <= 1; sideways++) {
               for (int forward = -1; forward <= 1; forward++) {
                  if (sideways != 0 || forward != 0) {
                     double length = Math.hypot(sideways, forward);
                     double worldX = (sideways * cos - forward * sin) / length;
                     double worldZ = (forward * cos + sideways * sin) / length;
                     double dot = worldX * movement.field_1352 + worldZ * movement.field_1350;
                     if (!(dot >= bestDot)) {
                        bestDot = dot;
                        bestSideways = sideways;
                        bestForward = forward;
                     }
                  }
               }
            }

            class_10185 current = player.field_3913.field_54155;
            player.field_3913.field_54155 = new class_10185(
               bestForward > 0, bestForward < 0, bestSideways > 0, bestSideways < 0, false, current.comp_3164(), current.comp_3165() && bestForward > 0
            );
            ((ClientInputAccessor)player.field_3913).homovore$setMoveVector(new class_241(bestSideways, bestForward).method_35581());
            this.counterTicks--;
         } else {
            this.clearCounter();
         }
      } else {
         this.clearCounter();
      }
   }

   public boolean shouldCancelEntityPush() {
      return this.isEnabled() && this.entityPush.getValue();
   }

   public boolean shouldCancelBlockPush() {
      return this.isEnabled() && this.blockPush.getValue() && this.phaseConditionMet();
   }

   public boolean shouldCancelExplosion() {
      if (this.isEnabled() && this.explosions.getValue()) {
         if (this.mode.getValue() == VelocityModule.Mode.NORMAL) {
            return this.phaseConditionMet();
         } else if (this.mode.getValue() == VelocityModule.Mode.STATIC && this.staticConditionMet()) {
            this.staticCancelVelocity = true;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean phaseConditionMet() {
      if (!this.phaseOnly.getValue()) {
         return true;
      } else {
         return mc.field_1724 != null && mc.field_1687 != null
            ? mc.field_1687.method_20812(mc.field_1724, mc.field_1724.method_5829()).iterator().hasNext()
            : false;
      }
   }

   private boolean staticConditionMet() {
      return this.phaseConditionMet() && mc.field_1724 != null && !mc.field_1724.method_6128()
         ? !mc.field_1690.field_1894.method_1434()
            && !mc.field_1690.field_1881.method_1434()
            && !mc.field_1690.field_1913.method_1434()
            && !mc.field_1690.field_1849.method_1434()
         : false;
   }

   private void sendStaticCorrection() {
      if (this.staticCancelVelocity && mc.field_1724 != null && NetworkThreadExecutor.isConnected()) {
         class_243 position = mc.field_1724.method_73189();
         boolean onGround = mc.field_1724.method_24828();
         boolean horizontalCollision = mc.field_1724.field_5976;
         class_2338 digPos = mc.field_1724.method_20448() ? mc.field_1724.method_24515() : mc.field_1724.method_24515().method_10084();
         RotationManager.Angles rotation = Homovore2Client.rotations().movementRotation();
         if (rotation == null) {
            rotation = Homovore2Client.rotations().serverRotation();
         }

         RotationManager.Angles correctionRotation = rotation;
         NetworkThreadExecutor.execute(
               context -> {
                  context.send(
                     new class_2830(
                        position.field_1352,
                        position.field_1351,
                        position.field_1350,
                        correctionRotation.yaw(),
                        correctionRotation.pitch(),
                        onGround,
                        horizontalCollision
                     )
                  );
                  context.send(new class_2846(class_2847.field_12973, digPos, class_2350.field_11033, context.nextSequence()));
                  context.limits().recordBreak(digPos, class_2350.field_11033);
               }
            )
            .exceptionally(throwable -> {
               Homovore2.LOGGER.debug("Velocity correction failed", throwable);
               return null;
            });
      }
   }

   private class_243 suffocationPushBudget() {
      class_746 player = mc.field_1724;
      class_238 bounds = player.method_5829();
      double sample = player.method_17681() * 0.35;
      double xBudget = 0.0;
      double zBudget = 0.0;

      for (int xSign : new int[]{-1, 1}) {
         for (int zSign : new int[]{-1, 1}) {
            class_2350 direction = this.closestOpenDirection(player.method_23317() + sample * xSign, player.method_23321() + sample * zSign, bounds);
            if (direction != null) {
               if (direction.method_10166() == class_2351.field_11048) {
                  xBudget += 0.145;
               } else {
                  zBudget += 0.145;
               }
            }
         }
      }

      return new class_243(xBudget, 0.0, zBudget);
   }

   private class_2350 closestOpenDirection(double x, double z, class_238 bounds) {
      class_2338 pos = class_2338.method_49637(x, mc.field_1724.method_23318(), z);
      if (!this.suffocatesAt(pos, bounds)) {
         return null;
      }

      double relativeX = x - pos.method_10263();
      double relativeZ = z - pos.method_10260();
      class_2350 closest = null;
      double closestDistance = Double.MAX_VALUE;

      for (class_2350 direction : PUSH_DIRECTIONS) {
         double relative = direction.method_10166() == class_2351.field_11048 ? relativeX : relativeZ;
         double distance = direction.method_10171() == class_2352.field_11056 ? 1.0 - relative : relative;
         if (!(distance >= closestDistance) && !this.suffocatesAt(pos.method_10093(direction), bounds)) {
            closestDistance = distance;
            closest = direction;
         }
      }

      return closest;
   }

   private boolean suffocatesAt(class_2338 pos, class_238 bounds) {
      class_238 column = new class_238(
            pos.method_10263(), bounds.field_1322, pos.method_10260(), pos.method_10263() + 1.0, bounds.field_1325, pos.method_10260() + 1.0
         )
         .method_1011(1.0E-7);
      return mc.field_1687.method_39454(mc.field_1724, column);
   }

   private static double reduceTowardZero(double value, double amount) {
      return Math.copySign(Math.max(0.0, Math.abs(value) - amount), value);
   }

   private void clearCounter() {
      this.counterDirection = class_243.field_1353;
      this.counterTicks = 0;
   }

   public enum Mode {
      NORMAL,
      GRIM,
      STATIC;
   }
}
