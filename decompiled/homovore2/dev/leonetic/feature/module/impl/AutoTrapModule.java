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
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_4184;
import net.minecraft.class_2350.class_2353;

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
   private final Set<class_2338> wanted = new HashSet<>(16);
   private final List<class_2338> order = new ArrayList<>(16);
   private final Set<class_2338> owned = new HashSet<>(16);
   private final List<class_2338> scratch = new ArrayList<>(16);
   private final Map<class_2338, Long> renderMap = new HashMap<>();
   private UUID trackedId;
   private class_243 prevPos;
   private class_243 filteredVelocity = class_243.field_1353;
   private class_243 velocityChange = class_243.field_1353;
   private class_243 lockedElytraFeet;
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
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1755 == null && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available() && !inventory.deadOrDying() && InventoryUtil.find(class_1802.field_8281, InventoryUtil.HOTBAR_SCOPE).found()) {
            class_1309 target = this.findTarget();
            if (target == null) {
               this.releaseAll();
               this.resetTracking();
            } else {
               class_243 velocity = this.trackVelocity(target);
               class_243 anchor = target.method_73189();
               class_243 eye = inventory.eyePosition();
               AutoTrapModule.Prediction prediction = this.predictFeet(target, anchor, velocity);
               boolean useElytraLock = this.validElytraLock(anchor, velocity, target.method_6128());
               if (this.lockedElytraFeet != null && !useElytraLock) {
                  this.clearElytraLock();
               }

               boolean flightMode = target.method_6128() || useElytraLock;
               double wallThreshold = useElytraLock ? 0.8 : 0.1;
               boolean wallMode = target.method_6128() && velocity.method_1033() >= wallThreshold;
               class_243 desiredFeet = useElytraLock ? this.lockedElytraFeet : prediction.feet();
               class_243 displacement = desiredFeet.method_1020(anchor);
               class_243 predFeet = prediction.feet();
               Set<class_2338> bestWanted = new HashSet<>();
               List<class_2338> bestOrder = new ArrayList<>();
               List<class_2338> bestScratch = new ArrayList<>();
               int bestCoverage = -1;
               int bestSatisfied = -1;
               boolean bestLockable = false;
               boolean bestPlaceable = false;
               double[] backoff = useElytraLock ? LOCKED_CAGE_BACKOFF : (flightMode ? ELYTRA_CAGE_BACKOFF : CAGE_BACKOFF);

               for (double fraction : backoff) {
                  class_243 candidateFeet = anchor.method_1019(displacement.method_1021(fraction));
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
   public void onRender3D(class_4184 camera, float partialTick) {
      if (!this.render.getValue()) {
         this.renderMap.clear();
      } else {
         long now = System.currentTimeMillis();

         for (class_2338 pos : this.wanted) {
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

      for (class_2338 pos : this.order) {
         this.owned.add(pos);
         Homovore2Client.placements().enqueue(pos, null, class_1802.field_8281, options);
      }

      Homovore2Client.placements().flush();
   }

   private void releaseUnwanted() {
      Set<class_2338> stale = new HashSet<>(this.owned);
      stale.removeAll(this.wanted);
      if (!stale.isEmpty()) {
         Homovore2Client.placements().dequeueIf(stale::contains);
         this.owned.removeAll(stale);
      }
   }

   private void releaseAll() {
      if (!this.owned.isEmpty()) {
         Set<class_2338> released = Set.copyOf(this.owned);
         Homovore2Client.placements().dequeueIf(released::contains);
         this.owned.clear();
      }

      this.wanted.clear();
      this.order.clear();
      this.scratch.clear();
   }

   private void buildCage(class_243 feet, class_1309 target, class_243 eye, boolean forceCompact) {
      this.scratch.clear();
      if (!forceCompact && !(target.method_17682() <= 1.0)) {
         uprightCells(feet, target, this.scratch);
      } else {
         compactCells(feet, target, this.scratch, forceCompact);
      }

      this.evaluateCells(eye, null);
   }

   private void buildInterceptionWall(class_243 feet, class_1309 target, class_243 velocity, class_243 eye) {
      this.scratch.clear();
      double halfWidth = target.method_17681() * 0.5;
      int minX = class_3532.method_15357(feet.field_1352 - halfWidth);
      int maxX = class_3532.method_15357(feet.field_1352 + halfWidth - 1.0E-6);
      int minY = class_3532.method_15357(feet.field_1351);
      int maxY = class_3532.method_15357(feet.field_1351 + 0.6 - 1.0E-6);
      int minZ = class_3532.method_15357(feet.field_1350 - halfWidth);
      int maxZ = class_3532.method_15357(feet.field_1350 + halfWidth - 1.0E-6);
      double absX = Math.abs(velocity.field_1352);
      double absY = Math.abs(velocity.field_1351);
      double absZ = Math.abs(velocity.field_1350);
      if (absY > absX && absY > absZ) {
         int y = velocity.field_1351 >= 0.0 ? minY : maxY;

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               this.scratch.add(new class_2338(x, y, z));
            }
         }
      } else if (absX >= absZ) {
         int x = velocity.field_1352 >= 0.0 ? minX : maxX;

         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               this.scratch.add(new class_2338(x, y, z));
            }
         }
      } else {
         int z = velocity.field_1350 >= 0.0 ? minZ : maxZ;

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               this.scratch.add(new class_2338(x, y, z));
            }
         }
      }

      class_238 predictedBox = new class_238(
         feet.field_1352 - halfWidth,
         feet.field_1351,
         feet.field_1350 - halfWidth,
         feet.field_1352 + halfWidth,
         feet.field_1351 + 0.6,
         feet.field_1350 + halfWidth
      );
      this.evaluateCells(eye, predictedBox);
   }

   private void evaluateCells(class_243 eye, class_238 requiredCollision) {
      this.satisfiedCells = 0;
      this.wanted.clear();
      this.order.clear();
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();

      for (class_2338 pos : this.scratch) {
         class_2680 state = mc.field_1687.method_8320(pos);
         if (!state.method_45474()) {
            boolean blocksPath = state.method_26220(mc.field_1687, pos)
               .method_1090()
               .stream()
               .map(box -> box.method_989(pos.method_10263(), pos.method_10264(), pos.method_10260()))
               .anyMatch(box -> requiredCollision == null || box.method_994(requiredCollision));
            if (blocksPath) {
               this.satisfiedCells++;
            }
         } else if (this.withinReach(eye, pos) && this.placeable(world, pos) && this.wanted.add(pos)) {
            this.order.add(pos);
         }
      }
   }

   private boolean withinReach(class_243 eye, class_2338 pos) {
      double limit = 5.0;
      class_238 box = new class_238(pos);
      double dx = Math.max(Math.max(box.field_1323 - eye.field_1352, eye.field_1352 - box.field_1320), 0.0);
      double dy = Math.max(Math.max(box.field_1322 - eye.field_1351, eye.field_1351 - box.field_1325), 0.0);
      double dz = Math.max(Math.max(box.field_1321 - eye.field_1350, eye.field_1350 - box.field_1324), 0.0);
      return dx * dx + dy * dy + dz * dz <= limit * limit;
   }

   private boolean placeable(PlacementWorldSnapshot.Snapshot world, class_2338 pos) {
      if (mc.field_1687.method_31606(pos)) {
         return false;
      } else {
         return mc.field_1724.method_5829().method_994(new class_238(pos)) ? false : !world.available() || !world.known(pos) || !world.entityCollision(pos);
      }
   }

   private boolean validElytraLock(class_243 anchor, class_243 velocity, boolean fallFlying) {
      if (this.lockedElytraFeet != null && this.elytraLockTicks > 0) {
         if (fallFlying && !(velocity.method_1027() < 0.01)) {
            class_243 direction = velocity.method_1029();
            class_243 toLock = this.lockedElytraFeet.method_1020(anchor);
            double forward = toLock.method_1026(direction);
            double lateral = toLock.method_1020(direction.method_1021(forward)).method_1033();
            return forward >= -0.25 && lateral <= 1.5;
         } else {
            return anchor.method_1022(this.lockedElytraFeet) <= 2.5;
         }
      } else {
         return false;
      }
   }

   private void prioritizeElytraPlacements(class_243 feet, class_243 velocity) {
      if (!(velocity.method_1027() < 0.01)) {
         class_243 direction = velocity.method_1029();
         this.order.sort(Comparator.comparingDouble(pos -> class_243.method_24953(pos).method_1020(feet).method_1026(direction)));
      }
   }

   private void clearElytraLock() {
      this.lockedElytraFeet = null;
      this.elytraLockTicks = 0;
   }

   private static void compactCells(class_243 feet, class_1309 target, List<class_2338> out, boolean elytraShape) {
      double halfWidth = target.method_17681() * 0.5;
      int y = class_3532.method_15357(feet.field_1351 + (elytraShape ? 0.3 : target.method_17682() * 0.5));
      int minX = class_3532.method_15357(feet.field_1352 - halfWidth);
      int maxX = class_3532.method_15357(feet.field_1352 + halfWidth - 1.0E-6);
      int minZ = class_3532.method_15357(feet.field_1350 - halfWidth);
      int maxZ = class_3532.method_15357(feet.field_1350 + halfWidth - 1.0E-6);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            out.add(new class_2338(x, y - 1, z));
            out.add(new class_2338(x, y + 1, z));
         }
      }

      addRing(out, minX, maxX, minZ, maxZ, y);
   }

   private static void uprightCells(class_243 feet, class_1309 target, List<class_2338> out) {
      double halfWidth = target.method_17681() * 0.5;
      int feetY = class_3532.method_15357(feet.field_1351);
      int minX = class_3532.method_15357(feet.field_1352 - halfWidth);
      int maxX = class_3532.method_15357(feet.field_1352 + halfWidth);
      int minZ = class_3532.method_15357(feet.field_1350 - halfWidth);
      int maxZ = class_3532.method_15357(feet.field_1350 + halfWidth);
      if (!target.method_24828()) {
         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               out.add(new class_2338(x, feetY - 1, z));
            }
         }
      }

      addRing(out, minX, maxX, minZ, maxZ, feetY + 1);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            out.add(new class_2338(x, feetY + 2, z));
         }
      }
   }

   private static void addRing(List<class_2338> out, int minX, int maxX, int minZ, int maxZ, int y) {
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (class_2350 direction : class_2353.field_11062) {
               int nx = x + direction.method_10148();
               int nz = z + direction.method_10165();
               if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                  out.add(new class_2338(nx, y, nz));
               }
            }
         }
      }
   }

   private class_243 trackVelocity(class_1309 target) {
      class_243 current = target.method_73189();
      if (!target.method_5667().equals(this.trackedId)) {
         this.trackedId = target.method_5667();
         this.prevPos = current;
         this.filteredVelocity = class_243.field_1353;
         this.velocityChange = class_243.field_1353;
         return this.filteredVelocity;
      } else {
         class_243 raw = current.method_1020(this.prevPos);
         this.prevPos = current;
         if (raw.method_1033() > 8.0) {
            this.velocityChange = this.filteredVelocity.method_1021(-1.0);
            this.filteredVelocity = class_243.field_1353;
            return this.filteredVelocity;
         } else {
            class_243 previous = this.filteredVelocity;
            this.filteredVelocity = previous.method_1021(0.55).method_1019(raw.method_1021(0.45));
            this.velocityChange = this.filteredVelocity.method_1020(previous);
            return this.filteredVelocity;
         }
      }
   }

   private AutoTrapModule.Prediction predictFeet(class_1309 target, class_243 anchor, class_243 velocity) {
      double speed = velocity.method_1033();
      if (speed < 0.01) {
         return new AutoTrapModule.Prediction(anchor, 0.0);
      }

      double baseHorizon = target.method_24828() ? 3.0 : (target.method_6128() ? 2.25 : 2.0);
      double instability = this.velocityChange.method_1033() / (speed + 0.05);
      double minimumHorizon = target.method_6128() ? 1.5 : 0.75;
      double horizon = class_3532.method_15350(baseHorizon / (1.0 + instability * 1.5), minimumHorizon, 3.0);
      double dx = velocity.field_1352 * horizon;
      double dz = velocity.field_1350 * horizon;
      double dy = target.method_24828()
         ? 0.0
         : (target.method_6128() ? velocity.field_1351 * horizon : velocity.field_1351 * horizon - 0.04 * horizon * horizon);
      class_243 displacement = new class_243(dx, dy, dz);
      double maximumDistance = target.method_6128() ? 5.0 : 3.5;
      if (displacement.method_1033() > maximumDistance) {
         displacement = displacement.method_1029().method_1021(maximumDistance);
      }

      return new AutoTrapModule.Prediction(this.clampToCollision(target, anchor, anchor.method_1019(displacement)), horizon);
   }

   private class_243 clampToCollision(class_1309 target, class_243 from, class_243 to) {
      class_243 delta = to.method_1020(from);
      if (delta.method_1027() < 1.0E-6) {
         return from;
      }

      class_238 startBox = target.method_5829();
      Set<class_2338> startingCollisions = this.collisionCells(startBox);
      int steps = Math.max(1, class_3532.method_15384(delta.method_1033() / 0.25));
      class_243 last = from;

      for (int step = 1; step <= steps; step++) {
         class_243 sample = from.method_1019(delta.method_1021((double)step / steps));
         Set<class_2338> collisions = this.collisionCells(startBox.method_997(sample.method_1020(from)));
         if (collisions.stream().anyMatch(pos -> !startingCollisions.contains(pos))) {
            break;
         }

         last = sample;
      }

      return last;
   }

   private Set<class_2338> collisionCells(class_238 box) {
      Set<class_2338> collisions = new HashSet<>();

      for (class_2338 raw : class_2338.method_10094(
         class_3532.method_15357(box.field_1323),
         class_3532.method_15357(box.field_1322),
         class_3532.method_15357(box.field_1321),
         class_3532.method_15357(box.field_1320),
         class_3532.method_15357(box.field_1325),
         class_3532.method_15357(box.field_1324)
      )) {
         class_2338 pos = raw.method_10062();
         boolean intersects = mc.field_1687
            .method_8320(pos)
            .method_26220(mc.field_1687, pos)
            .method_1090()
            .stream()
            .map(shape -> shape.method_989(pos.method_10263(), pos.method_10264(), pos.method_10260()))
            .anyMatch(shape -> shape.method_994(box));
         if (intersects) {
            collisions.add(pos);
         }
      }

      return collisions;
   }

   private void resetTracking() {
      this.trackedId = null;
      this.prevPos = null;
      this.filteredVelocity = class_243.field_1353;
      this.velocityChange = class_243.field_1353;
      this.clearElytraLock();
   }

   private class_1309 findTarget() {
      double maxSq = 64.0;
      class_1657 best = null;
      double bestSq = Double.MAX_VALUE;

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player != mc.field_1724 && player.method_5805() && !player.method_7325() && !Homovore2Client.friends().isFriend(player)) {
            double distanceSq = mc.field_1724.method_5858(player);
            if (!(distanceSq > maxSq) && !(distanceSq >= bestSq)) {
               bestSq = distanceSq;
               best = player;
            }
         }
      }

      return best;
   }

   private record Prediction(class_243 feet, double horizon) {
   }
}
