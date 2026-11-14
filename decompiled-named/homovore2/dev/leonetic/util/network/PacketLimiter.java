package dev.leonetic.util.network;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class PacketLimiter {
   private static final long BASE_RECEIVE_GUARD_NANOS = 15000000L;
   private static final long MIN_RECEIVE_GUARD_NANOS = 15000000L;
   private static final long MAX_RECEIVE_GUARD_NANOS = 45000000L;
   private static final long MIN_ACK_TIMEOUT_NANOS = 150000000L;
   private static final long MAX_ACK_TIMEOUT_NANOS = 2000000000L;
   private static final double START_DELAY_JITTER_MILLIS = 50.0;
   private final RollingWindowCounter global;
   private final RollingWindowCounter placeRecipe;
   private final RollingWindowCounter useItem;
   private final PacketLimiter.AckLatencyTracker ackLatency = new PacketLimiter.AckLatencyTracker();
   private final RollingWindowCounter clickSlot;
   private final RollingWindowCounter offhand;
   private final AtomicReference<PacketLimiter.Snapshot> snapshot = new AtomicReference<>(PacketLimiter.Snapshot.EMPTY);
   private final AtomicReference<Object> useItemPriority = new AtomicReference<>();
   private final AtomicLong containerClicks = new AtomicLong();
   private final AtomicLong lastFinishDigNanos = new AtomicLong();
   private final AtomicLong startDelayBalanceBits = new AtomicLong(Double.doubleToLongBits(0.0));
   private long clientTick;
   private boolean brokeThisTick;
   private BlockPos lastBreakPos;
   private Direction lastBreakFace;
   private boolean interactedThisTick;
   private int lastInteractEntity;
   private boolean lastInteractSecondary;
   private Vec3d lastInteractLocation;

   public PacketLimiter() {
      PacketLimitSettings.Values values = PacketLimitSettings.get();
      this.global = new RollingWindowCounter(values.globalWindowNanos());
      this.placeRecipe = new RollingWindowCounter(values.placeRecipeWindowNanos());
      this.useItem = new RollingWindowCounter(values.useItemWindowNanos());
      this.clickSlot = new RollingWindowCounter(values.clickSlotWindowNanos());
      this.offhand = new RollingWindowCounter(values.offhandWindowNanos());
   }

   void observeOutbound(Packet<?> packet) {
      long now = System.nanoTime();
      PacketLimitSettings.Values values = PacketLimitSettings.get();
      this.applyWindows(values);
      this.global.add(now, 1L);
      if (packet instanceof PlayerInteractBlockC2SPacket useOn) {
         this.useItem.add(now, 1L);
         this.ackLatency.sent(useOn.getSequence(), now);
      } else if (packet instanceof PlayerInteractItemC2SPacket use) {
         this.useItem.add(now, 1L);
         this.ackLatency.sent(use.getSequence(), now);
      } else if (packet instanceof ClickSlotC2SPacket) {
         this.clickSlot.add(now, 1L);
         this.containerClicks.incrementAndGet();
      } else if (packet instanceof PlayerActionC2SPacket action && action.getAction() == Action.SWAP_ITEM_WITH_OFFHAND) {
         this.offhand.add(now, 1L);
      } else if (packet instanceof PlayerActionC2SPacket action && action.getAction() == Action.STOP_DESTROY_BLOCK) {
         this.lastFinishDigNanos.set(now);
      } else if (packet instanceof PlayerActionC2SPacket action && action.getAction() == Action.START_DESTROY_BLOCK) {
         this.startDelayBalanceBits.set(Double.doubleToLongBits(Math.clamp(this.predictStartDelayBalance(now, 1), -1000.0, 1000.0)));
      } else if (packet instanceof CraftRequestC2SPacket) {
         this.placeRecipe.add(now, 1L);
      }

      this.observeTickBoundary(packet);
      this.snapshot.set(this.capture(now, values));
   }

   void observeInbound(Packet<?> packet) {
      if (packet instanceof PlayerActionResponseS2CPacket ack) {
         long now = System.nanoTime();
         this.ackLatency.acknowledged(ack.sequence(), now);
         this.snapshot.set(this.capture(now, PacketLimitSettings.get()));
      }
   }

   private void observeTickBoundary(Packet<?> packet) {
      if (packet instanceof PlayerMoveC2SPacket || packet instanceof ClientTickEndC2SPacket) {
         this.beginTick();
      }
   }

   public boolean startDelayFits(int starts, double limit) {
      return this.predictStartDelayBalance(System.nanoTime(), starts) <= limit;
   }

   private double predictStartDelayBalance(long now, int starts) {
      double balance = Double.longBitsToDouble(this.startDelayBalanceBits.get());
      long last = this.lastFinishDigNanos.get();
      double delay = last == 0L ? Double.POSITIVE_INFINITY : (now - last) / 1000000.0 - 50.0;

      for (int i = 0; i < starts; i++) {
         balance = delay >= 275.0 ? balance * 0.9 : balance + 300.0 - Math.max(delay, 0.0);
      }

      return balance;
   }

   private void beginTick() {
      this.clientTick++;
      this.brokeThisTick = false;
      this.interactedThisTick = false;
      this.lastBreakPos = null;
      this.lastBreakFace = null;
      this.lastInteractLocation = null;
   }

   public long clientTick() {
      return this.clientTick;
   }

   private void applyWindows(PacketLimitSettings.Values values) {
      this.global.configure(values.globalWindowNanos());
      this.placeRecipe.configure(values.placeRecipeWindowNanos());
      this.useItem.configure(values.useItemWindowNanos() + this.ackLatency.receiveGuardNanos());
      this.clickSlot.configure(values.stretch(values.clickSlotWindowNanos()) + this.ackLatency.receiveGuardNanos());
      this.offhand.configure(values.stretch(values.offhandWindowNanos()));
   }

   private long useItemHeadroom(long now, PacketLimitSettings.Values values, Object owner) {
      return values.useItemLimit() <= 0 ? 0L : Math.max(0L, this.useItem.headroom(now, values.useItemLimit() + 1L) - this.useItemHeldBack(owner));
   }

   private int useItemHeldBack(Object owner) {
      Object holder = this.useItemPriority.get();
      return holder != null && holder != owner ? 1 : 0;
   }

   public void holdUseItemPriority(Object owner) {
      this.useItemPriority.set(Objects.requireNonNull(owner, "owner"));
   }

   public void releaseUseItemPriority(Object owner) {
      if (owner != null) {
         this.useItemPriority.compareAndSet(owner, null);
      }
   }

   public boolean canSend(PacketLimiter.Bucket bucket, int count) {
      return this.canSend(bucket, count, null);
   }

   public boolean canSend(PacketLimiter.Bucket bucket, int count, Object owner) {
      PacketLimitSettings.Values values = PacketLimitSettings.get();
      if (!values.enabled()) {
         return true;
      }

      if (count <= 0) {
         return true;
      }

      long now = System.nanoTime();
      this.applyWindows(values);
      if (this.global.headroom(now, values.globalLimit()) < count) {
         return false;
      }

      if (bucket == PacketLimiter.Bucket.GLOBAL) {
         return true;
      }

      return switch (bucket) {
         case GLOBAL -> true;
         case USE_ITEM -> this.useItemHeadroom(now, values, owner) >= count;
         case CLICK_SLOT -> this.clickSlot.headroom(now, values.clickSlotLimit()) >= count;
         case OFFHAND -> this.offhand.headroom(now, values.offhandLimit()) >= count;
         case PLACE_RECIPE -> this.placeRecipe.headroom(now, values.placeRecipeLimit()) >= count;
      };
   }

   public boolean canSend(PacketLimiter.Bucket bucket) {
      return this.canSend(bucket, 1);
   }

   public int clickSlotHeadroomOffThread(int reserve) {
      PacketLimitSettings.Values values = PacketLimitSettings.get();
      if (!values.enabled()) {
         return Integer.MAX_VALUE;
      }

      PacketLimiter.Snapshot current = this.snapshot.get();
      long clickLimit = current.clickSlotLimit() > 0L ? current.clickSlotLimit() : values.clickSlotLimit();
      long globalLimit = current.globalLimit() > 0L ? current.globalLimit() : values.globalLimit();
      long left = Math.min(clickLimit - 1L - current.clickSlotUsed(), globalLimit - 1L - current.globalUsed());
      return (int)Math.max(0L, left - Math.max(0, reserve));
   }

   public long containerClicksObserved() {
      return this.containerClicks.get();
   }

   public void reconcileDispatchedClicks(long observedBefore, int applied) {
      int missing = (int)Math.max(0L, applied - (this.containerClicks.get() - observedBefore));
      if (missing > 0) {
         long now = System.nanoTime();
         PacketLimitSettings.Values values = PacketLimitSettings.get();
         this.applyWindows(values);
         this.global.add(now, missing);
         this.clickSlot.add(now, missing);
         this.containerClicks.addAndGet(missing);
         this.snapshot.set(this.capture(now, values));
      }
   }

   public int remaining(PacketLimiter.Bucket bucket) {
      PacketLimitSettings.Values values = PacketLimitSettings.get();
      if (!values.enabled()) {
         return Integer.MAX_VALUE;
      }

      long now = System.nanoTime();
      this.applyWindows(values);
      long globalLeft = this.global.headroom(now, values.globalLimit());
      if (bucket == PacketLimiter.Bucket.GLOBAL) {
         return (int)Math.min(2147483647L, globalLeft);
      }

      long bucketLeft = switch (bucket) {
         case GLOBAL -> globalLeft;
         case USE_ITEM -> this.useItemHeadroom(now, values, null);
         case CLICK_SLOT -> this.clickSlot.headroom(now, values.clickSlotLimit());
         case OFFHAND -> this.offhand.headroom(now, values.offhandLimit());
         case PLACE_RECIPE -> this.placeRecipe.headroom(now, values.placeRecipeLimit());
      };
      return (int)Math.min(2147483647L, Math.min(globalLeft, bucketLeft));
   }

   public long nanosUntilCanSend(PacketLimiter.Bucket bucket, int count) {
      PacketLimitSettings.Values values = PacketLimitSettings.get();
      if (values.enabled() && count > 0) {
         long now = System.nanoTime();
         this.applyWindows(values);
         long globalWait = this.global.nanosUntilHeadroom(now, values.globalLimit(), count);
         if (bucket == PacketLimiter.Bucket.GLOBAL) {
            return globalWait;
         }

         long bucketWait = switch (bucket) {
            case GLOBAL -> globalWait;
            case USE_ITEM -> this.useItem.nanosUntilHeadroom(now, values.useItemLimit() + 1L, count + this.useItemHeldBack(null));
            case CLICK_SLOT -> this.clickSlot.nanosUntilHeadroom(now, values.clickSlotLimit(), count);
            case OFFHAND -> this.offhand.nanosUntilHeadroom(now, values.offhandLimit(), count);
            case PLACE_RECIPE -> this.placeRecipe.nanosUntilHeadroom(now, values.placeRecipeLimit(), count);
         };
         return Math.max(globalWait, bucketWait);
      } else {
         return 0L;
      }
   }

   public long acknowledgementTimeoutNanos() {
      return this.ackLatency.acknowledgementTimeoutNanos();
   }

   public boolean canBreak(BlockPos pos, Direction face) {
      if (!this.gateEnabled()) {
         return true;
      } else {
         return !this.brokeThisTick ? true : pos.equals(this.lastBreakPos) && face == this.lastBreakFace;
      }
   }

   public void recordBreak(BlockPos pos, Direction face) {
      this.brokeThisTick = true;
      this.lastBreakPos = pos.toImmutable();
      this.lastBreakFace = face;
   }

   public boolean canInteract(int entityId, boolean secondaryAction, Vec3d location) {
      if (!this.gateEnabled()) {
         return true;
      } else if (!this.interactedThisTick) {
         return true;
      } else {
         return entityId == this.lastInteractEntity && secondaryAction == this.lastInteractSecondary
            ? location == null || this.lastInteractLocation == null || location.equals(this.lastInteractLocation)
            : false;
      }
   }

   public boolean canAttack(int entityId) {
      return this.canInteract(entityId, this.lastInteractSecondary, null);
   }

   public void recordInteract(int entityId, boolean secondaryAction, Vec3d location) {
      this.interactedThisTick = true;
      this.lastInteractEntity = entityId;
      this.lastInteractSecondary = secondaryAction;
      if (location != null) {
         this.lastInteractLocation = location;
      }
   }

   private boolean gateEnabled() {
      PacketLimitSettings.Values values = PacketLimitSettings.get();
      return values.enabled() && values.grimTickGate();
   }

   public PacketLimiter.Snapshot snapshot() {
      return this.snapshot.get();
   }

   private PacketLimiter.Snapshot capture(long now, PacketLimitSettings.Values values) {
      return new PacketLimiter.Snapshot(
         this.global.sum(now),
         values.globalLimit(),
         (int)this.useItem.sum(now),
         values.useItemLimit(),
         this.ackLatency.receiveGuardNanos() / 1000000L,
         this.ackLatency.jitterNanos() / 1000000L,
         this.clickSlot.sum(now),
         values.clickSlotLimit(),
         this.offhand.sum(now),
         values.offhandLimit(),
         this.placeRecipe.sum(now),
         values.placeRecipeLimit()
      );
   }

   public void reset() {
      this.containerClicks.set(0L);
      this.useItemPriority.set(null);
      this.lastFinishDigNanos.set(0L);
      this.startDelayBalanceBits.set(Double.doubleToLongBits(0.0));
      this.global.reset();
      this.placeRecipe.reset();
      this.useItem.reset();
      this.ackLatency.reset();
      this.clickSlot.reset();
      this.offhand.reset();
      this.beginTick();
      this.snapshot.set(PacketLimiter.Snapshot.EMPTY);
   }

   private static final class AckLatencyTracker {
      private static final int MAX_PENDING = 128;
      private static final int JITTER_SAMPLES = 24;
      private static final int RTT_SAMPLES = 24;
      private final ArrayDeque<PacketLimiter.AckLatencyTracker.SentSequence> pending = new ArrayDeque<>();
      private final long[] jitter = new long[24];
      private final long[] rtts = new long[24];
      private int jitterNext;
      private int jitterCount;
      private int rttNext;
      private int rttCount;
      private long previousRtt = -1L;

      void sent(int sequence, long now) {
         this.pending.addLast(new PacketLimiter.AckLatencyTracker.SentSequence(sequence, now));

         while (this.pending.size() > 128) {
            this.pending.removeFirst();
         }
      }

      void acknowledged(int sequence, long now) {
         PacketLimiter.AckLatencyTracker.SentSequence matched = null;

         while (!this.pending.isEmpty() && this.pending.peekFirst().sequence <= sequence) {
            matched = this.pending.removeFirst();
         }

         if (matched != null) {
            long rtt = now - matched.sentAt;
            if (rtt > 0L && rtt <= 4000000000L) {
               this.rtts[this.rttNext++ % this.rtts.length] = rtt;
               this.rttCount = Math.min(this.rttCount + 1, this.rtts.length);
               if (this.previousRtt > 0L) {
                  this.jitter[this.jitterNext++ % this.jitter.length] = Math.abs(rtt - this.previousRtt);
                  this.jitterCount = Math.min(this.jitterCount + 1, this.jitter.length);
               }

               this.previousRtt = rtt;
            }
         }
      }

      long jitterNanos() {
         return percentile(this.jitter, this.jitterCount, 0.9);
      }

      long receiveGuardNanos() {
         long derived = 15000000L + this.jitterNanos() / 2L;
         return Math.clamp(derived, 15000000L, 45000000L);
      }

      long acknowledgementTimeoutNanos() {
         long highRtt = percentile(this.rtts, this.rttCount, 0.9);
         long derived = highRtt + this.receiveGuardNanos() * 2L;
         return Math.clamp(Math.max(150000000L, derived), 150000000L, 2000000000L);
      }

      void reset() {
         this.pending.clear();
         Arrays.fill(this.jitter, 0L);
         Arrays.fill(this.rtts, 0L);
         this.jitterNext = 0;
         this.jitterCount = 0;
         this.rttNext = 0;
         this.rttCount = 0;
         this.previousRtt = -1L;
      }

      private static long percentile(long[] samples, int count, double quantile) {
         if (count <= 0) {
            return 0L;
         }

         long[] copy = Arrays.copyOf(samples, count);
         Arrays.sort(copy);
         int index = Math.min(count - 1, (int)Math.ceil(count * quantile) - 1);
         return copy[Math.max(0, index)];
      }

      private record SentSequence(int sequence, long sentAt) {
      }
   }

   public enum Bucket {
      GLOBAL,
      USE_ITEM,
      CLICK_SLOT,
      OFFHAND,
      PLACE_RECIPE;
   }

   public record Snapshot(
      long globalUsed,
      long globalLimit,
      int useItemUsed,
      int useItemLimit,
      long useItemGuardMillis,
      long useItemJitterMillis,
      long clickSlotUsed,
      long clickSlotLimit,
      long offhandUsed,
      long offhandLimit,
      long placeRecipeUsed,
      long placeRecipeLimit
   ) {
      public static final PacketLimiter.Snapshot EMPTY = new PacketLimiter.Snapshot(0L, 0L, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
   }
}
