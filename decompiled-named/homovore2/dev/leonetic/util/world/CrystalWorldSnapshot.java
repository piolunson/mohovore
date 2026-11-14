package dev.leonetic.util.world;

import dev.leonetic.Homovore2Client;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.PistonType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

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

   public static void capture(MinecraftClient client) {
      Objects.requireNonNull(client, "client");
      if (!client.isOnThread()) {
         throw new IllegalStateException("Crystal world snapshots must be captured on the client thread");
      }

      if (ACTIVE.get()) {
         ClientPlayerEntity player = client.player;
         ClientWorld level = client.world;
         if (player != null && level != null) {
            CrystalWorldSnapshot.BlockCapture blocks = captureBlocks(level, player.getBlockPos());
            int baseCount = blocks.baseCount();
            int pistonCount = blocks.pistonCount();
            CrystalWorldSnapshot.EntityCapture entities = captureEntities(level, player);
            int entityCount = entities.count();
            CrystalWorldSnapshot.SelfState self = new CrystalWorldSnapshot.SelfState(
               player.getHealth() + player.getAbsorptionAmount(),
               player.getArmor(),
               (float)player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
               ExplosionMath.resistanceFactor(player),
               ExplosionMath.protectionPoints(player),
               level.getDifficulty().getId()
            );
            captureExposure(level, player, entities.nearestTarget(), entities.nearestTargetIndex(), baseCount, entityCount, PLANNING_LIMITS.get(), self);
            capturePistonMasks(level, baseCount, entityCount, pistonCount);
            int latencyTicks = captureLatencyTicks(client, player);
            SNAPSHOT.set(
               new CrystalWorldSnapshot.Snapshot(
                  VERSIONS.incrementAndGet(),
                  EPOCH.get(),
                  player.getId(),
                  self.difficulty(),
                  self.health(),
                  self.armor(),
                  self.toughness(),
                  self.resistance(),
                  self.protection(),
                  player.getAbilities().invulnerable,
                  player.getX(),
                  player.getY(),
                  player.getZ(),
                  box(player.getBoundingBox()),
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
                  entities.nearestTarget() == null ? -1 : entities.nearestTarget().getId(),
                  latencyTicks
               )
            );
         } else {
            SNAPSHOT.set(EMPTY);
         }
      }
   }

   private static int captureLatencyTicks(MinecraftClient client, ClientPlayerEntity player) {
      ClientPlayNetworkHandler connection = client.getNetworkHandler();
      if (connection == null) {
         return 0;
      }

      PlayerListEntry info = connection.getPlayerListEntry(player.getUuid());
      return info == null ? 0 : MathHelper.clamp(Math.round(info.getLatency() / 50.0F), 0, 10);
   }

   private static double[] box(Box box) {
      return new double[]{box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
   }

   private static CrystalWorldSnapshot.BlockCapture captureBlocks(ClientWorld level, BlockPos center) {
      int count = 0;
      int pistons = 0;
      boolean trackPistons = PISTONS.get();
      Mutable cursor = new Mutable();
      int cx = center.getX();
      int cy = center.getY();
      int cz = center.getZ();
      int captureRadiusSquared = 64;

      for (int dx = -6; dx <= 6; dx++) {
         int xx = dx * dx;

         for (int dy = -6; dy <= 7; dy++) {
            int xxyy = xx + dy * dy;
            if (xxyy <= captureRadiusSquared) {
               int y = cy + dy;
               if (!level.isOutOfHeightLimit(y) && !level.isOutOfHeightLimit(y + 1)) {
                  for (int dz = -6; dz <= 6; dz++) {
                     if (xxyy + dz * dz <= captureRadiusSquared) {
                        int x = cx + dx;
                        int z = cz + dz;
                        cursor.set(x, y, z);
                        BlockState base = level.getBlockState(cursor);
                        if (trackPistons && pistons < 16 && isNormalPiston(base)) {
                           pistonScratch[pistons++] = BlockPos.asLong(x, y, z);
                        }

                        if (base.isOf(Blocks.OBSIDIAN) || base.isOf(Blocks.BEDROCK)) {
                           cursor.setY(y + 1);
                           if (level.getBlockState(cursor).isAir()) {
                              if (count == baseScratch.length) {
                                 baseScratch = Arrays.copyOf(baseScratch, baseScratch.length << 1);
                                 baseSelfExposureScratch = Arrays.copyOf(baseSelfExposureScratch, baseScratch.length);
                                 baseTargetExposureScratch = Arrays.copyOf(baseTargetExposureScratch, baseScratch.length);
                                 basePistonScratch = Arrays.copyOf(basePistonScratch, baseScratch.length);
                              }

                              baseScratch[count++] = BlockPos.asLong(x, y, z);
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

   private static boolean isNormalPiston(BlockState state) {
      if (state.isOf(Blocks.PISTON)) {
         return true;
      } else {
         return !state.isOf(Blocks.PISTON_HEAD) && !state.isOf(Blocks.MOVING_PISTON)
            ? false
            : !state.contains(Properties.PISTON_TYPE) || state.get(Properties.PISTON_TYPE) == PistonType.DEFAULT;
      }
   }

   private static CrystalWorldSnapshot.EntityCapture captureEntities(ClientWorld level, ClientPlayerEntity localPlayer) {
      int count = 0;
      PlayerEntity nearestTarget = null;
      PlayerEntity preferredTarget = null;
      int nearestTargetIndex = -1;
      int preferredTargetIndex = -1;
      int preferredTargetId = PREFERRED_TARGET_ID.get();
      double nearestDistance = Double.POSITIVE_INFINITY;
      double px = localPlayer.getX();
      double py = localPlayer.getY();
      double pz = localPlayer.getZ();
      double limit = 19.0;
      double limitSquared = limit * limit;

      for (Entity entity : level.getEntities()) {
         if (entity != localPlayer && !entity.isRemoved() && !entity.isSpectator()) {
            double dx = entity.getX() - px;
            double dy = entity.getY() - py;
            double dz = entity.getZ() - pz;
            if (!(dx * dx + dy * dy + dz * dz > limitSquared)) {
               byte flags = 0;
               if (entity instanceof PlayerEntity target && !target.isDead() && !Homovore2Client.friends().isFriend(target)) {
                  flags = (byte)(flags | 1);
                  if (EffectParticles.isTurtleMaster(target)) {
                     flags = (byte)(flags | 8);
                  }

                  if (target.getId() == preferredTargetId) {
                     preferredTarget = target;
                     preferredTargetIndex = count;
                  }

                  double distance = distanceSquaredToBox(px, py, pz, target.getBoundingBox());
                  if (distance <= nearestDistance) {
                     nearestDistance = distance;
                     nearestTarget = target;
                     nearestTargetIndex = count;
                  }
               }

               if (entity instanceof EndCrystalEntity) {
                  flags = (byte)(flags | 2);
               }

               flags = (byte)(flags | 4);
               if (flags != 0) {
                  ensureEntityCapacity(count + 1);
                  idScratch[count] = entity.getId();
                  flagScratch[count] = flags;
                  Box box = entity.getBoundingBox();
                  int boxOffset = count * 6;
                  boxScratch[boxOffset] = box.minX;
                  boxScratch[boxOffset + 1] = box.minY;
                  boxScratch[boxOffset + 2] = box.minZ;
                  boxScratch[boxOffset + 3] = box.maxX;
                  boxScratch[boxOffset + 4] = box.maxY;
                  boxScratch[boxOffset + 5] = box.maxZ;
                  int targetOffset = count * 5;
                  if ((flags & 1) != 0) {
                     PlayerEntity target = (PlayerEntity)entity;
                     targetScratch[targetOffset] = target.getArmor();
                     targetScratch[targetOffset + 1] = (float)target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
                     targetScratch[targetOffset + 2] = ExplosionMath.resistanceFactor(target);
                     targetScratch[targetOffset + 3] = ExplosionMath.protectionPoints(target);
                     targetScratch[targetOffset + 4] = target.hurtTime;
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
      ClientWorld level,
      ClientPlayerEntity player,
      PlayerEntity target,
      int targetIndex,
      int baseCount,
      int entityCount,
      CrystalWorldSnapshot.PlanningLimits limits,
      CrystalWorldSnapshot.SelfState self
   ) {
      double eyeY = player.getEyeY();
      boolean pistonScoring = PISTONS.get();

      for (int i = 0; i < baseCount; i++) {
         long base = baseScratch[i];
         int bx = BlockPos.unpackLongX(base);
         int by = BlockPos.unpackLongY(base);
         int bz = BlockPos.unpackLongZ(base);
         double x = bx + 0.5;
         double y = by + 1.0;
         double z = bz + 0.5;
         boolean inRange = distanceSquaredToBox(player.getX(), eyeY, player.getZ(), bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0) <= limits.placeRangeSquared()
            && distanceSquaredToBox(player.getX(), eyeY, player.getZ(), x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0) <= limits.breakRangeSquared();
         if (inRange && !permanentlyBlocked(bx, by, bz, entityCount)) {
            Vec3d source = new Vec3d(x, y, z);
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
            Vec3d source = new Vec3d(
               (boxScratch[offset] + boxScratch[offset + 3]) * 0.5, boxScratch[offset + 1], (boxScratch[offset + 2] + boxScratch[offset + 5]) * 0.5
            );
            boolean inRange = distanceSquaredToBox(
                  player.getX(),
                  eyeY,
                  player.getZ(),
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
               entitySelfExposureScratch[i] = needsSelfExposure(player, source.x, source.y, source.z, limits.maxSelfDamage(), self)
                  ? ExplosionMath.exposure(level, source, player)
                  : 1.0F;
               entityTargetExposureScratch[i] = target == null
                     || !pistonScoring && !canReachMinimumDamage(targetIndex, source.x, source.y, source.z, limits.minDamage(), self.difficulty())
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

   private static boolean needsSelfExposure(ClientPlayerEntity player, double x, double y, double z, double maxSelfDamage, CrystalWorldSnapshot.SelfState self) {
      double damage = ExplosionMath.damage(
         squaredDistance(player.getX(), player.getY(), player.getZ(), x, y, z),
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

   private static void capturePistonMasks(ClientWorld level, int baseCount, int entityCount, int pistonCount) {
      if (pistonCount == 0) {
         Arrays.fill(basePistonScratch, 0, baseCount, 0);
         Arrays.fill(entityPistonScratch, 0, entityCount, 0);
      } else {
         for (int i = 0; i < baseCount; i++) {
            long base = baseScratch[i];
            basePistonScratch[i] = pistonMask(
               level, new Vec3d(BlockPos.unpackLongX(base) + 0.5, BlockPos.unpackLongY(base) + 1.0, BlockPos.unpackLongZ(base) + 0.5), pistonCount
            );
         }

         for (int i = 0; i < entityCount; i++) {
            if ((flagScratch[i] & 2) == 0) {
               entityPistonScratch[i] = 0;
            } else {
               int offset = i * 6;
               entityPistonScratch[i] = pistonMask(
                  level,
                  new Vec3d(
                     (boxScratch[offset] + boxScratch[offset + 3]) * 0.5, boxScratch[offset + 1], (boxScratch[offset + 2] + boxScratch[offset + 5]) * 0.5
                  ),
                  pistonCount
               );
            }
         }
      }
   }

   private static int pistonMask(ClientWorld level, Vec3d source, int pistonCount) {
      int mask = 0;

      for (int i = 0; i < pistonCount; i++) {
         if (ExplosionMath.destroysBlock(level, source, BlockPos.fromLong(pistonScratch[i]))) {
            mask |= 1 << i;
         }
      }

      return mask;
   }

   private static double distanceSquaredToBox(double x, double y, double z, Box box) {
      double dx = x < box.minX ? box.minX - x : (x > box.maxX ? x - box.maxX : 0.0);
      double dy = y < box.minY ? box.minY - y : (y > box.maxY ? y - box.maxY : 0.0);
      double dz = z < box.minZ ? box.minZ - z : (z > box.maxZ ? z - box.maxZ : 0.0);
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

   private record EntityCapture(int count, PlayerEntity nearestTarget, int nearestTargetIndex) {
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
