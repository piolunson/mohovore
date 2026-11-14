package dev.leonetic.manager;

import dev.leonetic.event.impl.TickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkActionContext;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.world.PlacementWorldSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2620;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_3965;
import net.minecraft.class_4463;
import net.minecraft.class_8042;
import net.minecraft.class_2350.class_2351;

public final class PlacementManager {
   private static final double FACE_VISIBILITY_MARGIN = 0.05;
   private static final int REFUSALS_BEFORE_BACKOFF = 2;
   private static final long BASE_BACKOFF_NANOS = 250000000L;
   private static final long MAX_BACKOFF_NANOS = 2000000000L;
   private static final long STREAK_RESET_NANOS = 5000000000L;
   private final CopyOnWriteArrayList<PlacementManager.PlacementListener> listeners = new CopyOnWriteArrayList<>();
   private final AtomicReference<PlacementManager.Status> status = new AtomicReference<>(PlacementManager.Status.EMPTY);
   private final AtomicReference<Set<class_2338>> pending = new AtomicReference<>(Set.of());

   public PlacementManager.SessionState createSessionState() {
      this.status.set(PlacementManager.Status.EMPTY);
      return new PlacementManager.SessionState(this);
   }

   public PlacementManager.Status status() {
      return this.status.get();
   }

   public void clearStatus() {
      this.status.set(PlacementManager.Status.EMPTY);
      this.pending.set(Set.of());
   }

   public Set<class_2338> pending() {
      return this.pending.get();
   }

   public boolean isPending(class_2338 pos) {
      return pos != null && this.pending.get().contains(pos);
   }

   public void addListener(PlacementManager.PlacementListener listener) {
      this.listeners.addIfAbsent(Objects.requireNonNull(listener, "listener"));
   }

   public void removeListener(PlacementManager.PlacementListener listener) {
      this.listeners.remove(listener);
   }

   public CompletableFuture<Boolean> enqueue(class_2338 pos, class_1792 item) {
      return this.enqueue(pos, null, item, PlacementManager.Options.DEFAULT);
   }

   public CompletableFuture<Boolean> enqueue(class_2338 pos, class_2350 supportSide, class_1792 item) {
      return this.enqueue(pos, supportSide, item, PlacementManager.Options.DEFAULT);
   }

   public CompletableFuture<Boolean> enqueue(class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      PlacementManager.Request request = request(pos, supportSide, item, options);
      return NetworkThreadExecutor.submit(context -> {
         boolean queued = context.placements().enqueue(request, false);
         if (queued) {
            context.placements().requestFlush(context);
         }

         return queued;
      });
   }

   public boolean enqueueNow(NetworkActionContext context, class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      Objects.requireNonNull(context, "context").requireNetworkThread();
      boolean queued = context.placements().enqueue(request(pos, supportSide, item, options), true);
      if (queued) {
         context.placements().requestFlush(context);
      }

      return queued;
   }

   public CompletableFuture<Boolean> maintain(class_2338 pos, class_1792 item) {
      return this.maintain(pos, null, item, PlacementManager.Options.DEFAULT);
   }

   public CompletableFuture<Boolean> maintain(class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      PlacementManager.Request request = request(pos, supportSide, item, options);
      return NetworkThreadExecutor.submit(context -> context.placements().maintain(context, request));
   }

   public CompletableFuture<Boolean> unmaintain(class_2338 pos) {
      class_2338 immutable = immutable(pos);
      return NetworkThreadExecutor.submit(context -> context.placements().unmaintain(immutable));
   }

   public CompletableFuture<Boolean> dequeue(class_2338 pos) {
      class_2338 immutable = immutable(pos);
      return NetworkThreadExecutor.submit(context -> context.placements().dequeue(immutable));
   }

   public CompletableFuture<Integer> dequeueIf(Predicate<class_2338> filter) {
      Objects.requireNonNull(filter, "filter");
      return NetworkThreadExecutor.submit(context -> context.placements().dequeueIf(filter));
   }

   public CompletableFuture<Void> clearQueue() {
      return NetworkThreadExecutor.execute(context -> context.placements().clearQueue());
   }

   public CompletableFuture<Boolean> forceRetry(class_2338 pos) {
      class_2338 immutable = immutable(pos);
      return NetworkThreadExecutor.submit(context -> context.placements().forceRetry(context, immutable));
   }

   public CompletableFuture<Boolean> place(class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      PlacementManager.Request request = request(pos, supportSide, item, options);
      return NetworkThreadExecutor.submit(context -> context.placements().placeNow(context, request));
   }

   public boolean placeNow(NetworkActionContext context, class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      Objects.requireNonNull(context, "context").requireNetworkThread();
      return context.placements().placeNow(context, request(pos, supportSide, item, options));
   }

   public Set<class_2338> placeAllNow(
      NetworkActionContext context, List<class_2338> positions, class_2350 supportSide, class_1792 item, PlacementManager.Options options
   ) {
      Objects.requireNonNull(context, "context").requireNetworkThread();
      return context.placements().placeAllNow(context, requests(positions, supportSide, item, options));
   }

   public boolean forcePlaceNow(NetworkActionContext context, class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      Objects.requireNonNull(context, "context").requireNetworkThread();
      return context.placements().forcePlaceNow(context, request(pos, supportSide, item, options));
   }

   public Set<class_2338> forcePlaceAllNow(
      NetworkActionContext context, List<class_2338> positions, class_2350 supportSide, class_1792 item, PlacementManager.Options options
   ) {
      Objects.requireNonNull(context, "context").requireNetworkThread();
      return context.placements().forcePlaceAllNow(context, requests(positions, supportSide, item, options));
   }

   public CompletableFuture<Boolean> place(class_2338 pos, class_1792 item) {
      return this.place(pos, null, item, PlacementManager.Options.DEFAULT);
   }

   public class_3965 prepareHit(class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      PlacementManager.Prepared prepared = PlacementManager.SessionState.prepare(request(pos, supportSide, item, options));
      return prepared == null ? null : immutableHit(prepared.hit());
   }

   public CompletableFuture<Integer> flush() {
      return NetworkThreadExecutor.submit(context -> context.placements().flush(context));
   }

   public CompletableFuture<Integer> budget() {
      return NetworkThreadExecutor.submit(context -> context.limits().remaining(PacketLimiter.Bucket.USE_ITEM));
   }

   public CompletableFuture<Integer> useItemOn(List<class_3965> hits, class_1792 item, boolean swing) {
      Objects.requireNonNull(hits, "hits");
      Objects.requireNonNull(item, "item");
      List<class_3965> immutableHits = hits.stream().map(PlacementManager::immutableHit).toList();
      return immutableHits.isEmpty()
         ? CompletableFuture.completedFuture(0)
         : NetworkThreadExecutor.submit(context -> context.placements().useItemOn(context, immutableHits, item, swing));
   }

   public CompletableFuture<Integer> useItemOn(List<class_3965> hits, Result item, boolean swing) {
      Objects.requireNonNull(hits, "hits");
      Objects.requireNonNull(item, "item");
      List<class_3965> immutableHits = hits.stream().map(PlacementManager::immutableHit).toList();
      if (!immutableHits.isEmpty() && item.found()) {
         AtomicInteger sent = new AtomicInteger();
         return InventoryUtil.withSwap(item, context -> sent.set(useItemOnNow(context, immutableHits, item, swing)))
            .thenApply(acquired -> Boolean.TRUE.equals(acquired) ? sent.get() : 0);
      } else {
         return CompletableFuture.completedFuture(0);
      }
   }

   public boolean useItemOnNow(NetworkActionContext context, class_3965 hit, class_1792 item, boolean swing) {
      Objects.requireNonNull(context, "context").requireNetworkThread();
      Objects.requireNonNull(hit, "hit");
      Objects.requireNonNull(item, "item");
      return context.placements().useItemOn(context, List.of(immutableHit(hit)), item, swing) == 1;
   }

   @Subscribe
   public void onTick(TickEvent.Post event) {
      if (NetworkThreadExecutor.isConnected()) {
         this.flush();
      }
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (!NetworkThreadExecutor.isNetworkThread()) {
         throw new IllegalStateException("Packet events must be delivered on the network thread");
      }

      NetworkThreadExecutor.executeNow(context -> this.inspectPacket(context, event.getPacket()));
   }

   private void inspectPacket(NetworkActionContext context, class_2596<?> packet) {
      if (packet instanceof class_2626 update) {
         this.onBlockUpdate(context, update.method_11309(), update.method_11308());
      } else if (packet instanceof class_2637 section) {
         section.method_30621((pos, state) -> this.onBlockUpdate(context, pos, state));
      } else if (packet instanceof class_4463 ack) {
         context.placements().observeActionAck(context, ack.comp_633());
      } else if (packet instanceof class_2620 breaking) {
         class_2338 pos = breaking.method_11277().method_10062();
         context.placements().observeBreakProgress(pos, breaking.method_11278());

         for (PlacementManager.PlacementListener listener : this.listeners) {
            listener.onBreakProgress(pos, breaking.method_11280(), breaking.method_11278());
         }
      } else if (packet instanceof class_8042 bundle) {
         for (class_2596<?> nested : bundle.method_48324()) {
            this.inspectPacket(context, nested);
         }
      }
   }

   private void onBlockUpdate(NetworkActionContext context, class_2338 pos, class_2680 state) {
      class_2338 immutable = pos.method_10062();
      PlacementWorldSnapshot.Snapshot before = PlacementWorldSnapshot.snapshot();
      boolean wasReplaceable = before.known(immutable) && before.replaceable(immutable);
      PlacementWorldSnapshot.observeBlockUpdate(immutable, state);

      for (PlacementManager.PlacementListener listener : this.listeners) {
         listener.onBlockUpdate(immutable, state, wasReplaceable);
      }

      context.placements().observeBlockUpdate(context, immutable, state.method_45474());
   }

   private static PlacementManager.Request request(class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      return new PlacementManager.Request(immutable(pos), supportSide, Objects.requireNonNull(item, "item"), Objects.requireNonNull(options, "options"));
   }

   private static List<PlacementManager.Request> requests(List<class_2338> positions, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
      return positions.stream().map(pos -> request(pos, supportSide, item, options)).toList();
   }

   private static class_2338 immutable(class_2338 pos) {
      return Objects.requireNonNull(pos, "pos").method_10062();
   }

   private static class_3965 immutableHit(class_3965 hit) {
      Objects.requireNonNull(hit, "hit");
      class_3965 copy = new class_3965(hit.method_17784(), hit.method_17780(), hit.method_17777().method_10062(), hit.method_17781());
      return hit.method_62877() ? copy.method_62876() : copy;
   }

   private static int useItemOnNow(NetworkActionContext context, List<class_3965> hits, Result item, boolean swing) {
      context.requireNetworkThread();
      int budget = context.limits().remaining(PacketLimiter.Bucket.USE_ITEM);
      int sent = 0;

      for (class_3965 hit : hits) {
         if (sent >= budget || !context.placeBurst(item.hand(), hit, swing)) {
            break;
         }

         sent++;
      }

      return sent;
   }

   private void publish(PlacementManager.SessionState state) {
      Set<class_2338> owned = new HashSet<>((state.queue.size() + state.inFlight.size()) * 2);
      owned.addAll(state.queue.keySet());
      owned.addAll(state.inFlight.keySet());
      this.pending.set(Collections.unmodifiableSet(owned));
      this.status
         .set(
            new PlacementManager.Status(
               state.queue.size(),
               state.inFlight.size(),
               state.maintained.size(),
               state.threatened.size(),
               state.lastDeferral,
               state.budgetDeferrals,
               state.worldDeferrals,
               state.itemDeferrals,
               state.swapDeferrals,
               state.positionDeferrals,
               state.ackTimeouts,
               state.refusals
            )
         );
   }

   private record Backoff(int streak, long until) {
   }

   public enum DeferralReason {
      NONE,
      BUDGET,
      WORLD,
      ITEM,
      SWAP,
      POSITION_CONFIRMATION;
   }

   public record Options(boolean allowAirPlace, boolean allowEntityCollision, boolean extendedReach, boolean swing) {
      public static final PlacementManager.Options DEFAULT = new PlacementManager.Options(true, false, false, false);
      public static final PlacementManager.Options IN_ENTITY = new PlacementManager.Options(true, true, false, false);
      public static final PlacementManager.Options EXTENDED_REACH = new PlacementManager.Options(true, false, true, false);
   }

   private record Pending(PlacementManager.Request request, int sequence, long sentAt, long expiresAt, boolean acknowledged, boolean sawAir) {
      PlacementManager.Pending acknowledge() {
         return new PlacementManager.Pending(this.request, this.sequence, this.sentAt, this.expiresAt, true, this.sawAir);
      }

      PlacementManager.Pending seeAir() {
         return new PlacementManager.Pending(this.request, this.sequence, this.sentAt, this.expiresAt, this.acknowledged, true);
      }

      boolean refused() {
         return this.acknowledged && this.sawAir;
      }
   }

   @FunctionalInterface
   public interface PlacementListener {
      void onBlockUpdate(class_2338 var1, class_2680 var2, boolean var3);

      default void onBreakProgress(class_2338 pos, int breakerId, int progress) {
      }
   }

   private record Prepared(PlacementManager.Request request, class_3965 hit) {
   }

   private record Request(class_2338 pos, class_2350 supportSide, class_1792 item, PlacementManager.Options options) {
   }

   private record Sent(PlacementManager.Prepared prepared, int sequence) {
   }

   public static final class SessionState {
      private final PlacementManager owner;
      private final ArrayDeque<class_2338> order = new ArrayDeque<>();
      private final Map<class_2338, PlacementManager.Request> queue = new HashMap<>();
      private final Map<class_2338, PlacementManager.Pending> inFlight = new HashMap<>();
      private final Map<class_2338, PlacementManager.Request> maintained = new HashMap<>();
      private final Set<class_2338> threatened = new HashSet<>();
      private final Map<class_2338, PlacementManager.Backoff> backoff = new HashMap<>();
      private long backoffWakeAt;
      private long worldEpoch;
      private long budgetWakeAt;
      private long expiryWakeAt;
      private boolean flushScheduled;
      private PlacementManager.DeferralReason lastDeferral = PlacementManager.DeferralReason.NONE;
      private long budgetDeferrals;
      private long worldDeferrals;
      private long itemDeferrals;
      private long swapDeferrals;
      private long positionDeferrals;
      private long ackTimeouts;
      private long refusals;

      private SessionState(PlacementManager owner) {
         this.owner = owner;
      }

      private boolean enqueue(PlacementManager.Request request, boolean priority) {
         this.adoptWorld();
         if (this.inFlight.containsKey(request.pos)) {
            return false;
         }

         PlacementManager.Request previous = this.queue.putIfAbsent(request.pos, request);
         if (previous != null) {
            return false;
         }

         if (priority) {
            this.order.addFirst(request.pos);
         } else {
            this.order.addLast(request.pos);
         }

         this.owner.publish(this);
         return true;
      }

      private boolean maintain(NetworkActionContext context, PlacementManager.Request request) {
         this.adoptWorld();
         PlacementManager.Request previous = this.maintained.put(request.pos, request);
         if (previous != null && !previous.equals(request) && this.queue.containsKey(request.pos)) {
            this.dequeue(request.pos);
         }

         PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
         if (world.replaceable(request.pos) && !this.inFlight.containsKey(request.pos)) {
            this.enqueue(request, true);
            this.requestFlush(context);
         }

         this.owner.publish(this);
         return !request.equals(previous);
      }

      private boolean unmaintain(class_2338 pos) {
         boolean removed = this.maintained.remove(pos) != null;
         removed |= this.dequeue(pos);
         removed |= this.inFlight.remove(pos) != null;
         this.threatened.remove(pos);
         this.backoff.remove(pos);
         this.owner.publish(this);
         return removed;
      }

      private boolean dequeue(class_2338 pos) {
         PlacementManager.Request removed = this.queue.remove(pos);
         if (removed != null) {
            this.order.remove(pos);
         }

         this.owner.publish(this);
         return removed != null;
      }

      private int dequeueIf(Predicate<class_2338> filter) {
         int removed = 0;
         Iterator<class_2338> iterator = this.order.iterator();

         while (iterator.hasNext()) {
            class_2338 pos = iterator.next();
            if (filter.test(pos)) {
               iterator.remove();
               if (this.queue.remove(pos) != null) {
                  removed++;
               }
            }
         }

         this.owner.publish(this);
         return removed;
      }

      private void clearQueue() {
         this.order.clear();
         this.queue.clear();
         this.owner.publish(this);
      }

      private boolean forceRetry(NetworkActionContext context, class_2338 pos) {
         this.adoptWorld();
         PlacementManager.Pending pending = this.inFlight.remove(pos);
         PlacementManager.Request request = this.maintained.get(pos);
         if (request == null && pending != null) {
            request = pending.request;
         }

         if (request == null) {
            this.owner.publish(this);
            return false;
         } else {
            this.enqueue(request, true);
            this.flush(context);
            return this.inFlight.containsKey(pos);
         }
      }

      private boolean placeNow(NetworkActionContext context, PlacementManager.Request request) {
         return !this.placeAllNow(context, List.of(request)).isEmpty();
      }

      private Set<class_2338> placeAllNow(NetworkActionContext context, List<PlacementManager.Request> requests) {
         context.requireNetworkThread();
         this.adoptWorld();
         this.expirePending();
         Map<class_1792, List<PlacementManager.Request>> byItem = new LinkedHashMap<>();

         for (PlacementManager.Request request : requests) {
            if (this.inFlight.containsKey(request.pos)) {
               this.defer(PlacementManager.DeferralReason.WORLD);
            } else if (this.backingOff(request.pos)) {
               this.enqueue(request, true);
               this.scheduleBackoffWake(context);
               this.defer(PlacementManager.DeferralReason.WORLD);
            } else if (prepare(request) == null) {
               this.defer(PlacementManager.DeferralReason.WORLD);
            } else {
               byItem.computeIfAbsent(request.item, ignored -> new ArrayList<>()).add(request);
            }
         }

         Set<class_2338> placed = new LinkedHashSet<>();

         for (Entry<class_1792, List<PlacementManager.Request>> group : byItem.entrySet()) {
            List<PlacementManager.Request> batch = group.getValue();
            PlacementManager.Prepared first = prepare(batch.getFirst());
            if (first != null && admissible(context, first)) {
               Result item = InventoryUtil.find(group.getKey(), InventoryUtil.HOTBAR_SCOPE);
               if (!item.found()) {
                  this.defer(PlacementManager.DeferralReason.ITEM);
               } else {
                  List<PlacementManager.Sent> sent = new ArrayList<>(batch.size());
                  boolean acquired = InventoryUtil.withSwapNow(context, item, action -> {
                     for (PlacementManager.Request requestx : batch) {
                        PlacementManager.Prepared current = prepare(requestx);
                        if (current != null) {
                           if (!admissible(action, current)) {
                              break;
                           }

                           if (action.placeBurst(item.hand(), current.hit, requestx.options.swing, requestx.options.extendedReach)) {
                              sent.add(new PlacementManager.Sent(current, action.lastUseSequence()));
                           }
                        }
                     }
                  });
                  if (acquired && !sent.isEmpty()) {
                     long now = System.nanoTime();
                     long expiresAt = now + context.limits().acknowledgementTimeoutNanos();

                     for (PlacementManager.Sent entry : sent) {
                        PlacementManager.Request request = entry.prepared.request;
                        this.inFlight.put(request.pos, new PlacementManager.Pending(request, entry.sequence, now, expiresAt, false, false));
                        this.dequeue(request.pos);
                        placed.add(request.pos);
                     }

                     this.lastDeferral = PlacementManager.DeferralReason.NONE;
                  } else {
                     this.defer(
                        context.awaitingPositionConfirmation() ? PlacementManager.DeferralReason.POSITION_CONFIRMATION : PlacementManager.DeferralReason.SWAP
                     );
                  }
               }
            } else {
               this.defer(PlacementManager.DeferralReason.BUDGET);
               this.scheduleBudgetWake(context);
            }
         }

         if (!placed.isEmpty()) {
            this.scheduleExpiryWake(context);
         }

         this.owner.publish(this);
         return placed;
      }

      private boolean forcePlaceNow(NetworkActionContext context, PlacementManager.Request request) {
         return !this.forcePlaceAllNow(context, List.of(request)).isEmpty();
      }

      private Set<class_2338> forcePlaceAllNow(NetworkActionContext context, List<PlacementManager.Request> requests) {
         context.requireNetworkThread();
         this.adoptWorld();
         Map<class_2338, PlacementManager.Pending> previous = new HashMap<>();

         for (PlacementManager.Request request : requests) {
            PlacementManager.Pending pending = this.inFlight.remove(request.pos);
            if (pending != null) {
               previous.put(request.pos, pending);
            }

            this.dequeue(request.pos);
         }

         Set<class_2338> placed = this.placeAllNow(context, requests);

         for (PlacementManager.Request request : requests) {
            if (!placed.contains(request.pos)) {
               PlacementManager.Pending pending = previous.get(request.pos);
               if (pending != null) {
                  this.inFlight.put(request.pos, pending);
               } else {
                  PlacementManager.Request maintainedRequest = this.maintained.get(request.pos);
                  if (maintainedRequest != null) {
                     this.enqueue(maintainedRequest, true);
                  }
               }
            }
         }

         this.owner.publish(this);
         return placed;
      }

      private int useItemOn(NetworkActionContext context, List<class_3965> hits, class_1792 target, boolean swing) {
         context.requireNetworkThread();
         Result item = InventoryUtil.find(target, InventoryUtil.HOTBAR_SCOPE);
         if (!item.found()) {
            return 0;
         }

         int[] sent = new int[]{0};
         boolean acquired = InventoryUtil.withSwapNow(context, item, action -> sent[0] = PlacementManager.useItemOnNow(action, hits, item, swing));
         return acquired ? sent[0] : 0;
      }

      private int flush(NetworkActionContext context) {
         context.requireNetworkThread();
         this.adoptWorld();
         this.expirePending();
         this.scheduleExpiryWake(context);
         if (this.queue.isEmpty()) {
            this.owner.publish(this);
            return 0;
         }

         if (context.limits().remaining(PacketLimiter.Bucket.USE_ITEM) <= 0) {
            this.defer(PlacementManager.DeferralReason.BUDGET);
            this.scheduleBudgetWake(context);
            this.owner.publish(this);
            return 0;
         }

         List<PlacementManager.Prepared> ready = new ArrayList<>();
         PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
         class_1792 burstItem = null;
         int budget = context.limits().remaining(PacketLimiter.Bucket.USE_ITEM);
         Iterator<class_2338> iterator = this.order.iterator();

         while (iterator.hasNext() && ready.size() < budget) {
            class_2338 pos = iterator.next();
            PlacementManager.Request request = this.queue.get(pos);
            if (request == null) {
               iterator.remove();
            } else if (!this.inFlight.containsKey(pos)
               && !this.backingOff(pos)
               && (burstItem == null || request.item == burstItem)
               && world.available()
               && world.known(pos)) {
               PlacementManager.Prepared prepared = prepare(request);
               if (prepared == null) {
                  if (!world.replaceable(pos)) {
                     iterator.remove();
                     this.queue.remove(pos);
                  } else {
                     this.defer(PlacementManager.DeferralReason.WORLD);
                  }
               } else {
                  if (burstItem == null) {
                     burstItem = request.item;
                  }

                  ready.add(prepared);
               }
            }
         }

         this.scheduleBackoffWake(context);
         if (!ready.isEmpty() && admissible(context, ready.getFirst())) {
            class_1792 selectedItem = burstItem;
            Result item = InventoryUtil.find(selectedItem, InventoryUtil.HOTBAR_SCOPE);
            if (!item.found()) {
               this.defer(PlacementManager.DeferralReason.ITEM);
               this.owner.publish(this);
               return 0;
            }

            List<PlacementManager.Sent> sent = new ArrayList<>(ready.size());
            boolean acquired = InventoryUtil.withSwapNow(context, item, action -> {
               for (PlacementManager.Prepared planned : ready) {
                  PlacementManager.Prepared prepared = prepare(planned.request);
                  if (prepared != null) {
                     if (!action.placeBurst(item.hand(), prepared.hit, prepared.request.options.swing, prepared.request.options.extendedReach)) {
                        break;
                     }

                     sent.add(new PlacementManager.Sent(prepared, action.lastUseSequence()));
                  }
               }
            });
            if (acquired && !sent.isEmpty()) {
               long sentAt = System.nanoTime();
               long expiresAt = sentAt + context.limits().acknowledgementTimeoutNanos();

               for (PlacementManager.Sent entry : sent) {
                  PlacementManager.Request request = entry.prepared.request;
                  this.queue.remove(request.pos);
                  this.order.remove(request.pos);
                  this.inFlight.put(request.pos, new PlacementManager.Pending(request, entry.sequence, sentAt, expiresAt, false, false));
                  this.threatened.remove(request.pos);
               }

               this.scheduleExpiryWake(context);
               this.lastDeferral = PlacementManager.DeferralReason.NONE;
               this.owner.publish(this);
               return sent.size();
            } else {
               this.defer(context.awaitingPositionConfirmation() ? PlacementManager.DeferralReason.POSITION_CONFIRMATION : PlacementManager.DeferralReason.SWAP);
               this.owner.publish(this);
               return 0;
            }
         } else {
            if (!ready.isEmpty()) {
               this.defer(PlacementManager.DeferralReason.BUDGET);
               this.scheduleBudgetWake(context);
            }

            this.owner.publish(this);
            return 0;
         }
      }

      private void observeBlockUpdate(NetworkActionContext context, class_2338 pos, boolean nowReplaceable) {
         context.requireNetworkThread();
         this.adoptWorld();
         this.threatened.remove(pos);
         if (!nowReplaceable) {
            this.inFlight.remove(pos);
            this.backoff.remove(pos);
            this.dequeue(pos);
            this.owner.publish(this);
         } else {
            PlacementManager.Pending pending = this.inFlight.get(pos);
            if (pending != null) {
               if (!pending.acknowledged) {
                  this.inFlight.put(pos, pending.seeAir());
                  this.owner.publish(this);
                  return;
               }

               this.inFlight.remove(pos);
               this.refused(pos);
            }

            PlacementManager.Request request = this.maintained.get(pos);
            if (request == null && pending != null) {
               request = pending.request;
            }

            if (request != null) {
               this.dequeue(pos);
               this.enqueue(request, true);
               this.requestFlush(context);
            } else {
               this.owner.publish(this);
            }
         }
      }

      private void observeActionAck(NetworkActionContext context, int sequence) {
         context.requireNetworkThread();
         if (!this.inFlight.isEmpty()) {
            boolean changed = false;
            List<class_2338> refused = new ArrayList<>();

            for (Entry<class_2338, PlacementManager.Pending> entry : this.inFlight.entrySet()) {
               PlacementManager.Pending pending = entry.getValue();
               if (pending.sequence >= 0 && pending.sequence <= sequence && !pending.acknowledged) {
                  PlacementManager.Pending acknowledged = pending.acknowledge();
                  entry.setValue(acknowledged);
                  changed = true;
                  if (acknowledged.refused()) {
                     refused.add(entry.getKey());
                  }
               }
            }

            if (changed) {
               if (refused.isEmpty()) {
                  this.owner.publish(this);
               } else {
                  PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();

                  for (class_2338 pos : refused) {
                     PlacementManager.Pending pending = this.inFlight.remove(pos);
                     this.refused(pos);
                     if (world.replaceable(pos)) {
                        this.dequeue(pos);
                        this.enqueue(this.maintained.getOrDefault(pos, pending.request), true);
                     }
                  }

                  this.requestFlush(context);
               }
            }
         }
      }

      private void observeBreakProgress(class_2338 pos, int progress) {
         this.adoptWorld();
         if (progress >= 0 && progress <= 9 && this.maintained.containsKey(pos)) {
            this.threatened.add(pos);
         } else if (progress < 0 || progress > 9) {
            this.threatened.remove(pos);
         }

         this.owner.publish(this);
      }

      private void expirePending() {
         long now = System.nanoTime();
         PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
         Iterator<Entry<class_2338, PlacementManager.Pending>> iterator = this.inFlight.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<class_2338, PlacementManager.Pending> entry = iterator.next();
            PlacementManager.Pending pending = entry.getValue();
            if (now >= pending.expiresAt) {
               iterator.remove();
               if (pending.acknowledged) {
                  this.refused(entry.getKey());
               } else {
                  this.ackTimeouts++;
               }

               PlacementManager.Request request = this.maintained.getOrDefault(entry.getKey(), pending.request);
               if (world.replaceable(entry.getKey())) {
                  this.enqueue(request, true);
               }
            }
         }
      }

      private void adoptWorld() {
         long epoch = PlacementWorldSnapshot.snapshot().epoch();
         if (epoch != 0L && this.worldEpoch != epoch) {
            this.worldEpoch = epoch;
            this.order.clear();
            this.queue.clear();
            this.inFlight.clear();
            this.maintained.clear();
            this.threatened.clear();
            this.backoff.clear();
            this.backoffWakeAt = 0L;
            this.budgetWakeAt = 0L;
            this.expiryWakeAt = 0L;
            this.owner.publish(this);
         }
      }

      private void requestFlush(NetworkActionContext context) {
         if (!this.flushScheduled) {
            this.flushScheduled = true;
            context.schedule(scheduled -> {
               this.flushScheduled = false;
               this.flush(scheduled);
            }, 0L);
         }
      }

      private void refused(class_2338 pos) {
         this.refusals++;
         long now = System.nanoTime();
         PlacementManager.Backoff previous = this.backoff.get(pos);
         int streak = previous != null && now - previous.until <= 5000000000L ? previous.streak + 1 : 1;
         long hold = 0L;
         if (streak > 2) {
            int doublings = Math.min(8, streak - 2 - 1);
            hold = Math.min(2000000000L, 250000000L << doublings);
         }

         this.backoff.put(pos, new PlacementManager.Backoff(streak, now + hold));
      }

      private boolean backingOff(class_2338 pos) {
         PlacementManager.Backoff entry = this.backoff.get(pos);
         return entry != null && System.nanoTime() - entry.until < 0L;
      }

      private void scheduleBackoffWake(NetworkActionContext context) {
         long now = System.nanoTime();
         long target = Long.MAX_VALUE;

         for (class_2338 pos : this.queue.keySet()) {
            PlacementManager.Backoff entry = this.backoff.get(pos);
            if (entry != null && entry.until - now > 0L) {
               target = Math.min(target, entry.until);
            }
         }

         if (target != Long.MAX_VALUE && (this.backoffWakeAt == 0L || this.backoffWakeAt - now <= 0L || this.backoffWakeAt > target)) {
            this.backoffWakeAt = target;
            long scheduledTarget = target;
            context.schedule(scheduled -> {
               if (this.backoffWakeAt == scheduledTarget) {
                  this.backoffWakeAt = 0L;
                  this.flush(scheduled);
               }
            }, Math.max(0L, scheduledTarget - now));
         }
      }

      private void scheduleExpiryWake(NetworkActionContext context) {
         long target = Long.MAX_VALUE;

         for (PlacementManager.Pending pending : this.inFlight.values()) {
            target = Math.min(target, pending.expiresAt);
         }

         if (target != Long.MAX_VALUE && (this.expiryWakeAt == 0L || this.expiryWakeAt > target)) {
            this.expiryWakeAt = target;
            long scheduledTarget = target;
            long delay = Math.max(0L, scheduledTarget - System.nanoTime());
            context.schedule(scheduled -> {
               if (this.expiryWakeAt == scheduledTarget) {
                  this.expiryWakeAt = 0L;
                  this.flush(scheduled);
               }
            }, delay);
         }
      }

      private void scheduleBudgetWake(NetworkActionContext context) {
         long delay = context.limits().nanosUntilCanSend(PacketLimiter.Bucket.USE_ITEM, 1);
         if (delay > 0L) {
            long target = System.nanoTime() + delay;
            if (this.budgetWakeAt == 0L || this.budgetWakeAt > target) {
               this.budgetWakeAt = target;
               context.schedule(scheduled -> {
                  if (this.budgetWakeAt == target) {
                     this.budgetWakeAt = 0L;
                     this.flush(scheduled);
                  }
               }, delay);
            }
         }
      }

      private void defer(PlacementManager.DeferralReason reason) {
         this.lastDeferral = reason;
         switch (reason) {
            case NONE:
            default:
               break;
            case BUDGET:
               this.budgetDeferrals++;
               break;
            case WORLD:
               this.worldDeferrals++;
               break;
            case ITEM:
               this.itemDeferrals++;
               break;
            case SWAP:
               this.swapDeferrals++;
               break;
            case POSITION_CONFIRMATION:
               this.positionDeferrals++;
         }
      }

      private static boolean admissible(NetworkActionContext context, PlacementManager.Prepared prepared) {
         int packets = prepared.request.options.swing ? 2 : 1;
         return context.limits().canSend(PacketLimiter.Bucket.USE_ITEM) && context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= packets;
      }

      private static PlacementManager.Prepared prepare(PlacementManager.Request request) {
         PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
         class_2338 pos = request.pos;
         PlacementManager.Options options = request.options;
         if (world.available()
            && world.known(pos)
            && world.replaceable(pos)
            && !world.selfCollision(pos)
            && (options.allowEntityCollision || !world.entityCollision(pos))) {
            class_2350 supportSide = request.supportSide;
            if (supportSide != null && !usableSupport(world, pos, supportSide, options)) {
               supportSide = null;
            }

            if (supportSide == null) {
               supportSide = nearestSupport(world, pos, options);
            }

            class_3965 hit;
            if (supportSide == null) {
               if (!options.allowAirPlace) {
                  return null;
               }

               class_2350 face = airFace(world, pos, request.item);
               class_243 location = faceCenter(pos, face);
               hit = new class_3965(location, face, pos, inside(world, pos));
            } else {
               class_2338 support = pos.method_10093(supportSide);
               class_243 location = faceCenter(pos, supportSide);
               class_2350 hitSide = supportSide.method_10153();
               hit = new class_3965(location, hitSide, support, inside(world, support));
            }

            return !options.extendedReach && !world.withinPlaceReach(hit.method_17777()) ? null : new PlacementManager.Prepared(request, hit);
         } else {
            return null;
         }
      }

      private static class_2350 nearestSupport(PlacementWorldSnapshot.Snapshot world, class_2338 pos, PlacementManager.Options options) {
         class_2350 best = null;
         double bestDistance = Double.POSITIVE_INFINITY;

         for (class_2350 side : class_2350.values()) {
            if (usableSupport(world, pos, side, options)) {
               class_243 location = faceCenter(pos, side);
               double distance = world.distanceToSqr(location.field_1352, location.field_1351, location.field_1350);
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = side;
               }
            }
         }

         return best;
      }

      private static class_2350 airFace(PlacementWorldSnapshot.Snapshot world, class_2338 pos, class_1792 item) {
         if (item instanceof class_1747 blockItem) {
            class_2680 desired = blockItem.method_7711().method_9564();
            if (desired.method_28498(class_2741.field_12496)) {
               return switch ((class_2351)desired.method_11654(class_2741.field_12496)) {
                  case field_11048 -> class_2350.field_11034;
                  case field_11052 -> class_2350.field_11036;
                  case field_11051 -> class_2350.field_11035;
                  default -> throw new MatchException(null, null);
               };
            }
         }

         class_2350 best = class_2350.field_11036;
         double bestDistance = Double.POSITIVE_INFINITY;
         boolean bestVisible = false;

         for (class_2350 side : class_2350.values()) {
            class_243 location = faceCenter(pos, side);
            double distance = world.distanceToSqr(location.field_1352, location.field_1351, location.field_1350);
            boolean visible = world.faceVisible(pos, side, 0.05);
            if (visible && !bestVisible || visible == bestVisible && distance < bestDistance) {
               bestDistance = distance;
               best = side;
               bestVisible = visible;
            }
         }

         return best;
      }

      private static boolean usableSupport(PlacementWorldSnapshot.Snapshot world, class_2338 pos, class_2350 side, PlacementManager.Options options) {
         class_2338 support = pos.method_10093(side);
         return world.support(support) && supportFaceVisible(world, pos, side) && (options.extendedReach || world.withinPlaceReach(support));
      }

      private static boolean supportFaceVisible(PlacementWorldSnapshot.Snapshot world, class_2338 pos, class_2350 side) {
         return world.faceVisible(pos.method_10093(side), side.method_10153(), 0.05);
      }

      private static class_243 faceCenter(class_2338 pos, class_2350 side) {
         return class_243.method_24953(pos).method_1031(side.method_10148() * 0.5, side.method_10164() * 0.5, side.method_10165() * 0.5);
      }

      private static boolean inside(PlacementWorldSnapshot.Snapshot world, class_2338 pos) {
         return world.eyeInside(pos);
      }
   }

   public record Status(
      int queued,
      int inFlight,
      int maintained,
      int threatened,
      PlacementManager.DeferralReason lastDeferral,
      long budgetDeferrals,
      long worldDeferrals,
      long itemDeferrals,
      long swapDeferrals,
      long positionDeferrals,
      long acknowledgementTimeouts,
      long refusals
   ) {
      public static final PlacementManager.Status EMPTY = new PlacementManager.Status(
         0, 0, 0, 0, PlacementManager.DeferralReason.NONE, 0L, 0L, 0L, 0L, 0L, 0L, 0L
      );
   }
}
