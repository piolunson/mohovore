package dev.leonetic.util.network;

import dev.leonetic.manager.PlacementManager;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.SlotManager;
import dev.leonetic.util.inventory.SwapManager;
import dev.leonetic.util.world.PlacementWorldSnapshot;
import java.util.Objects;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_2824;
import net.minecraft.class_2846;
import net.minecraft.class_2879;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_310;
import net.minecraft.class_3965;
import net.minecraft.class_2846.class_2847;

public final class NetworkActionContext {
   private final NetworkThreadExecutor.Session session;
   private int lastUseSequence = -1;

   NetworkActionContext(NetworkThreadExecutor.Session session) {
      this.session = session;
   }

   public class_2535 connection() {
      return this.session.connection();
   }

   public SlotManager slots() {
      return this.session.slotManager();
   }

   public SwapManager swaps() {
      return this.session.swapManager();
   }

   public PlacementManager.SessionState placements() {
      return this.session.placementManager();
   }

   public PacketLimiter limits() {
      return this.session.limiter();
   }

   public int nextSequence() {
      this.requireNetworkThread();
      return this.session.nextSequence();
   }

   public void schedule(NetworkAction action, long delayNanos) {
      this.requireNetworkThread();
      this.session.schedule(Objects.requireNonNull(action, "action"), delayNanos);
   }

   public boolean awaitingPositionConfirmation() {
      this.requireNetworkThread();
      return this.session.awaitingPositionConfirmation();
   }

   public int lastUseSequence() {
      this.requireNetworkThread();
      return this.lastUseSequence;
   }

   public void send(class_2596<?> packet) {
      this.requireNetworkThread();
      this.session.ensureOpen();
      this.connection().method_10743(Objects.requireNonNull(packet, "packet"));
   }

   public boolean swing(class_1268 hand) {
      Objects.requireNonNull(hand, "hand");
      if (!this.limits().canSend(PacketLimiter.Bucket.GLOBAL)) {
         return false;
      }

      this.send(new class_2879(hand));
      return true;
   }

   public boolean useItem(class_1268 hand, float yaw, float pitch) {
      return this.useItem(hand, yaw, pitch, null);
   }

   public boolean useItem(class_1268 hand, float yaw, float pitch, Object owner) {
      Objects.requireNonNull(hand, "hand");
      if (!this.limits().canSend(PacketLimiter.Bucket.USE_ITEM, 1, owner)) {
         return false;
      }

      int sequence = this.nextSequence();
      this.lastUseSequence = sequence;
      this.send(new class_2886(hand, sequence, yaw, pitch));
      return true;
   }

   public boolean useItem(class_1268 hand) {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      return this.useItem(hand, snapshot.yaw(), snapshot.pitch());
   }

   public boolean useItemOn(class_1268 hand, class_3965 hit) {
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(hit, "hit");
      if (this.correctionWouldInvalidate(hit)) {
         return false;
      }

      if (!this.limits().canSend(PacketLimiter.Bucket.USE_ITEM)) {
         return false;
      }

      this.sendUseItemOn(hand, hit);
      return true;
   }

   public boolean place(class_1268 hand, class_3965 hit, boolean swing) {
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(hit, "hit");
      if (this.correctionWouldInvalidate(hit)) {
         return false;
      }

      if (!this.limits().canSend(PacketLimiter.Bucket.USE_ITEM)) {
         return false;
      }

      if (swing && this.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
         return false;
      }

      this.sendUseItemOn(hand, hit);
      if (swing) {
         this.send(new class_2879(hand));
      }

      return true;
   }

   public boolean placeBurst(class_1268 hand, class_3965 hit, boolean swing) {
      return this.placeBurst(hand, hit, swing, false);
   }

   public boolean placeBurst(class_1268 hand, class_3965 hit, boolean swing, boolean extendedReach) {
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(hit, "hit");
      if (this.correctionWouldInvalidate(hit)) {
         return false;
      }

      if (!validPlacementHit(hit, extendedReach)) {
         return false;
      }

      if (!this.limits().canSend(PacketLimiter.Bucket.USE_ITEM)) {
         return false;
      }

      if (swing && this.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
         return false;
      }

      this.sendUseItemOn(hand, hit);
      if (swing) {
         this.send(new class_2879(hand));
      }

      return true;
   }

   public boolean attack(int entityId) {
      if (!this.limits().canSend(PacketLimiter.Bucket.GLOBAL)) {
         return false;
      }

      if (!this.limits().canAttack(entityId)) {
         return false;
      }

      class_1297 entity = this.targetEntity(entityId);
      if (entity == null) {
         return false;
      }

      this.send(class_2824.method_34206(entity, false));
      this.limits().recordInteract(entityId, false, null);
      return true;
   }

   public boolean interact(int entityId, class_1268 hand, class_243 location, boolean secondaryAction) {
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(location, "location");
      if (!this.limits().canSend(PacketLimiter.Bucket.GLOBAL)) {
         return false;
      }

      if (!this.limits().canInteract(entityId, secondaryAction, location)) {
         return false;
      }

      class_1297 entity = this.targetEntity(entityId);
      if (entity == null) {
         return false;
      }

      this.send(class_2824.method_34208(entity, secondaryAction, hand, location));
      this.limits().recordInteract(entityId, secondaryAction, location);
      return true;
   }

   public boolean playerAction(class_2847 action, class_2338 pos, class_2350 direction) {
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(direction, "direction");
      PacketLimiter.Bucket bucket = action == class_2847.field_12969 ? PacketLimiter.Bucket.OFFHAND : PacketLimiter.Bucket.GLOBAL;
      if (!this.limits().canSend(bucket)) {
         return false;
      }

      if (isDig(action) && !this.limits().canBreak(pos, direction)) {
         return false;
      }

      this.sendPlayerAction(action, pos, direction);
      return true;
   }

   public boolean breakBlock(class_2338 pos, class_2350 direction, boolean swing) {
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(direction, "direction");
      if (this.limits().remaining(PacketLimiter.Bucket.GLOBAL) < (swing ? 3 : 2)) {
         return false;
      }

      if (!this.limits().canBreak(pos, direction)) {
         return false;
      }

      this.sendPlayerAction(class_2847.field_12968, pos, direction);
      this.sendPlayerAction(class_2847.field_12973, pos, direction);
      if (swing) {
         this.send(new class_2879(class_1268.field_5808));
      }

      return true;
   }

   public boolean startInstantBreak(class_2338 pos, class_2350 direction, boolean swing) {
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(direction, "direction");
      int cost = swing ? 2 : 1;
      if (this.limits().remaining(PacketLimiter.Bucket.GLOBAL) < cost) {
         return false;
      }

      if (!this.limits().canBreak(pos, direction)) {
         return false;
      }

      this.sendPlayerAction(class_2847.field_12968, pos, direction);
      if (swing) {
         this.send(new class_2879(class_1268.field_5808));
      }

      return true;
   }

   public boolean startGrimBreak(class_2338 target, class_2350 targetDirection, class_2338 decoy, class_2350 decoyDirection, boolean swing) {
      Objects.requireNonNull(decoy, "decoy");
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(targetDirection, "targetDirection");
      Objects.requireNonNull(decoyDirection, "decoyDirection");
      if (decoy.equals(target)) {
         return false;
      }

      int cost = swing ? 3 : 2;
      if (this.limits().remaining(PacketLimiter.Bucket.GLOBAL) < cost) {
         return false;
      }

      if (!this.limits().canBreak(target, targetDirection)) {
         return false;
      }

      this.sendPlayerAction(class_2847.field_12968, target, targetDirection);
      this.sendPlayerActionUntracked(class_2847.field_12968, decoy, decoyDirection);
      if (swing) {
         this.send(new class_2879(class_1268.field_5808));
      }

      return true;
   }

   public boolean releaseUsingItem() {
      return this.playerAction(class_2847.field_12974, class_2338.field_10980, class_2350.field_11033);
   }

   public void requireNetworkThread() {
      if (!this.session.inEventLoop()) {
         throw new IllegalStateException("Game action must run on the active connection's network thread");
      }
   }

   private void sendUseItemOn(class_1268 hand, class_3965 hit) {
      int sequence = this.nextSequence();
      this.lastUseSequence = sequence;
      this.send(new class_2885(hand, hit, sequence));
   }

   private class_1297 targetEntity(int entityId) {
      class_310 client = class_310.method_1551();
      return client.field_1687 == null ? null : client.field_1687.method_8469(entityId);
   }

   private boolean correctionWouldInvalidate(class_3965 hit) {
      if (!this.session.awaitingPositionConfirmation()) {
         return false;
      }

      class_243 target = this.session.pendingTeleportPosition();
      if (target == null) {
         return true;
      }

      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      if (!world.available()) {
         return true;
      }

      class_2338 clicked = hit.method_17777();
      double eyeY = target.field_1351 + (world.eyeY() - world.feetY());
      double dx = Math.max(Math.max(clicked.method_10263() - target.field_1352, target.field_1352 - (clicked.method_10263() + 1.0)), 0.0);
      double dy = Math.max(Math.max(clicked.method_10264() - eyeY, eyeY - (clicked.method_10264() + 1.0)), 0.0);
      double dz = Math.max(Math.max(clicked.method_10260() - target.field_1350, target.field_1350 - (clicked.method_10260() + 1.0)), 0.0);
      double reach = 4.5;
      return dx * dx + dy * dy + dz * dz > reach * reach;
   }

   private static boolean validPlacementHit(class_3965 hit, boolean extendedReach) {
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      class_2338 clicked = hit.method_17777();
      class_243 location = hit.method_17784();
      if (world.available() && world.known(clicked)) {
         double offsetX = Math.abs(location.field_1352 - (clicked.method_10263() + 0.5));
         double offsetY = Math.abs(location.field_1351 - (clicked.method_10264() + 0.5));
         double offsetZ = Math.abs(location.field_1350 - (clicked.method_10260() + 0.5));
         if (Double.isFinite(location.field_1352)
            && Double.isFinite(location.field_1351)
            && Double.isFinite(location.field_1350)
            && !(offsetX > 1.0000001)
            && !(offsetY > 1.0000001)
            && !(offsetZ > 1.0000001)) {
            return extendedReach ? true : world.withinPlaceReach(clicked);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void sendPlayerAction(class_2847 action, class_2338 pos, class_2350 direction) {
      class_2338 immutable = pos.method_10062();
      this.send(new class_2846(action, immutable, direction, this.nextSequence()));
      if (isDig(action)) {
         this.limits().recordBreak(immutable, direction);
      }
   }

   private void sendPlayerActionUntracked(class_2847 action, class_2338 pos, class_2350 direction) {
      this.send(new class_2846(action, pos.method_10062(), direction, this.nextSequence()));
   }

   private static boolean isDig(class_2847 action) {
      return action == class_2847.field_12968 || action == class_2847.field_12973;
   }
}
