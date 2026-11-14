package dev.leonetic.util.world;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1297;
import net.minecraft.class_2199;
import net.minecraft.class_2237;
import net.minecraft.class_2244;
import net.minecraft.class_2248;
import net.minecraft.class_2269;
import net.minecraft.class_2323;
import net.minecraft.class_2338;
import net.minecraft.class_2349;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2401;
import net.minecraft.class_2428;
import net.minecraft.class_243;
import net.minecraft.class_2533;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_2338.class_2339;

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
   private static class_638 lastLevel;

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

   public static void capture(class_310 client) {
      Objects.requireNonNull(client, "client");
      if (!client.method_18854()) {
         throw new IllegalStateException("Placement snapshots must be captured on the client thread");
      }

      class_746 player = client.field_1724;
      class_638 level = client.field_1687;
      if (player != null && level != null) {
         if (level != lastLevel) {
            lastLevel = level;
            EPOCHS.incrementAndGet();
         }

         class_2338 center = player.method_24515();
         int minX = center.method_10263() - 7;
         int minY = center.method_10264() - 7;
         int minZ = center.method_10260() - 7;
         byte[] cells = new byte[3375];
         class_2339 cursor = new class_2339();

         for (int y = 0; y < 15; y++) {
            int worldY = minY + y;
            if (!level.method_31601(worldY)) {
               for (int z = 0; z < 15; z++) {
                  for (int x = 0; x < 15; x++) {
                     cursor.method_10103(minX + x, worldY, minZ + z);
                     cells[index(x, y, z)] = blockFlags(level.method_8320(cursor));
                  }
               }
            }
         }

         markBox(cells, minX, minY, minZ, player.method_5829(), (byte)16);

         for (class_1297 entity : level.method_18112()) {
            if (entity != player && !entity.method_31481() && !entity.method_7325() && entity.field_23807) {
               markBox(cells, minX, minY, minZ, entity.method_5829(), (byte)8);
            }
         }

         SNAPSHOT.set(
            new PlacementWorldSnapshot.Snapshot(
               VERSIONS.incrementAndGet(),
               EPOCHS.get(),
               player.method_5628(),
               minX,
               minY,
               minZ,
               player.method_23317(),
               player.method_23320(),
               player.method_23321(),
               player.method_5751(),
               cells
            )
         );
      } else {
         SNAPSHOT.set(PlacementWorldSnapshot.Snapshot.EMPTY);
      }
   }

   public static void observeBlockUpdate(class_2338 pos, class_2680 state) {
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

   private static byte blockFlags(class_2680 state) {
      byte flags = 1;
      if (state.method_45474()) {
         flags = (byte)(flags | 2);
      }

      if (!state.method_26215() && !state.method_45474() && state.method_26227().method_15769() && !isInteractable(state.method_26204())) {
         flags = (byte)(flags | 4);
      }

      return flags;
   }

   private static void markBox(byte[] cells, int minX, int minY, int minZ, class_238 box, byte flag) {
      int fromX = Math.max(0, (int)Math.floor(box.field_1323) - minX);
      int fromY = Math.max(0, (int)Math.floor(box.field_1322) - minY);
      int fromZ = Math.max(0, (int)Math.floor(box.field_1321) - minZ);
      int toX = Math.min(14, (int)Math.ceil(box.field_1320) - 1 - minX);
      int toY = Math.min(14, (int)Math.ceil(box.field_1325) - 1 - minY);
      int toZ = Math.min(14, (int)Math.ceil(box.field_1324) - 1 - minZ);
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

   private static boolean isInteractable(class_2248 block) {
      return block instanceof class_2237
         || block instanceof class_2323
         || block instanceof class_2533
         || block instanceof class_2349
         || block instanceof class_2269
         || block instanceof class_2401
         || block instanceof class_2244
         || block instanceof class_2428
         || block instanceof class_2199;
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

      public boolean known(class_2338 pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 1) != 0;
      }

      public boolean replaceable(class_2338 pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 2) != 0;
      }

      public boolean support(class_2338 pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 4) != 0;
      }

      public boolean entityCollision(class_2338 pos) {
         int index = this.index(pos);
         return index >= 0 && (this.cells[index] & 8) != 0;
      }

      public boolean selfCollision(class_2338 pos) {
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

      public double distanceToBlockSqr(class_2338 pos) {
         double dx = Math.max(Math.max(pos.method_10263() - this.eyeX, this.eyeX - (pos.method_10263() + 1.0)), 0.0);
         double dy = Math.max(Math.max(pos.method_10264() - this.eyeY, this.eyeY - (pos.method_10264() + 1.0)), 0.0);
         double dz = Math.max(Math.max(pos.method_10260() - this.eyeZ, this.eyeZ - (pos.method_10260() + 1.0)), 0.0);
         return dx * dx + dy * dy + dz * dz;
      }

      public boolean withinPlanReach(class_2338 pos) {
         return this.distanceToBlockSqr(pos) <= 25.0;
      }

      public boolean withinPlaceReach(class_2338 pos) {
         return this.distanceToBlockSqr(pos) <= 20.25;
      }

      public double distanceToSqr(double x, double y, double z) {
         double dx = x - this.eyeX;
         double dy = y - this.eyeY;
         double dz = z - this.eyeZ;
         return dx * dx + dy * dy + dz * dz;
      }

      public double distanceToSqrFrom(class_243 feet, double x, double y, double z) {
         double dx = x - feet.field_1352;
         double dy = y - (feet.field_1351 + this.eyeHeight);
         double dz = z - feet.field_1350;
         return dx * dx + dy * dy + dz * dz;
      }

      public boolean faceVisible(class_2338 pos, class_2350 face, double margin) {
         return switch (face) {
            case field_11033 -> this.eyeY <= pos.method_10264() - margin;
            case field_11036 -> this.eyeY >= pos.method_10264() + 1.0 + margin;
            case field_11043 -> this.eyeZ <= pos.method_10260() - margin;
            case field_11035 -> this.eyeZ >= pos.method_10260() + 1.0 + margin;
            case field_11039 -> this.eyeX <= pos.method_10263() - margin;
            case field_11034 -> this.eyeX >= pos.method_10263() + 1.0 + margin;
            default -> throw new MatchException(null, null);
         };
      }

      public boolean eyeInside(class_2338 pos) {
         return this.eyeX > pos.method_10263()
            && this.eyeX < pos.method_10263() + 1.0
            && this.eyeY > pos.method_10264()
            && this.eyeY < pos.method_10264() + 1.0
            && this.eyeZ > pos.method_10260()
            && this.eyeZ < pos.method_10260() + 1.0;
      }

      private int index(class_2338 pos) {
         int x = pos.method_10263() - this.minX;
         int y = pos.method_10264() - this.minY;
         int z = pos.method_10260() - this.minZ;
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
