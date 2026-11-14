package dev.leonetic.util.world;

import dev.leonetic.Homovore2Client;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2764;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_5134;
import net.minecraft.class_634;
import net.minecraft.class_638;
import net.minecraft.class_640;
import net.minecraft.class_746;
import net.minecraft.class_2338.class_2339;

public final class CrystalWorldSnapshot {
   public static final byte TARGET = 1;
   public static final byte CRYSTAL = 2;
   public static final byte BLOCKER = 4;
   public static final byte TURTLE = 8;
   private static final int SCAN_RADIUS = 6;
   private static final int ENTITY_STRIDE = 6;
   private static final int TARGET_STRIDE = 5;
   public static final int MAX_PISTONS = 16;
   private static final CrystalWorldSnapshot.Snapshot EMPTY = new CrystalWorldSnapshot.Snapshot(
      0L,
      0L,
      -1,
      0,
      0.0F,
      0.0F,
      0.0F,
      1.0F,
      0,
      false,
      0.0,
      0.0,
      0.0,
      new double[6],
      new long[0],
      new float[0],
      new float[0],
      new int[0],
      new int[0],
      new byte[0],
      new double[0],
      new float[0],
      new float[0],
      new float[0],
      new int[0],
      new long[0],
      -1,
      0
   );
   private static final AtomicBoolean ACTIVE = new AtomicBoolean();
   private static final AtomicBoolean PISTONS = new AtomicBoolean();
   private static final AtomicInteger PREFERRED_TARGET_ID = new AtomicInteger(-1);
   private static final AtomicLong EPOCH = new AtomicLong(1L);
   private static final AtomicLong VERSIONS = new AtomicLong();
   private static final AtomicReference<CrystalWorldSnapshot.Snapshot> SNAPSHOT = new AtomicReference<>(EMPTY);
   private static final AtomicReference<CrystalWorldSnapshot.PlanningLimits> PLANNING_LIMITS = new AtomicReference<>(
      new CrystalWorldSnapshot.PlanningLimits(0.0, 36.0, 6.0, 6.0)
   );
   private static long[] baseScratch = new long[128];
   private static float[] baseSelfExposureScratch = new float[128];
   private static float[] baseTargetExposureScratch = new float[128];
   private static int[] idScratch = new int[32];
   private static byte[] flagScratch = new byte[32];
   private static double[] boxScratch = new double[192];
   private static float[] targetScratch = new float[160];
   private static float[] entitySelfExposureScratch = new float[32];
   private static float[] entityTargetExposureScratch = new float[32];
   private static int[] basePistonScratch = new int[128];
   private static int[] entityPistonScratch = new int[32];
   private static final long[] pistonScratch = new long[16];

   private CrystalWorldSnapshot() {
      throw new AssertionError();
   }

   public static void setActive(boolean active) {
      ACTIVE.set(active);
      if (!active) {
         SNAPSHOT.set(EMPTY);
      }
   }

   public static void setPistonTargeting(boolean pistons) {
      PISTONS.set(pistons);
   }

   public static void setPreferredTargetId(int entityId) {
      PREFERRED_TARGET_ID.set(entityId);
   }

   public static void setPlanningLimits(double minDamage, double maxSelfDamage, double placeRange, double breakRange) {
      double safeMinDamage = Math.max(0.0, minDamage);
      double safeMaxSelfDamage = Math.max(0.0, maxSelfDamage);
      double safePlaceRange = Math.max(0.0, placeRange);
      double safeBreakRange = Math.max(0.0, breakRange);
      CrystalWorldSnapshot.PlanningLimits current = PLANNING_LIMITS.get();
      if (current.minDamage() != safeMinDamage
         || current.maxSelfDamage() != safeMaxSelfDamage
         || current.placeRange() != safePlaceRange
         || current.breakRange() != safeBreakRange) {
         PLANNING_LIMITS.set(new CrystalWorldSnapshot.PlanningLimits(safeMinDamage, safeMaxSelfDamage, safePlaceRange, safeBreakRange));
      }
   }

   public static CrystalWorldSnapshot.Snapshot snapshot() {
      return SNAPSHOT.get();
   }

   public static void clear() {
      EPOCH.incrementAndGet();
      SNAPSHOT.set(EMPTY);
   }

   public static void capture(class_310 client) {
      Objects.requireNonNull(client, "client");
      if (!client.method_18854()) {
         throw new IllegalStateException("Crystal world snapshots must be captured on the client thread");
      }

      if (ACTIVE.get()) {
         class_746 player = client.field_1724;
         class_638 level = client.field_1687;
         if (player != null && level != null) {
            CrystalWorldSnapshot.BlockCapture blocks = captureBlocks(level, player.method_24515());
            int baseCount = blocks.baseCount();
            int pistonCount = blocks.pistonCount();
            CrystalWorldSnapshot.EntityCapture entities = captureEntities(level, player);
            int entityCount = entities.count();
            CrystalWorldSnapshot.SelfState self = new CrystalWorldSnapshot.SelfState(
               player.method_6032() + player.method_6067(),
               player.method_6096(),
               (float)player.method_45325(class_5134.field_23725),
               ExplosionMath.resistanceFactor(player),
               ExplosionMath.protectionPoints(player),
               level.method_8407().method_5461()
            );
            captureExposure(level, player, entities.nearestTarget(), entities.nearestTargetIndex(), baseCount, entityCount, PLANNING_LIMITS.get(), self);
            capturePistonMasks(level, baseCount, entityCount, pistonCount);
            int latencyTicks = captureLatencyTicks(client, player);
            SNAPSHOT.set(
               new CrystalWorldSnapshot.Snapshot(
                  VERSIONS.incrementAndGet(),
                  EPOCH.get(),
                  player.method_5628(),
                  self.difficulty(),
                  self.health(),
                  self.armor(),
                  self.toughness(),
                  self.resistance(),
                  self.protection(),
                  player.method_31549().field_7480,
                  player.method_23317(),
                  player.method_23318(),
                  player.method_23321(),
                  box(player.method_5829()),
                  Arrays.copyOf(baseScratch, baseCount),
                  Arrays.copyOf(baseSelfExposureScratch, baseCount),
                  Arrays.copyOf(baseTargetExposureScratch, baseCount),
                  Arrays.copyOf(basePistonScratch, baseCount),
                  Arrays.copyOf(idScratch, entityCount),
                  Arrays.copyOf(flagScratch, entityCount),
                  Arrays.copyOf(boxScratch, entityCount * 6),
                  Arrays.copyOf(targetScratch, entityCount * 5),
                  Arrays.copyOf(entitySelfExposureScratch, entityCount),
                  Arrays.copyOf(entityTargetExposureScratch, entityCount),
                  Arrays.copyOf(entityPistonScratch, entityCount),
                  Arrays.copyOf(pistonScratch, pistonCount),
                  entities.nearestTarget() == null ? -1 : entities.nearestTarget().method_5628(),
                  latencyTicks
               )
            );
         } else {
            SNAPSHOT.set(EMPTY);
         }
      }
   }

   private static int captureLatencyTicks(class_310 client, class_746 player) {
      class_634 connection = client.method_1562();
      if (connection == null) {
         return 0;
      }

      class_640 info = connection.method_2871(player.method_5667());
      return info == null ? 0 : class_3532.method_15340(Math.round(info.method_2959() / 50.0F), 0, 10);
   }

   private static double[] box(class_238 box) {
      return new double[]{box.field_1323, box.field_1322, box.field_1321, box.field_1320, box.field_1325, box.field_1324};
   }

   private static CrystalWorldSnapshot.BlockCapture captureBlocks(class_638 level, class_2338 center) {
      int count = 0;
      int pistons = 0;
      boolean trackPistons = PISTONS.get();
      class_2339 cursor = new class_2339();
      int cx = center.method_10263();
      int cy = center.method_10264();
      int cz = center.method_10260();
      int captureRadiusSquared = 64;

      for (int dx = -6; dx <= 6; dx++) {
         int xx = dx * dx;

         for (int dy = -6; dy <= 7; dy++) {
            int xxyy = xx + dy * dy;
            if (xxyy <= captureRadiusSquared) {
               int y = cy + dy;
               if (!level.method_31601(y) && !level.method_31601(y + 1)) {
                  for (int dz = -6; dz <= 6; dz++) {
                     if (xxyy + dz * dz <= captureRadiusSquared) {
                        int x = cx + dx;
                        int z = cz + dz;
                        cursor.method_10103(x, y, z);
                        class_2680 base = level.method_8320(cursor);
                        if (trackPistons && pistons < 16 && isNormalPiston(base)) {
                           pistonScratch[pistons++] = class_2338.method_10064(x, y, z);
                        }

                        if (base.method_27852(class_2246.field_10540) || base.method_27852(class_2246.field_9987)) {
                           cursor.method_33098(y + 1);
                           if (level.method_8320(cursor).method_26215()) {
                              if (count == baseScratch.length) {
                                 baseScratch = Arrays.copyOf(baseScratch, baseScratch.length << 1);
                                 baseSelfExposureScratch = Arrays.copyOf(baseSelfExposureScratch, baseScratch.length);
                                 baseTargetExposureScratch = Arrays.copyOf(baseTargetExposureScratch, baseScratch.length);
                                 basePistonScratch = Arrays.copyOf(basePistonScratch, baseScratch.length);
                              }

                              baseScratch[count++] = class_2338.method_10064(x, y, z);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return new CrystalWorldSnapshot.BlockCapture(count, pistons);
   }

   private static boolean isNormalPiston(class_2680 state) {
      if (state.method_27852(class_2246.field_10560)) {
         return true;
      } else {
         return !state.method_27852(class_2246.field_10379) && !state.method_27852(class_2246.field_10008)
            ? false
            : !state.method_28498(class_2741.field_12492) || state.method_11654(class_2741.field_12492) == class_2764.field_12637;
      }
   }

   private static CrystalWorldSnapshot.EntityCapture captureEntities(class_638 level, class_746 localPlayer) {
      int count = 0;
      class_1657 nearestTarget = null;
      class_1657 preferredTarget = null;
      int nearestTargetIndex = -1;
      int preferredTargetIndex = -1;
      int preferredTargetId = PREFERRED_TARGET_ID.get();
      double nearestDistance = Double.POSITIVE_INFINITY;
      double px = localPlayer.method_23317();
      double py = localPlayer.method_23318();
      double pz = localPlayer.method_23321();
      double limit = 19.0;
      double limitSquared = limit * limit;

      for (class_1297 entity : level.method_18112()) {
         if (entity != localPlayer && !entity.method_31481() && !entity.method_7325()) {
            double dx = entity.method_23317() - px;
            double dy = entity.method_23318() - py;
            double dz = entity.method_23321() - pz;
            if (!(dx * dx + dy * dy + dz * dz > limitSquared)) {
               byte flags = 0;
               if (entity instanceof class_1657 target && !target.method_29504() && !Homovore2Client.friends().isFriend(target)) {
                  flags = (byte)(flags | 1);
                  if (EffectParticles.isTurtleMaster(target)) {
                     flags = (byte)(flags | 8);
                  }

                  if (target.method_5628() == preferredTargetId) {
                     preferredTarget = target;
                     preferredTargetIndex = count;
                  }

                  double distance = distanceSquaredToBox(px, py, pz, target.method_5829());
                  if (distance <= nearestDistance) {
                     nearestDistance = distance;
                     nearestTarget = target;
                     nearestTargetIndex = count;
                  }
               }

               if (entity instanceof class_1511) {
                  flags = (byte)(flags | 2);
               }

               flags = (byte)(flags | 4);
               if (flags != 0) {
                  ensureEntityCapacity(count + 1);
                  idScratch[count] = entity.method_5628();
                  flagScratch[count] = flags;
                  class_238 box = entity.method_5829();
                  int boxOffset = count * 6;
                  boxScratch[boxOffset] = box.field_1323;
                  boxScratch[boxOffset + 1] = box.field_1322;
                  boxScratch[boxOffset + 2] = box.field_1321;
                  boxScratch[boxOffset + 3] = box.field_1320;
                  boxScratch[boxOffset + 4] = box.field_1325;
                  boxScratch[boxOffset + 5] = box.field_1324;
                  int targetOffset = count * 5;
                  if ((flags & 1) != 0) {
                     class_1657 target = (class_1657)entity;
                     targetScratch[targetOffset] = target.method_6096();
                     targetScratch[targetOffset + 1] = (float)target.method_45325(class_5134.field_23725);
                     targetScratch[targetOffset + 2] = ExplosionMath.resistanceFactor(target);
                     targetScratch[targetOffset + 3] = ExplosionMath.protectionPoints(target);
                     targetScratch[targetOffset + 4] = target.field_6235;
                  } else {
                     Arrays.fill(targetScratch, targetOffset, targetOffset + 5, 0.0F);
                  }

                  count++;
               }
            }
         }
      }

      return preferredTarget != null
         ? new CrystalWorldSnapshot.EntityCapture(count, preferredTarget, preferredTargetIndex)
         : new CrystalWorldSnapshot.EntityCapture(count, nearestTarget, nearestTargetIndex);
   }

   private static void captureExposure(
      class_638 level,
      class_746 player,
      class_1657 target,
      int targetIndex,
      int baseCount,
      int entityCount,
      CrystalWorldSnapshot.PlanningLimits limits,
      CrystalWorldSnapshot.SelfState self
   ) {
      double eyeY = player.method_23320();
      boolean pistonScoring = PISTONS.get();

      for (int i = 0; i < baseCount; i++) {
         long base = baseScratch[i];
         int bx = class_2338.method_10061(base);
         int by = class_2338.method_10071(base);
         int bz = class_2338.method_10083(base);
         double x = bx + 0.5;
         double y = by + 1.0;
         double z = bz + 0.5;
         boolean inRange = distanceSquaredToBox(player.method_23317(), eyeY, player.method_23321(), bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0)
               <= limits.placeRangeSquared()
            && distanceSquaredToBox(player.method_23317(), eyeY, player.method_23321(), x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0)
               <= limits.breakRangeSquared();
         if (inRange && !permanentlyBlocked(bx, by, bz, entityCount)) {
            class_243 source = new class_243(x, y, z);
            baseSelfExposureScratch[i] = needsSelfExposure(player, x, y, z, limits.maxSelfDamage(), self)
               ? ExplosionMath.exposure(level, source, player)
               : 1.0F;
            baseTargetExposureScratch[i] = target == null
                  || !pistonScoring && !canReachMinimumDamage(targetIndex, x, y, z, limits.minDamage(), self.difficulty())
               ? 1.0F
               : ExplosionMath.exposure(level, source, target);
         } else {
            baseSelfExposureScratch[i] = 1.0F;
            baseTargetExposureScratch[i] = 1.0F;
         }
      }

      for (int i = 0; i < entityCount; i++) {
         if ((flagScratch[i] & 2) == 0) {
            entitySelfExposureScratch[i] = 1.0F;
            entityTargetExposureScratch[i] = 1.0F;
         } else {
            int offset = i * 6;
            class_243 source = new class_243(
               (boxScratch[offset] + boxScratch[offset + 3]) * 0.5, boxScratch[offset + 1], (boxScratch[offset + 2] + boxScratch[offset + 5]) * 0.5
            );
            boolean inRange = distanceSquaredToBox(
                  player.method_23317(),
                  eyeY,
                  player.method_23321(),
                  boxScratch[offset],
                  boxScratch[offset + 1],
                  boxScratch[offset + 2],
                  boxScratch[offset + 3],
                  boxScratch[offset + 4],
                  boxScratch[offset + 5]
               )
               <= limits.breakRangeSquared();
            if (!inRange) {
               entitySelfExposureScratch[i] = 1.0F;
               entityTargetExposureScratch[i] = 1.0F;
            } else {
               entitySelfExposureScratch[i] = needsSelfExposure(player, source.field_1352, source.field_1351, source.field_1350, limits.maxSelfDamage(), self)
                  ? ExplosionMath.exposure(level, source, player)
                  : 1.0F;
               entityTargetExposureScratch[i] = target == null
                     || !pistonScoring
                        && !canReachMinimumDamage(targetIndex, source.field_1352, source.field_1351, source.field_1350, limits.minDamage(), self.difficulty())
                  ? 1.0F
                  : ExplosionMath.exposure(level, source, target);
            }
         }
      }
   }

   private static boolean permanentlyBlocked(int bx, int by, int bz, int entityCount) {
      for (int i = 0; i < entityCount; i++) {
         if ((flagScratch[i] & 4) != 0 && (flagScratch[i] & 2) == 0) {
            int offset = i * 6;
            if (boxScratch[offset + 3] > bx
               && boxScratch[offset] < bx + 1.0
               && boxScratch[offset + 4] > by + 1.0
               && boxScratch[offset + 1] < by + 3.0
               && boxScratch[offset + 5] > bz
               && boxScratch[offset + 2] < bz + 1.0) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean needsSelfExposure(class_746 player, double x, double y, double z, double maxSelfDamage, CrystalWorldSnapshot.SelfState self) {
      double damage = ExplosionMath.damage(
         squaredDistance(player.method_23317(), player.method_23318(), player.method_23321(), x, y, z),
         1.0,
         self.armor(),
         self.toughness(),
         self.resistance(),
         self.protection(),
         self.difficulty()
      );
      return damage > maxSelfDamage || damage + 1.0 >= self.health();
   }

   private static boolean canReachMinimumDamage(int target, double x, double y, double z, double minDamage, int difficulty) {
      if (target < 0) {
         return false;
      }

      int offset = target * 6;
      double targetX = (boxScratch[offset] + boxScratch[offset + 3]) * 0.5;
      double targetY = boxScratch[offset + 1];
      double targetZ = (boxScratch[offset + 2] + boxScratch[offset + 5]) * 0.5;
      int stats = target * 5;
      return ExplosionMath.damage(
            squaredDistance(targetX, targetY, targetZ, x, y, z),
            1.0,
            targetScratch[stats],
            targetScratch[stats + 1],
            targetScratch[stats + 2],
            (int)targetScratch[stats + 3],
            difficulty
         )
         >= minDamage;
   }

   private static void capturePistonMasks(class_638 level, int baseCount, int entityCount, int pistonCount) {
      if (pistonCount == 0) {
         Arrays.fill(basePistonScratch, 0, baseCount, 0);
         Arrays.fill(entityPistonScratch, 0, entityCount, 0);
      } else {
         for (int i = 0; i < baseCount; i++) {
            long base = baseScratch[i];
            basePistonScratch[i] = pistonMask(
               level, new class_243(class_2338.method_10061(base) + 0.5, class_2338.method_10071(base) + 1.0, class_2338.method_10083(base) + 0.5), pistonCount
            );
         }

         for (int i = 0; i < entityCount; i++) {
            if ((flagScratch[i] & 2) == 0) {
               entityPistonScratch[i] = 0;
            } else {
               int offset = i * 6;
               entityPistonScratch[i] = pistonMask(
                  level,
                  new class_243(
                     (boxScratch[offset] + boxScratch[offset + 3]) * 0.5, boxScratch[offset + 1], (boxScratch[offset + 2] + boxScratch[offset + 5]) * 0.5
                  ),
                  pistonCount
               );
            }
         }
      }
   }

   private static int pistonMask(class_638 level, class_243 source, int pistonCount) {
      int mask = 0;

      for (int i = 0; i < pistonCount; i++) {
         if (ExplosionMath.destroysBlock(level, source, class_2338.method_10092(pistonScratch[i]))) {
            mask |= 1 << i;
         }
      }

      return mask;
   }

   private static double distanceSquaredToBox(double x, double y, double z, class_238 box) {
      double dx = x < box.field_1323 ? box.field_1323 - x : (x > box.field_1320 ? x - box.field_1320 : 0.0);
      double dy = y < box.field_1322 ? box.field_1322 - y : (y > box.field_1325 ? y - box.field_1325 : 0.0);
      double dz = z < box.field_1321 ? box.field_1321 - z : (z > box.field_1324 ? z - box.field_1324 : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private static double distanceSquaredToBox(double x, double y, double z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = x < minX ? minX - x : (x > maxX ? x - maxX : 0.0);
      double dy = y < minY ? minY - y : (y > maxY ? y - maxY : 0.0);
      double dz = z < minZ ? minZ - z : (z > maxZ ? z - maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private static double squaredDistance(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = ax - bx;
      double dy = ay - by;
      double dz = az - bz;
      return dx * dx + dy * dy + dz * dz;
   }

   private static void ensureEntityCapacity(int required) {
      if (required > idScratch.length) {
         int capacity = Math.max(required, idScratch.length << 1);
         idScratch = Arrays.copyOf(idScratch, capacity);
         flagScratch = Arrays.copyOf(flagScratch, capacity);
         boxScratch = Arrays.copyOf(boxScratch, capacity * 6);
         targetScratch = Arrays.copyOf(targetScratch, capacity * 5);
         entitySelfExposureScratch = Arrays.copyOf(entitySelfExposureScratch, capacity);
         entityTargetExposureScratch = Arrays.copyOf(entityTargetExposureScratch, capacity);
         entityPistonScratch = Arrays.copyOf(entityPistonScratch, capacity);
      }
   }

   private record BlockCapture(int baseCount, int pistonCount) {
   }

   private record EntityCapture(int count, class_1657 nearestTarget, int nearestTargetIndex) {
   }

   private record PlanningLimits(double minDamage, double maxSelfDamage, double placeRange, double breakRange) {
      private double placeRangeSquared() {
         return this.placeRange * this.placeRange;
      }

      private double breakRangeSquared() {
         return this.breakRange * this.breakRange;
      }
   }

   private record SelfState(float health, float armor, float toughness, float resistance, int protection, int difficulty) {
   }

   public static final class Snapshot {
      private final long version;
      private final long epoch;
      private final int playerId;
      private final int difficulty;
      private final float playerHealth;
      private final float playerArmor;
      private final float playerToughness;
      private final float playerResistance;
      private final int playerProtection;
      private final boolean playerInvulnerable;
      private final double playerX;
      private final double playerY;
      private final double playerZ;
      private final double[] playerBox;
      private final long[] bases;
      private final float[] baseSelfExposure;
      private final float[] baseTargetExposure;
      private final int[] basePistonMasks;
      private final int[] entityIds;
      private final byte[] entityFlags;
      private final double[] entityBoxes;
      private final float[] targetStats;
      private final float[] entitySelfExposure;
      private final float[] entityTargetExposure;
      private final int[] entityPistonMasks;
      private final long[] pistons;
      private final int exposureTargetId;
      private final int latencyTicks;

      private Snapshot(
         long version,
         long epoch,
         int playerId,
         int difficulty,
         float playerHealth,
         float playerArmor,
         float playerToughness,
         float playerResistance,
         int playerProtection,
         boolean playerInvulnerable,
         double playerX,
         double playerY,
         double playerZ,
         double[] playerBox,
         long[] bases,
         float[] baseSelfExposure,
         float[] baseTargetExposure,
         int[] basePistonMasks,
         int[] entityIds,
         byte[] entityFlags,
         double[] entityBoxes,
         float[] targetStats,
         float[] entitySelfExposure,
         float[] entityTargetExposure,
         int[] entityPistonMasks,
         long[] pistons,
         int exposureTargetId,
         int latencyTicks
      ) {
         this.version = version;
         this.epoch = epoch;
         this.playerId = playerId;
         this.difficulty = difficulty;
         this.playerHealth = playerHealth;
         this.playerArmor = playerArmor;
         this.playerToughness = playerToughness;
         this.playerResistance = playerResistance;
         this.playerProtection = playerProtection;
         this.playerInvulnerable = playerInvulnerable;
         this.playerX = playerX;
         this.playerY = playerY;
         this.playerZ = playerZ;
         this.playerBox = playerBox;
         this.bases = bases;
         this.baseSelfExposure = baseSelfExposure;
         this.baseTargetExposure = baseTargetExposure;
         this.basePistonMasks = basePistonMasks;
         this.entityIds = entityIds;
         this.entityFlags = entityFlags;
         this.entityBoxes = entityBoxes;
         this.targetStats = targetStats;
         this.entitySelfExposure = entitySelfExposure;
         this.entityTargetExposure = entityTargetExposure;
         this.entityPistonMasks = entityPistonMasks;
         this.pistons = pistons;
         this.exposureTargetId = exposureTargetId;
         this.latencyTicks = latencyTicks;
      }

      public boolean available() {
         return this.playerId >= 0;
      }

      public long version() {
         return this.version;
      }

      public long epoch() {
         return this.epoch;
      }

      public int playerId() {
         return this.playerId;
      }

      public int difficulty() {
         return this.difficulty;
      }

      public float playerHealth() {
         return this.playerHealth;
      }

      public float playerArmor() {
         return this.playerArmor;
      }

      public float playerToughness() {
         return this.playerToughness;
      }

      public float playerResistance() {
         return this.playerResistance;
      }

      public int playerProtection() {
         return this.playerProtection;
      }

      public boolean playerInvulnerable() {
         return this.playerInvulnerable;
      }

      public double playerX() {
         return this.playerX;
      }

      public double playerY() {
         return this.playerY;
      }

      public double playerZ() {
         return this.playerZ;
      }

      public boolean selfCollides(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
         return this.playerBox[3] > minX
            && this.playerBox[0] < maxX
            && this.playerBox[4] > minY
            && this.playerBox[1] < maxY
            && this.playerBox[5] > minZ
            && this.playerBox[2] < maxZ;
      }

      public int baseCount() {
         return this.bases.length;
      }

      public long base(int index) {
         return this.bases[index];
      }

      public float baseSelfExposure(int index) {
         return this.baseSelfExposure[index];
      }

      public float baseTargetExposure(int index, int target) {
         return this.entityIds[target] == this.exposureTargetId ? this.baseTargetExposure[index] : 1.0F;
      }

      public int baseIndex(long packed) {
         for (int i = 0; i < this.bases.length; i++) {
            if (this.bases[i] == packed) {
               return i;
            }
         }

         return -1;
      }

      public int pistonCount() {
         return this.pistons.length;
      }

      public long piston(int index) {
         return this.pistons[index];
      }

      public int basePistonMask(int index) {
         return this.basePistonMasks[index];
      }

      public int entityPistonMask(int index) {
         return this.entityPistonMasks[index];
      }

      public int entityCount() {
         return this.entityIds.length;
      }

      public int exposureTargetId() {
         return this.exposureTargetId;
      }

      public int entityId(int index) {
         return this.entityIds[index];
      }

      public boolean hasFlag(int index, byte flag) {
         return (this.entityFlags[index] & flag) != 0;
      }

      public double minX(int index) {
         return this.entityBoxes[index * 6];
      }

      public double minY(int index) {
         return this.entityBoxes[index * 6 + 1];
      }

      public double minZ(int index) {
         return this.entityBoxes[index * 6 + 2];
      }

      public double maxX(int index) {
         return this.entityBoxes[index * 6 + 3];
      }

      public double maxY(int index) {
         return this.entityBoxes[index * 6 + 4];
      }

      public double maxZ(int index) {
         return this.entityBoxes[index * 6 + 5];
      }

      public float targetArmor(int index) {
         return this.targetStats[index * 5];
      }

      public float targetToughness(int index) {
         return this.targetStats[index * 5 + 1];
      }

      public float targetResistance(int index) {
         return this.targetStats[index * 5 + 2];
      }

      public int targetProtection(int index) {
         return (int)this.targetStats[index * 5 + 3];
      }

      public int targetHurtTime(int index) {
         return (int)this.targetStats[index * 5 + 4];
      }

      public int latencyTicks() {
         return this.latencyTicks;
      }

      public float entitySelfExposure(int index) {
         return this.entitySelfExposure[index];
      }

      public float entityTargetExposure(int index, int target) {
         return this.entityIds[target] == this.exposureTargetId ? this.entityTargetExposure[index] : 1.0F;
      }
   }
}
