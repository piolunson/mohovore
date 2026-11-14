package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.mixin.ClientInputAccessor;
import dev.leonetic.util.network.NetworkThreadExecutor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;

public final class VelocityModule extends Module {
   private static final Direction[] PUSH_DIRECTIONS = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
   private static final double GRIM_PUSH_BUDGET = 0.145;
   public final Setting<VelocityModule.Mode> mode = this.mode("Mode", VelocityModule.Mode.NORMAL);
   public final Setting<Boolean> phaseOnly = this.bool("PhaseOnly", true);
   public final Setting<Boolean> knockback = this.bool("Knockback", true);
   public final Setting<Boolean> explosions = this.bool("Explosions", false);
   public final Setting<Boolean> entityPush = this.bool("EntityPush", true);
   public final Setting<Boolean> blockPush = this.bool("BlockPush", true);
   private Vec3d counterDirection = Vec3d.ZERO;
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

   public Vec3d adjustGrimKnockback(Vec3d movement) {
      if (!this.isEnabled() || !this.knockback.getValue() || this.mode.getValue() != VelocityModule.Mode.GRIM) {
         return null;
      }

      if (!this.phaseConditionMet()) {
         return null;
      }

      double horizontal = Math.hypot(movement.x, movement.z);
      if (horizontal > 1.0E-6) {
         this.counterDirection = new Vec3d(movement.x / horizontal, 0.0, movement.z / horizontal);
         this.counterTicks = 6;
      } else {
         this.clearCounter();
      }

      if (!this.blockPush.getValue()) {
         return null;
      }

      Vec3d budget = this.suffocationPushBudget();
      double x = reduceTowardZero(movement.x, budget.x);
      double z = reduceTowardZero(movement.z, budget.z);
      return x == movement.x && z == movement.z ? null : new Vec3d(x, movement.y, z);
   }

   public void applyGrimCounterInput(ClientPlayerEntity player) {
      if (this.staticCancelVelocity) {
         this.sendStaticCorrection();
         this.staticCancelVelocity = false;
      }

      if (this.counterTicks > 0 && this.isEnabled() && this.mode.getValue() == VelocityModule.Mode.GRIM && this.phaseConditionMet()) {
         Vec3d movement = player.getVelocity();
         double horizontalSq = movement.x * movement.x + movement.z * movement.z;
         if (!(horizontalSq < 9.0E-6) && !(movement.x * this.counterDirection.x + movement.z * this.counterDirection.z <= 0.0)) {
            double radians = Math.toRadians(player.getYaw());
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
                     double dot = worldX * movement.x + worldZ * movement.z;
                     if (!(dot >= bestDot)) {
                        bestDot = dot;
                        bestSideways = sideways;
                        bestForward = forward;
                     }
                  }
               }
            }

            PlayerInput current = player.input.playerInput;
            player.input.playerInput = new PlayerInput(
               bestForward > 0, bestForward < 0, bestSideways > 0, bestSideways < 0, false, current.sneak(), current.sprint() && bestForward > 0
            );
            ((ClientInputAccessor)player.input).homovore$setMoveVector(new Vec2f(bestSideways, bestForward).normalize());
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
         return mc.player != null && mc.world != null ? mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox()).iterator().hasNext() : false;
      }
   }

   private boolean staticConditionMet() {
      return this.phaseConditionMet() && mc.player != null && !mc.player.isGliding()
         ? !mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed() && !mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()
         : false;
   }

   private void sendStaticCorrection() {
      if (this.staticCancelVelocity && mc.player != null && NetworkThreadExecutor.isConnected()) {
         Vec3d position = mc.player.getEntityPos();
         boolean onGround = mc.player.isOnGround();
         boolean horizontalCollision = mc.player.horizontalCollision;
         BlockPos digPos = mc.player.isCrawling() ? mc.player.getBlockPos() : mc.player.getBlockPos().up();
         RotationManager.Angles rotation = Homovore2Client.rotations().movementRotation();
         if (rotation == null) {
            rotation = Homovore2Client.rotations().serverRotation();
         }

         RotationManager.Angles correctionRotation = rotation;
         NetworkThreadExecutor.execute(context -> {
            context.send(new Full(position.x, position.y, position.z, correctionRotation.yaw(), correctionRotation.pitch(), onGround, horizontalCollision));
            context.send(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, digPos, Direction.DOWN, context.nextSequence()));
            context.limits().recordBreak(digPos, Direction.DOWN);
         }).exceptionally(throwable -> {
            Homovore2.LOGGER.debug("Velocity correction failed", throwable);
            return null;
         });
      }
   }

   private Vec3d suffocationPushBudget() {
      ClientPlayerEntity player = mc.player;
      Box bounds = player.getBoundingBox();
      double sample = player.getWidth() * 0.35;
      double xBudget = 0.0;
      double zBudget = 0.0;

      for (int xSign : new int[]{-1, 1}) {
         for (int zSign : new int[]{-1, 1}) {
            Direction direction = this.closestOpenDirection(player.getX() + sample * xSign, player.getZ() + sample * zSign, bounds);
            if (direction != null) {
               if (direction.getAxis() == Axis.X) {
                  xBudget += 0.145;
               } else {
                  zBudget += 0.145;
               }
            }
         }
      }

      return new Vec3d(xBudget, 0.0, zBudget);
   }

   private Direction closestOpenDirection(double x, double z, Box bounds) {
      BlockPos pos = BlockPos.ofFloored(x, mc.player.getY(), z);
      if (!this.suffocatesAt(pos, bounds)) {
         return null;
      }

      double relativeX = x - pos.getX();
      double relativeZ = z - pos.getZ();
      Direction closest = null;
      double closestDistance = Double.MAX_VALUE;

      for (Direction direction : PUSH_DIRECTIONS) {
         double relative = direction.getAxis() == Axis.X ? relativeX : relativeZ;
         double distance = direction.getDirection() == AxisDirection.POSITIVE ? 1.0 - relative : relative;
         if (!(distance >= closestDistance) && !this.suffocatesAt(pos.offset(direction), bounds)) {
            closestDistance = distance;
            closest = direction;
         }
      }

      return closest;
   }

   private boolean suffocatesAt(BlockPos pos, Box bounds) {
      Box column = new Box(pos.getX(), bounds.minY, pos.getZ(), pos.getX() + 1.0, bounds.maxY, pos.getZ() + 1.0).contract(1.0E-7);
      return mc.world.canCollide(mc.player, column);
   }

   private static double reduceTowardZero(double value, double amount) {
      return Math.copySign(Math.max(0.0, Math.abs(value) - amount), value);
   }

   private void clearCounter() {
      this.counterDirection = Vec3d.ZERO;
      this.counterTicks = 0;
   }

   public enum Mode {
      NORMAL,
      GRIM,
      STATIC;
   }
}
