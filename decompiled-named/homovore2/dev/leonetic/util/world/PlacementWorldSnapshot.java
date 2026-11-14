package dev.leonetic.util.world;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

public final class PlacementWorldSnapshot {
   public static final double NORMAL_REACH = 5.154;
   public static final double PLACE_REACH = 4.5;
   public static final double PLAN_REACH = 5.0;
   private static final int RADIUS = 7;
   private static final int SIDE = 15;
   private static final int VOLUME = 3375;
   private static final byte KNOWN = 1;
   private static final byte REPLACEABLE = 2;
   private static final byte SUPPORT = 4;
   private static final byte ENTITY = 8;
   private static final byte SELF = 16;
   private static final AtomicLong VERSIONS = new AtomicLong();
   private static final AtomicLong EPOCHS = new AtomicLong(1L);
   private static final AtomicReference<PlacementWorldSnapshot.Snapshot> SNAPSHOT = new AtomicReference<>(PlacementWorldSnapshot.Snapshot.EMPTY);
   private static ClientWorld lastLevel;

   private PlacementWorldSnapshot() {
      throw new AssertionError();
   }

   public static PlacementWorldSnapshot.Snapshot snapshot() {
      return SNAPSHOT.get();
   }

   public static void clear() {
      lastLevel = null;
      EPOCHS.incrementAndGet();
      SNAPSHOT.set(PlacementWorldSnapshot.Snapshot.EMPTY);
   }

   public static void capture(MinecraftClient client) {
      Objects.requireNonNull(client, "client");
      if (!client.isOnThread()) {
         throw new IllegalStateException("Placement snapshots must be captured on the client thread");
      }

      ClientPlayerEntity player = client.player;
      ClientWorld level = client.world;
      if (player != null && level != null) {
         if (level != lastLevel) {
            lastLevel = level;
            EPOCHS.incrementAndGet();
         }

         BlockPos center = player.getBlockPos();
         int minX = center.getX() - 7;
         int minY = center.getY() - 7;
         int minZ = center.getZ() - 7;
         byte[] cells = new byte[3375];
         Mutable cursor = new Mutable();

         for (int y = 0; y < 15; y++) {
            int worldY = minY + y;
            if (!level.isOutOfHeightLimit(worldY)) {
               for (int z = 0; z < 15; z++) {
                  for (int x = 0; x < 15; x++) {
                     cursor.set(minX + x, worldY, minZ + z);
                     cells[index(x, y, z)] = blockFlags(level.getBlockState(cursor));
                  }
               }
            }
         }

         markBox(cells, minX, minY, minZ, player.getBoundingBox(), (byte)16);

         for (Entity entity : level.getEntities()) {
            if (entity != player && !entity.isRemoved() && !entity.isSpectator() && entity.intersectionChecked) {
               markBox(cells, minX, minY, minZ, entity.getBoundingBox(), (byte)8);
            }
         }

         SNAPSHOT.set(
            new PlacementWorldSnapshot.Snapshot(
               VERSIONS.incrementAndGet(),
               EPOCHS.get(),
               player.getId(),
               minX,
               minY,
               minZ,
               player.getX(),
               player.getEyeY(),
               player.getZ(),
               player.getStandingEyeHeight(),
               cells
            )
         );
      } else {
         SNAPSHOT.set(PlacementWorldSnapshot.Snapshot.EMPTY);
      }
   }

   public static void observeBlockUpdate(BlockPos pos, BlockState state) {
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(state, "state");

      PlacementWorldSnapshot.Snapshot before;
      PlacementWorldSnapshot.Snapshot after;
      do {
         before = SNAPSHOT.get();
         int index = before.index(pos);
         if (index < 0) {
            return;
         }

         byte[] cells = (byte[])before.cells.clone();
         byte occupants = (byte)(cells[index] & 24);
         cells[index] = (byte)(blockFlags(state) | occupants);
         after = before.withCells(VERSIONS.incrementAndGet(), cells);
      } while (!SNAPSHOT.compareAndSet(before, after));
   }

   private static byte blockFlags(BlockState state) {
      byte flags = 1;
      if (state.isReplaceable()) {
         flags = (byte)(flags | 2);
      }

      if (!state.isAir() && !state.isReplaceable() && state.getFluidState().isEmpty() && !isInteractable(state.getBlock())) {
         flags = (byte)(flags | 4);
      }

      return flags;
   }

   private static void markBox(byte[] cells, int minX, int minY, int minZ, Box box, byte flag) {
      int fromX = Math.max(0, (int)Math.floor(box.minX) - minX);
      int fromY = Math.max(0, (int)Math.floor(box.minY) - minY);
      int fromZ = Math.max(0, (int)Math.floor(box.minZ) - minZ);
      int toX = Math.min(14, (int)Math.ceil(box.maxX) - 1 - minX);
      int toY = Math.min(14, (int)Math.ceil(box.maxY) - 1 - minY);
      int toZ = Math.min(14, (int)Math.ceil(box.maxZ) - 1 - minZ);
      if (fromX <= toX && fromY <= toY && fromZ <= toZ) {
         for (int y = fromY; y <= toY; y++) {
            for (int z = fromZ; z <= toZ; z++) {
               for (int x = fromX; x <= toX; x++) {
                  cells[index(x, y, z)] |= flag;
               }
            }
         }
      }
   }

   private static int index(int x, int y, int z) {
      return (y * 15 + z) * 15 + x;
   }

   private static boolean isInteractable(Block block) {
      return block instanceof BlockWithEntity
         || block instanceof DoorBlock
         || block instanceof TrapdoorBlock
         || block instanceof FenceGateBlock
         || block instanceof ButtonBlock
         || block instanceof LeverBlock
         || block instanceof BedBlock
         || block instanceof NoteBlock
         || block instanceof AnvilBlock;
   }

   public static final class Snapshot {
      private static final PlacementWorldSnapshot.Snapshot EMPTY = new PlacementWorldSnapshot.Snapshot(0L, 0L, -1, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, new byte[0]);
      private final long version;
      private final long epoch;
      private final int playerId;
      private final int minX;
      private final int minY;
      private final int minZ;
      private final double eyeX;
      private final double eyeY;
      private final double eyeZ;
      private final double eyeHeight;
      private final byte[] cells;

      private Snapshot(
         long version, long epoch, int playerId, int minX, int minY, int minZ, double eyeX, double eyeY, double eyeZ, double eyeHeight, byte[] cells
      ) {
         this.version = version;
         this.epoch = epoch;
         this.playerId = playerId;
         this.minX = minX;
         this.minY = minY;
         this.minZ = minZ;
         this.eyeX = eyeX;
         this.eyeY = eyeY;
         this.eyeZ = eyeZ;
         this.eyeHeight = eyeHeight;
         this.cells = cells;
      }

      private PlacementWorldSnapshot.Snapshot withCells(long version, byte[] cells) {
         return new PlacementWorldSnapshot.Snapshot(
            version, this.epoch, this.playerId, this.minX, this.minY, this.minZ, this.eyeX, this.eyeY, this.eyeZ, this.eyeHeight, cells
         );
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

      public boolean available() {
         return this.cells.length == 3375;
      }

      public boolean known(BlockPos pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 1) != 0;
      }

      public boolean replaceable(BlockPos pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 2) != 0;
      }

      public boolean support(BlockPos pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 4) != 0;
      }

      public boolean entityCollision(BlockPos pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 8) != 0;
      }

      public boolean selfCollision(BlockPos pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 16) != 0;
      }

      public double eyeX() {
         return this.eyeX;
      }

      public double eyeY() {
         return this.eyeY;
      }

      public double eyeZ() {
         return this.eyeZ;
      }

      public double feetY() {
         return this.eyeY - this.eyeHeight;
      }

      public double distanceToBlockSqr(BlockPos pos) {
         double dx = Math.max(Math.max(pos.getX() - this.eyeX, this.eyeX - (pos.getX() + 1.0)), 0.0);
         double dy = Math.max(Math.max(pos.getY() - this.eyeY, this.eyeY - (pos.getY() + 1.0)), 0.0);
         double dz = Math.max(Math.max(pos.getZ() - this.eyeZ, this.eyeZ - (pos.getZ() + 1.0)), 0.0);
         return dx * dx + dy * dy + dz * dz;
      }

      public boolean withinPlanReach(BlockPos pos) {
         return this.distanceToBlockSqr(pos) <= 25.0;
      }

      public boolean withinPlaceReach(BlockPos pos) {
         return this.distanceToBlockSqr(pos) <= 20.25;
      }

      public double distanceToSqr(double x, double y, double z) {
         double dx = x - this.eyeX;
         double dy = y - this.eyeY;
         double dz = z - this.eyeZ;
         return dx * dx + dy * dy + dz * dz;
      }

      public double distanceToSqrFrom(Vec3d feet, double x, double y, double z) {
         double dx = x - feet.x;
         double dy = y - (feet.y + this.eyeHeight);
         double dz = z - feet.z;
         return dx * dx + dy * dy + dz * dz;
      }

      public boolean faceVisible(BlockPos pos, Direction face, double margin) {
         return switch (face) {
            case DOWN -> this.eyeY <= pos.getY() - margin;
            case UP -> this.eyeY >= pos.getY() + 1.0 + margin;
            case NORTH -> this.eyeZ <= pos.getZ() - margin;
            case SOUTH -> this.eyeZ >= pos.getZ() + 1.0 + margin;
            case WEST -> this.eyeX <= pos.getX() - margin;
            case EAST -> this.eyeX >= pos.getX() + 1.0 + margin;
            default -> throw new MatchException(null, null);
         };
      }

      public boolean eyeInside(BlockPos pos) {
         return this.eyeX > pos.getX()
            && this.eyeX < pos.getX() + 1.0
            && this.eyeY > pos.getY()
            && this.eyeY < pos.getY() + 1.0
            && this.eyeZ > pos.getZ()
            && this.eyeZ < pos.getZ() + 1.0;
      }

      private int index(BlockPos pos) {
         int x = pos.getX() - this.minX;
         int y = pos.getY() - this.minY;
         int z = pos.getZ() - this.minZ;
         return x >= 0 && x < 15 && y >= 0 && y < 15 && z >= 0 && z < 15 ? PlacementWorldSnapshot.index(x, y, z) : -1;
      }

      @Override
      public String toString() {
         return "PlacementSnapshot[version="
            + this.version
            + ", player="
            + this.playerId
            + ", origin="
            + this.minX
            + ","
            + this.minY
            + ","
            + this.minZ
            + ", cells="
            + this.cells.length
            + "]";
      }
   }
}
