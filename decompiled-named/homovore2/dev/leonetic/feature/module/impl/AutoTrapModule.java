package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.render.BlockRender;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.world.PlacementWorldSnapshot;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;

public final class AutoTrapModule extends Module {
   private static final double MAX_SAMPLE_SPEED = 8.0;
   private static final double MAX_PREDICT_DISTANCE = 3.5;
   private static final double MAX_ELYTRA_PREDICT_DISTANCE = 5.0;
   private static final double[] CAGE_BACKOFF = new double[]{1.0, 0.75, 0.5, 0.25, 0.0};
   private static final double[] ELYTRA_CAGE_BACKOFF = new double[]{1.0, 0.75};
   private static final double[] LOCKED_CAGE_BACKOFF = new double[]{1.0};
   private static final int ELYTRA_LOCK_TICKS = 8;
   private static final double ELYTRA_IMPACT_SPEED = 0.8;
   private static final double TARGET_RANGE = 8.0;
   private static final double REACH = 5.0;
   private static final boolean SWING = false;
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.5, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> sideColor = this.color("SideColor", 255, 0, 0, 44).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 0, 0, 0).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)1.0, (double)0.1, (double)8.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(() -> this.render.getValue()).page("Render");
   private final Set<BlockPos> wanted = new HashSet<>(16);
   private final List<BlockPos> order = new ArrayList<>(16);
   private final Set<BlockPos> owned = new HashSet<>(16);
   private final List<BlockPos> scratch = new ArrayList<>(16);
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private UUID trackedId;
   private Vec3d prevPos;
   private Vec3d filteredVelocity = Vec3d.ZERO;
   private Vec3d velocityChange = Vec3d.ZERO;
   private Vec3d lockedElytraFeet;
   private int elytraLockTicks;
   private int satisfiedCells;

   public AutoTrapModule() {
      super("AutoTrap", Category.COMBAT, "Predicts a target's path and encases them in air-placed obsidian.");
   }

   @Override
   public void onEnable() {
      this.resetTracking();
      this.renderMap.clear();
   }

   @Override
   public void onDisable() {
      this.releaseAll();
      this.resetTracking();
      this.renderMap.clear();
   }

   @Override
   public void onTick() {
      if (mc.player != null && mc.world != null && mc.currentScreen == null && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available() && !inventory.deadOrDying() && InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.HOTBAR_SCOPE).found()) {
            LivingEntity target = this.findTarget();
            if (target == null) {
               this.releaseAll();
               this.resetTracking();
            } else {
               Vec3d velocity = this.trackVelocity(target);
               Vec3d anchor = target.getEntityPos();
               Vec3d eye = inventory.eyePosition();
               AutoTrapModule.Prediction prediction = this.predictFeet(target, anchor, velocity);
               boolean useElytraLock = this.validElytraLock(anchor, velocity, target.isGliding());
               if (this.lockedElytraFeet != null && !useElytraLock) {
                  this.clearElytraLock();
               }

               boolean flightMode = target.isGliding() || useElytraLock;
               double wallThreshold = useElytraLock ? 0.8 : 0.1;
               boolean wallMode = target.isGliding() && velocity.length() >= wallThreshold;
               Vec3d desiredFeet = useElytraLock ? this.lockedElytraFeet : prediction.feet();
               Vec3d displacement = desiredFeet.subtract(anchor);
               Vec3d predFeet = prediction.feet();
               Set<BlockPos> bestWanted = new HashSet<>();
               List<BlockPos> bestOrder = new ArrayList<>();
               List<BlockPos> bestScratch = new ArrayList<>();
               int bestCoverage = -1;
               int bestSatisfied = -1;
               boolean bestLockable = false;
               boolean bestPlaceable = false;
               double[] backoff = useElytraLock ? LOCKED_CAGE_BACKOFF : (flightMode ? ELYTRA_CAGE_BACKOFF : CAGE_BACKOFF);

               for (double fraction : backoff) {
                  Vec3d candidateFeet = anchor.add(displacement.multiply(fraction));
                  if (wallMode) {
                     this.buildInterceptionWall(candidateFeet, target, velocity, eye);
                  } else {
                     this.buildCage(candidateFeet, target, eye, flightMode);
                  }

                  int candidateReachable = this.wanted.size();
                  int candidateCoverage = candidateReachable + this.satisfiedCells;
                  boolean candidateLockable = wallMode && candidateReachable >= 1;
                  boolean candidatePlaceable = candidateReachable > 0;
                  if (better(
                     candidateLockable, candidatePlaceable, candidateCoverage, this.satisfiedCells, bestLockable, bestPlaceable, bestCoverage, bestSatisfied
                  )) {
                     bestCoverage = candidateCoverage;
                     bestSatisfied = this.satisfiedCells;
                     bestLockable = candidateLockable;
                     bestPlaceable = candidatePlaceable;
                     predFeet = candidateFeet;
                     bestWanted.clear();
                     bestWanted.addAll(this.wanted);
                     bestOrder.clear();
                     bestOrder.addAll(this.order);
                     bestScratch.clear();
                     bestScratch.addAll(this.scratch);
                  }
               }

               this.wanted.clear();
               this.wanted.addAll(bestWanted);
               this.order.clear();
               this.order.addAll(bestOrder);
               this.scratch.clear();
               this.scratch.addAll(bestScratch);
               boolean createdElytraLock = wallMode && !useElytraLock && !this.wanted.isEmpty();
               if (createdElytraLock) {
                  this.lockedElytraFeet = predFeet;
                  this.elytraLockTicks = 8;
               } else if (useElytraLock) {
                  this.elytraLockTicks--;
               }

               if (flightMode && (useElytraLock || createdElytraLock)) {
                  this.prioritizeElytraPlacements(predFeet, velocity);
               }

               boolean commit = !this.wanted.isEmpty() && (!flightMode || useElytraLock || createdElytraLock);
               if (!commit) {
                  this.wanted.clear();
                  this.order.clear();
               }

               this.releaseUnwanted();
               if (commit) {
                  this.submit();
               }
            }
         } else {
            this.releaseAll();
            this.resetTracking();
         }
      } else {
         this.releaseAll();
         this.resetTracking();
      }
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (!this.render.getValue()) {
         this.renderMap.clear();
      } else {
         long now = System.currentTimeMillis();

         for (BlockPos pos : this.wanted) {
            this.renderMap.put(pos, now);
         }

         this.renderMap
            .entrySet()
            .removeIf(
               entry -> {
                  if (BlockRender.expired(entry.getValue(), now, this.fadeTime.getValue())) {
                     return true;
                  }

                  BlockRender.cuboid(
                     entry.getKey(),
                     this.lineColor.getValue(),
                     this.sideColor.getValue(),
                     this.lineWidth.getValue().floatValue(),
                     BlockRender.alpha(entry.getValue(), now, this.fadeTime.getValue()),
                     this.throughWalls.getValue()
                  );
                  return false;
               }
            );
      }
   }

   private static boolean better(
      boolean lockable, boolean placeable, int coverage, int satisfied, boolean bestLockable, boolean bestPlaceable, int bestCoverage, int bestSatisfied
   ) {
      if (lockable != bestLockable) {
         return lockable;
      } else if (placeable != bestPlaceable) {
         return placeable;
      } else {
         return coverage != bestCoverage ? coverage > bestCoverage : satisfied > bestSatisfied;
      }
   }

   private void submit() {
      PlacementManager.Options options = new PlacementManager.Options(true, false, true, false);

      for (BlockPos pos : this.order) {
         this.owned.add(pos);
         Homovore2Client.placements().enqueue(pos, null, Items.OBSIDIAN, options);
      }

      Homovore2Client.placements().flush();
   }

   private void releaseUnwanted() {
      Set<BlockPos> stale = new HashSet<>(this.owned);
      stale.removeAll(this.wanted);
      if (!stale.isEmpty()) {
         Homovore2Client.placements().dequeueIf(stale::contains);
         this.owned.removeAll(stale);
      }
   }

   private void releaseAll() {
      if (!this.owned.isEmpty()) {
         Set<BlockPos> released = Set.copyOf(this.owned);
         Homovore2Client.placements().dequeueIf(released::contains);
         this.owned.clear();
      }

      this.wanted.clear();
      this.order.clear();
      this.scratch.clear();
   }

   private void buildCage(Vec3d feet, LivingEntity target, Vec3d eye, boolean forceCompact) {
      this.scratch.clear();
      if (!forceCompact && !(target.getHeight() <= 1.0)) {
         uprightCells(feet, target, this.scratch);
      } else {
         compactCells(feet, target, this.scratch, forceCompact);
      }

      this.evaluateCells(eye, null);
   }

   private void buildInterceptionWall(Vec3d feet, LivingEntity target, Vec3d velocity, Vec3d eye) {
      this.scratch.clear();
      double halfWidth = target.getWidth() * 0.5;
      int minX = MathHelper.floor(feet.x - halfWidth);
      int maxX = MathHelper.floor(feet.x + halfWidth - 1.0E-6);
      int minY = MathHelper.floor(feet.y);
      int maxY = MathHelper.floor(feet.y + 0.6 - 1.0E-6);
      int minZ = MathHelper.floor(feet.z - halfWidth);
      int maxZ = MathHelper.floor(feet.z + halfWidth - 1.0E-6);
      double absX = Math.abs(velocity.x);
      double absY = Math.abs(velocity.y);
      double absZ = Math.abs(velocity.z);
      if (absY > absX && absY > absZ) {
         int y = velocity.y >= 0.0 ? minY : maxY;

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               this.scratch.add(new BlockPos(x, y, z));
            }
         }
      } else if (absX >= absZ) {
         int x = velocity.x >= 0.0 ? minX : maxX;

         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               this.scratch.add(new BlockPos(x, y, z));
            }
         }
      } else {
         int z = velocity.z >= 0.0 ? minZ : maxZ;

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               this.scratch.add(new BlockPos(x, y, z));
            }
         }
      }

      Box predictedBox = new Box(feet.x - halfWidth, feet.y, feet.z - halfWidth, feet.x + halfWidth, feet.y + 0.6, feet.z + halfWidth);
      this.evaluateCells(eye, predictedBox);
   }

   private void evaluateCells(Vec3d eye, Box requiredCollision) {
      this.satisfiedCells = 0;
      this.wanted.clear();
      this.order.clear();
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();

      for (BlockPos pos : this.scratch) {
         BlockState state = mc.world.getBlockState(pos);
         if (!state.isReplaceable()) {
            boolean blocksPath = state.getCollisionShape(mc.world, pos)
               .getBoundingBoxes()
               .stream()
               .map(box -> box.offset(pos.getX(), pos.getY(), pos.getZ()))
               .anyMatch(box -> requiredCollision == null || box.intersects(requiredCollision));
            if (blocksPath) {
               this.satisfiedCells++;
            }
         } else if (this.withinReach(eye, pos) && this.placeable(world, pos) && this.wanted.add(pos)) {
            this.order.add(pos);
         }
      }
   }

   private boolean withinReach(Vec3d eye, BlockPos pos) {
      double limit = 5.0;
      Box box = new Box(pos);
      double dx = Math.max(Math.max(box.minX - eye.x, eye.x - box.maxX), 0.0);
      double dy = Math.max(Math.max(box.minY - eye.y, eye.y - box.maxY), 0.0);
      double dz = Math.max(Math.max(box.minZ - eye.z, eye.z - box.maxZ), 0.0);
      return dx * dx + dy * dy + dz * dz <= limit * limit;
   }

   private boolean placeable(PlacementWorldSnapshot.Snapshot world, BlockPos pos) {
      if (mc.world.isOutOfHeightLimit(pos)) {
         return false;
      } else {
         return mc.player.getBoundingBox().intersects(new Box(pos)) ? false : !world.available() || !world.known(pos) || !world.entityCollision(pos);
      }
   }

   private boolean validElytraLock(Vec3d anchor, Vec3d velocity, boolean fallFlying) {
      if (this.lockedElytraFeet != null && this.elytraLockTicks > 0) {
         if (fallFlying && !(velocity.lengthSquared() < 0.01)) {
            Vec3d direction = velocity.normalize();
            Vec3d toLock = this.lockedElytraFeet.subtract(anchor);
            double forward = toLock.dotProduct(direction);
            double lateral = toLock.subtract(direction.multiply(forward)).length();
            return forward >= -0.25 && lateral <= 1.5;
         } else {
            return anchor.distanceTo(this.lockedElytraFeet) <= 2.5;
         }
      } else {
         return false;
      }
   }

   private void prioritizeElytraPlacements(Vec3d feet, Vec3d velocity) {
      if (!(velocity.lengthSquared() < 0.01)) {
         Vec3d direction = velocity.normalize();
         this.order.sort(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).subtract(feet).dotProduct(direction)));
      }
   }

   private void clearElytraLock() {
      this.lockedElytraFeet = null;
      this.elytraLockTicks = 0;
   }

   private static void compactCells(Vec3d feet, LivingEntity target, List<BlockPos> out, boolean elytraShape) {
      double halfWidth = target.getWidth() * 0.5;
      int y = MathHelper.floor(feet.y + (elytraShape ? 0.3 : target.getHeight() * 0.5));
      int minX = MathHelper.floor(feet.x - halfWidth);
      int maxX = MathHelper.floor(feet.x + halfWidth - 1.0E-6);
      int minZ = MathHelper.floor(feet.z - halfWidth);
      int maxZ = MathHelper.floor(feet.z + halfWidth - 1.0E-6);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            out.add(new BlockPos(x, y - 1, z));
            out.add(new BlockPos(x, y + 1, z));
         }
      }

      addRing(out, minX, maxX, minZ, maxZ, y);
   }

   private static void uprightCells(Vec3d feet, LivingEntity target, List<BlockPos> out) {
      double halfWidth = target.getWidth() * 0.5;
      int feetY = MathHelper.floor(feet.y);
      int minX = MathHelper.floor(feet.x - halfWidth);
      int maxX = MathHelper.floor(feet.x + halfWidth);
      int minZ = MathHelper.floor(feet.z - halfWidth);
      int maxZ = MathHelper.floor(feet.z + halfWidth);
      if (!target.isOnGround()) {
         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               out.add(new BlockPos(x, feetY - 1, z));
            }
         }
      }

      addRing(out, minX, maxX, minZ, maxZ, feetY + 1);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            out.add(new BlockPos(x, feetY + 2, z));
         }
      }
   }

   private static void addRing(List<BlockPos> out, int minX, int maxX, int minZ, int maxZ, int y) {
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (Direction direction : Type.HORIZONTAL) {
               int nx = x + direction.getOffsetX();
               int nz = z + direction.getOffsetZ();
               if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                  out.add(new BlockPos(nx, y, nz));
               }
            }
         }
      }
   }

   private Vec3d trackVelocity(LivingEntity target) {
      Vec3d current = target.getEntityPos();
      if (!target.getUuid().equals(this.trackedId)) {
         this.trackedId = target.getUuid();
         this.prevPos = current;
         this.filteredVelocity = Vec3d.ZERO;
         this.velocityChange = Vec3d.ZERO;
         return this.filteredVelocity;
      } else {
         Vec3d raw = current.subtract(this.prevPos);
         this.prevPos = current;
         if (raw.length() > 8.0) {
            this.velocityChange = this.filteredVelocity.multiply(-1.0);
            this.filteredVelocity = Vec3d.ZERO;
            return this.filteredVelocity;
         } else {
            Vec3d previous = this.filteredVelocity;
            this.filteredVelocity = previous.multiply(0.55).add(raw.multiply(0.45));
            this.velocityChange = this.filteredVelocity.subtract(previous);
            return this.filteredVelocity;
         }
      }
   }

   private AutoTrapModule.Prediction predictFeet(LivingEntity target, Vec3d anchor, Vec3d velocity) {
      double speed = velocity.length();
      if (speed < 0.01) {
         return new AutoTrapModule.Prediction(anchor, 0.0);
      }

      double baseHorizon = target.isOnGround() ? 3.0 : (target.isGliding() ? 2.25 : 2.0);
      double instability = this.velocityChange.length() / (speed + 0.05);
      double minimumHorizon = target.isGliding() ? 1.5 : 0.75;
      double horizon = MathHelper.clamp(baseHorizon / (1.0 + instability * 1.5), minimumHorizon, 3.0);
      double dx = velocity.x * horizon;
      double dz = velocity.z * horizon;
      double dy = target.isOnGround() ? 0.0 : (target.isGliding() ? velocity.y * horizon : velocity.y * horizon - 0.04 * horizon * horizon);
      Vec3d displacement = new Vec3d(dx, dy, dz);
      double maximumDistance = target.isGliding() ? 5.0 : 3.5;
      if (displacement.length() > maximumDistance) {
         displacement = displacement.normalize().multiply(maximumDistance);
      }

      return new AutoTrapModule.Prediction(this.clampToCollision(target, anchor, anchor.add(displacement)), horizon);
   }

   private Vec3d clampToCollision(LivingEntity target, Vec3d from, Vec3d to) {
      Vec3d delta = to.subtract(from);
      if (delta.lengthSquared() < 1.0E-6) {
         return from;
      }

      Box startBox = target.getBoundingBox();
      Set<BlockPos> startingCollisions = this.collisionCells(startBox);
      int steps = Math.max(1, MathHelper.ceil(delta.length() / 0.25));
      Vec3d last = from;

      for (int step = 1; step <= steps; step++) {
         Vec3d sample = from.add(delta.multiply((double)step / steps));
         Set<BlockPos> collisions = this.collisionCells(startBox.offset(sample.subtract(from)));
         if (collisions.stream().anyMatch(pos -> !startingCollisions.contains(pos))) {
            break;
         }

         last = sample;
      }

      return last;
   }

   private Set<BlockPos> collisionCells(Box box) {
      Set<BlockPos> collisions = new HashSet<>();

      for (BlockPos raw : BlockPos.iterate(
         MathHelper.floor(box.minX),
         MathHelper.floor(box.minY),
         MathHelper.floor(box.minZ),
         MathHelper.floor(box.maxX),
         MathHelper.floor(box.maxY),
         MathHelper.floor(box.maxZ)
      )) {
         BlockPos pos = raw.toImmutable();
         boolean intersects = mc.world
            .getBlockState(pos)
            .getCollisionShape(mc.world, pos)
            .getBoundingBoxes()
            .stream()
            .map(shape -> shape.offset(pos.getX(), pos.getY(), pos.getZ()))
            .anyMatch(shape -> shape.intersects(box));
         if (intersects) {
            collisions.add(pos);
         }
      }

      return collisions;
   }

   private void resetTracking() {
      this.trackedId = null;
      this.prevPos = null;
      this.filteredVelocity = Vec3d.ZERO;
      this.velocityChange = Vec3d.ZERO;
      this.clearElytraLock();
   }

   private LivingEntity findTarget() {
      double maxSq = 64.0;
      PlayerEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player && player.isAlive() && !player.isSpectator() && !Homovore2Client.friends().isFriend(player)) {
            double distanceSq = mc.player.squaredDistanceTo(player);
            if (!(distanceSq > maxSq) && !(distanceSq >= bestSq)) {
               bestSq = distanceSq;
               best = player;
            }
         }
      }

      return best;
   }

   private record Prediction(Vec3d feet, double horizon) {
   }
}
