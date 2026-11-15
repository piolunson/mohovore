package dev.piolunson.v2.util.network;

import dev.piolunson.v2.manager.PlacementManager;
import dev.piolunson.v2.util.inventory.InventorySnapshot;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.inventory.SlotManager;
import dev.piolunson.v2.util.inventory.SwapManager;
import dev.piolunson.v2.util.world.PlacementWorldSnapshot;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class NetworkActionContext {
   private final NetworkThreadExecutor.Session session;
   private int lastUseSequence = -1;

   NetworkActionContext(NetworkThreadExecutor.Session session) {
      this.session = session;
   }

   public ClientConnection connection() {
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

   public void send(Packet<?> packet) {
      this.requireNetworkThread();
      this.session.ensureOpen();
      this.connection().send(Objects.requireNonNull(packet, "packet"));
   }

   public boolean swing(Hand hand) {
      Objects.requireNonNull(hand, "hand");
      if (!this.limits().canSend(PacketLimiter.Bucket.GLOBAL)) {
         return false;
      }

      this.send(new HandSwingC2SPacket(hand));
      return true;
   }

   public boolean useItem(Hand hand, float yaw, float pitch) {
      return this.useItem(hand, yaw, pitch, null);
   }

   public boolean useItem(Hand hand, float yaw, float pitch, Object owner) {
      Objects.requireNonNull(hand, "hand");
      if (!this.limits().canSend(PacketLimiter.Bucket.USE_ITEM, 1, owner)) {
         return false;
      }

      int sequence = this.nextSequence();
      this.lastUseSequence = sequence;
      this.send(new PlayerInteractItemC2SPacket(hand, sequence, yaw, pitch));
      return true;
   }

   public boolean useItem(Hand hand) {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      return this.useItem(hand, snapshot.yaw(), snapshot.pitch());
   }

   public boolean useItemOn(Hand hand, BlockHitResult hit) {
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

   public boolean place(Hand hand, BlockHitResult hit, boolean swing) {
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
         this.send(new HandSwingC2SPacket(hand));
      }

      return true;
   }

   public boolean placeBurst(Hand hand, BlockHitResult hit, boolean swing) {
      return this.placeBurst(hand, hit, swing, false);
   }

   public boolean placeBurst(Hand hand, BlockHitResult hit, boolean swing, boolean extendedReach) {
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
         this.send(new HandSwingC2SPacket(hand));
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

      Entity entity = this.targetEntity(entityId);
      if (entity == null) {
         return false;
      }

      this.send(PlayerInteractEntityC2SPacket.attack(entity, false));
      this.limits().recordInteract(entityId, false, null);
      return true;
   }

   public boolean interact(int entityId, Hand hand, Vec3d location, boolean secondaryAction) {
      Objects.requireNonNull(hand, "hand");
      Objects.requireNonNull(location, "location");
      if (!this.limits().canSend(PacketLimiter.Bucket.GLOBAL)) {
         return false;
      }

      if (!this.limits().canInteract(entityId, secondaryAction, location)) {
         return false;
      }

      Entity entity = this.targetEntity(entityId);
      if (entity == null) {
         return false;
      }

      this.send(PlayerInteractEntityC2SPacket.interactAt(entity, secondaryAction, hand, location));
      this.limits().recordInteract(entityId, secondaryAction, location);
      return true;
   }

   public boolean playerAction(Action action, BlockPos pos, Direction direction) {
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(direction, "direction");
      PacketLimiter.Bucket bucket = action == Action.SWAP_ITEM_WITH_OFFHAND ? PacketLimiter.Bucket.OFFHAND : PacketLimiter.Bucket.GLOBAL;
      if (!this.limits().canSend(bucket)) {
         return false;
      }

      if (isDig(action) && !this.limits().canBreak(pos, direction)) {
         return false;
      }

      this.sendPlayerAction(action, pos, direction);
      return true;
   }

   public boolean breakBlock(BlockPos pos, Direction direction, boolean swing) {
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(direction, "direction");
      if (this.limits().remaining(PacketLimiter.Bucket.GLOBAL) < (swing ? 3 : 2)) {
         return false;
      }

      if (!this.limits().canBreak(pos, direction)) {
         return false;
      }

      this.sendPlayerAction(Action.START_DESTROY_BLOCK, pos, direction);
      this.sendPlayerAction(Action.STOP_DESTROY_BLOCK, pos, direction);
      if (swing) {
         this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
      }

      return true;
   }

   public boolean startInstantBreak(BlockPos pos, Direction direction, boolean swing) {
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(direction, "direction");
      int cost = swing ? 2 : 1;
      if (this.limits().remaining(PacketLimiter.Bucket.GLOBAL) < cost) {
         return false;
      }

      if (!this.limits().canBreak(pos, direction)) {
         return false;
      }

      this.sendPlayerAction(Action.START_DESTROY_BLOCK, pos, direction);
      if (swing) {
         this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
      }

      return true;
   }

   public boolean startGrimBreak(BlockPos target, Direction targetDirection, BlockPos decoy, Direction decoyDirection, boolean swing) {
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

      this.sendPlayerAction(Action.START_DESTROY_BLOCK, target, targetDirection);
      this.sendPlayerActionUntracked(Action.START_DESTROY_BLOCK, decoy, decoyDirection);
      if (swing) {
         this.send(new HandSwingC2SPacket(Hand.MAIN_HAND));
      }

      return true;
   }

   public boolean releaseUsingItem() {
      return this.playerAction(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN);
   }

   public void requireNetworkThread() {
      if (!this.session.inEventLoop()) {
         throw new IllegalStateException("Game action must run on the active connection's network thread");
      }
   }

   private void sendUseItemOn(Hand hand, BlockHitResult hit) {
      int sequence = this.nextSequence();
      this.lastUseSequence = sequence;
      this.send(new PlayerInteractBlockC2SPacket(hand, hit, sequence));
   }

   private Entity targetEntity(int entityId) {
      MinecraftClient client = MinecraftClient.getInstance();
      return client.world == null ? null : client.world.getEntityById(entityId);
   }

   private boolean correctionWouldInvalidate(BlockHitResult hit) {
      if (!this.session.awaitingPositionConfirmation()) {
         return false;
      }

      Vec3d target = this.session.pendingTeleportPosition();
      if (target == null) {
         return true;
      }

      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      if (!world.available()) {
         return true;
      }

      BlockPos clicked = hit.getBlockPos();
      double eyeY = target.y + (world.eyeY() - world.feetY());
      double dx = Math.max(Math.max(clicked.getX() - target.x, target.x - (clicked.getX() + 1.0)), 0.0);
      double dy = Math.max(Math.max(clicked.getY() - eyeY, eyeY - (clicked.getY() + 1.0)), 0.0);
      double dz = Math.max(Math.max(clicked.getZ() - target.z, target.z - (clicked.getZ() + 1.0)), 0.0);
      double reach = 4.5;
      return dx * dx + dy * dy + dz * dz > reach * reach;
   }

   private static boolean validPlacementHit(BlockHitResult hit, boolean extendedReach) {
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      BlockPos clicked = hit.getBlockPos();
      Vec3d location = hit.getPos();
      if (world.available() && world.known(clicked)) {
         double offsetX = Math.abs(location.x - (clicked.getX() + 0.5));
         double offsetY = Math.abs(location.y - (clicked.getY() + 0.5));
         double offsetZ = Math.abs(location.z - (clicked.getZ() + 0.5));
         if (Double.isFinite(location.x)
            && Double.isFinite(location.y)
            && Double.isFinite(location.z)
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

   private void sendPlayerAction(Action action, BlockPos pos, Direction direction) {
      BlockPos immutable = pos.toImmutable();
      this.send(new PlayerActionC2SPacket(action, immutable, direction, this.nextSequence()));
      if (isDig(action)) {
         this.limits().recordBreak(immutable, direction);
      }
   }

   private void sendPlayerActionUntracked(Action action, BlockPos pos, Direction direction) {
      this.send(new PlayerActionC2SPacket(action, pos.toImmutable(), direction, this.nextSequence()));
   }

   private static boolean isDig(Action action) {
      return action == Action.START_DESTROY_BLOCK || action == Action.STOP_DESTROY_BLOCK;
   }
}
