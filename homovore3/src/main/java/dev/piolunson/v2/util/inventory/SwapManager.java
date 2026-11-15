package dev.piolunson.v2.util.inventory;

import dev.piolunson.v2.Homovore2;
import dev.piolunson.v2.mixin.MultiPlayerGameModeAccessor;
import dev.piolunson.v2.util.network.NetworkAction;
import dev.piolunson.v2.util.network.NetworkActionContext;
import dev.piolunson.v2.util.network.NetworkThreadExecutor;
import dev.piolunson.v2.util.network.PacketLimiter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class SwapManager {
   public static final int SILENT_BUDGET = 3;
   public static final int ALTSWAP_BUDGET = 1;
   private static final int OFFHAND_CONFIRMATION_TIMEOUT_TICKS = 40;
   private static final long CLIENT_RESERVATION_WAIT_NANOS = 5000000L;
   private static final Set<ResultType> HOTBAR_ONLY = Set.of(ResultType.HOTBAR);
   private final NetworkThreadExecutor.Session session;
   private final SlotManager slots;
   private final AtomicReference<SwapManager.State> published = new AtomicReference<>(SwapManager.State.EMPTY);
   private final AtomicReference<SwapManager.ClientSelection> clientSelection = new AtomicReference<>(SwapManager.ClientSelection.EMPTY);
   private final AtomicLong clientSelectionVersions = new AtomicLong();
   private final AtomicLong appliedClientSelectionVersion = new AtomicLong();
   private long tick;
   private long nextLeaseId;
   private int silentUsed;
   private int altSwapUsed;
   private boolean compoundPending;
   private SwapManager.Latch latch;
   private SwapManager.PendingOffhand pendingOffhand;

   public SwapManager(NetworkThreadExecutor.Session session, SlotManager slots) {
      this.session = Objects.requireNonNull(session, "session");
      this.slots = Objects.requireNonNull(slots, "slots");
   }

   public CompletableFuture<Boolean> silent(int hotbarSlot, NetworkAction action) {
      Objects.requireNonNull(action, "action");
      return !SlotManager.validHotbarSlot(hotbarSlot)
         ? CompletableFuture.completedFuture(false)
         : this.session.submit(context -> this.silentNow(context, hotbarSlot, null, action));
   }

   public CompletableFuture<Boolean> silent(Result result, NetworkAction action) {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      return result.found() && result.type() == ResultType.HOTBAR
         ? this.session.submit(context -> this.silentNow(context, result.slot(), result, action))
         : CompletableFuture.completedFuture(false);
   }

   public CompletableFuture<Boolean> silent(int hotbarSlot, Runnable action) {
      Objects.requireNonNull(action, "action");
      return this.silent(hotbarSlot, context -> action.run());
   }

   public CompletableFuture<Boolean> silent(Predicate<ItemStack> match, NetworkAction action) {
      Objects.requireNonNull(match, "match");
      Objects.requireNonNull(action, "action");
      return this.silent(InventoryUtil.find(match, HOTBAR_ONLY), action);
   }

   public CompletableFuture<Boolean> silent(Predicate<ItemStack> match, Runnable action) {
      Objects.requireNonNull(action, "action");
      return this.silent(match, context -> action.run());
   }

   public CompletableFuture<Boolean> altSwap(int inventorySlot, NetworkAction action) {
      Objects.requireNonNull(action, "action");
      return inventorySlot >= 0 && inventorySlot < 36 ? this.altSwapAsync(inventorySlot, null, action) : CompletableFuture.completedFuture(false);
   }

   public CompletableFuture<Boolean> altSwap(Result result, NetworkAction action) {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      return !result.found() || result.type() != ResultType.HOTBAR && result.type() != ResultType.INVENTORY
         ? CompletableFuture.completedFuture(false)
         : this.altSwapAsync(result.slot(), result, action);
   }

   public CompletableFuture<Boolean> altSwap(int inventorySlot, Runnable action) {
      Objects.requireNonNull(action, "action");
      return this.altSwap(inventorySlot, context -> action.run());
   }

   public CompletableFuture<Boolean> altSilent(int inventorySlot, NetworkAction action) {
      return this.altSwap(inventorySlot, action);
   }

   public CompletableFuture<Boolean> altSilent(int inventorySlot, Runnable action) {
      return this.altSwap(inventorySlot, action);
   }

   public CompletableFuture<Boolean> altSilent(Predicate<ItemStack> match, NetworkAction action) {
      Objects.requireNonNull(match, "match");
      Objects.requireNonNull(action, "action");
      return this.altSwap(InventoryUtil.find(match, InventoryUtil.MAIN_INVENTORY_SCOPE), action);
   }

   public CompletableFuture<Boolean> altSilent(Predicate<ItemStack> match, Runnable action) {
      Objects.requireNonNull(action, "action");
      return this.altSilent(match, context -> action.run());
   }

   public CompletableFuture<Boolean> withSwap(Result result, NetworkAction action) {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      if (!result.found()) {
         return CompletableFuture.completedFuture(false);
      }

      return switch (result.type()) {
         case OFFHAND -> this.session.submit(context -> {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            this.checkOffhandConfirmation(snapshot);
            if (!this.available(result, snapshot)) {
               return false;
            }

            run(action, context);
            return true;
         });
         case HOTBAR -> this.silent(result, action);
         case INVENTORY -> this.altSwap(result, action);
         case NONE -> CompletableFuture.completedFuture(false);
      };
   }

   public CompletableFuture<Boolean> sequence(List<SwapManager.Step> steps) {
      Objects.requireNonNull(steps, "steps");
      List<SwapManager.Step> immutable = List.copyOf(steps);
      if (!immutable.isEmpty() && !immutable.stream().anyMatch(step -> !step.item().found() || step.item().type() == ResultType.OFFHAND)) {
         boolean hasInventory = immutable.stream().anyMatch(step -> step.item().type() == ResultType.INVENTORY);
         return !hasInventory
            ? this.session.submit(context -> this.runHotbarSequence(context, immutable))
            : awaitOnClientThread(this.session.submit(context -> this.reserveSequence(context, immutable))).thenCompose(ticket -> {
               if (ticket == null) {
                  return CompletableFuture.completedFuture(false);
               }

               CompletableFuture<Boolean> done = new CompletableFuture<>();
               long observedBefore = this.session.limiter().containerClicksObserved();
               runOnClientThread(() -> this.runClientSequence(ticket, observedBefore, done));
               return done;
            });
      } else {
         return CompletableFuture.completedFuture(false);
      }
   }

   public boolean withSwapNow(NetworkActionContext context, Result result, NetworkAction action) {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      context.requireNetworkThread();
      if (!result.found()) {
         return false;
      }

      return switch (result.type()) {
         case OFFHAND -> {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            this.checkOffhandConfirmation(snapshot);
            if (!this.available(result, snapshot)) {
               yield false;
            } else {
               run(action, context);
               yield true;
            }
         }
         case HOTBAR -> this.silentNow(context, result.slot(), result, action);
         case INVENTORY -> this.altSwapRefused(result.slot());
         case NONE -> false;
      };
   }

   public CompletableFuture<Boolean> withHeld(Result result, NetworkAction action) {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      return result.found() && result.type() == ResultType.HOTBAR && result.holding() ? this.session.submit(context -> {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         this.checkOffhandConfirmation(snapshot);
         if (this.available(result, snapshot) && this.selected(snapshot) == result.slot()) {
            run(action, context);
            return true;
         } else {
            return false;
         }
      }) : CompletableFuture.completedFuture(false);
   }

   public CompletableFuture<SwapManager.MainhandLease> leaseMainhand(int hotbarSlot) {
      return !SlotManager.validHotbarSlot(hotbarSlot)
         ? CompletableFuture.completedFuture(null)
         : this.session.submit(context -> this.leaseMainhandNow(context, hotbarSlot, null));
   }

   public CompletableFuture<SwapManager.MainhandLease> leaseMainhand(Result result) {
      Objects.requireNonNull(result, "result");
      return result.found() && result.type() == ResultType.HOTBAR
         ? this.session.submit(context -> this.leaseMainhandNow(context, result.slot(), result))
         : CompletableFuture.completedFuture(null);
   }

   public CompletableFuture<Boolean> visible(int hotbarSlot) {
      return this.leaseMainhand(hotbarSlot).thenApply(Objects::nonNull);
   }

   public CompletableFuture<Boolean> visible(Predicate<ItemStack> match) {
      Objects.requireNonNull(match, "match");
      return this.leaseMainhand(InventoryUtil.find(match, HOTBAR_ONLY)).thenApply(Objects::nonNull);
   }

   public CompletableFuture<Void> release() {
      return this.session.submit(context -> {
         this.releaseNow(context, 0L);
         return null;
      });
   }

   public CompletableFuture<Boolean> moveToOffhand(Result result) {
      Objects.requireNonNull(result, "result");
      return result.found() && result.type() != ResultType.OFFHAND
         ? this.session.submit(context -> this.moveToOffhandNow(context, result))
         : CompletableFuture.completedFuture(false);
   }

   public CompletableFuture<Boolean> refillHotbar(Result target, Result source, boolean quickMove) {
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(source, "source");
      return target.type() == ResultType.HOTBAR && source.type() == ResultType.INVENTORY && target.snapshotVersion() == source.snapshotVersion()
         ? this.session.submit(context -> this.refillHotbarNow(context, target, source, quickMove))
         : CompletableFuture.completedFuture(false);
   }

   public boolean isLatched() {
      return this.published.get().latchedSlot() >= 0;
   }

   public int getLatchedSlot() {
      return this.published.get().latchedSlot();
   }

   public int getLatchOrigin() {
      return this.published.get().latchOrigin();
   }

   public int getLatchTicks() {
      return this.published.get().latchTicks();
   }

   public SwapManager.State state() {
      return this.published.get();
   }

   public void syncClientSelection(MinecraftClient client) {
      Objects.requireNonNull(client, "client");
      if (!client.isOnThread()) {
         throw new IllegalStateException("Visible selections must be applied on the client thread");
      }

      if (client.player != null) {
         SwapManager.ClientSelection selection = this.clientSelection.get();
         if (SlotManager.validHotbarSlot(selection.slot)) {
            long applied = this.appliedClientSelectionVersion.get();
            if (selection.held || selection.version > applied) {
               if (client.player.getInventory().getSelectedSlot() != selection.slot) {
                  client.player.getInventory().setSelectedSlot(selection.slot);
               }

               if (client.interactionManager != null) {
                  ((MultiPlayerGameModeAccessor)client.interactionManager).homovore$setCarriedIndex(selection.slot);
               }

               this.appliedClientSelectionVersion.accumulateAndGet(selection.version, Math::max);
            }
         }
      }
   }

   public void beginTick(NetworkActionContext context) {
      context.requireNetworkThread();
      this.tick++;
      this.silentUsed = 0;
      this.altSwapUsed = 0;
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (this.latch != null) {
         if (!snapshot.available()) {
            this.latch.active.set(false);
            this.latch = null;
         } else {
            this.latch.ticks++;
            if (this.slots.selectedSlot() != this.latch.slot) {
               this.slots.selectNow(context, this.latch.slot);
            }
         }
      }

      this.publish();
   }

   private boolean silentNow(NetworkActionContext context, int hotbarSlot, Result result, NetworkAction action) {
      context.requireNetworkThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (!this.compoundPending && snapshot.available() && (result == null || this.available(result, snapshot))) {
         if (result == null && this.pendingOffhand != null && this.pendingOffhand.sourceSlot == hotbarSlot) {
            return false;
         }

         int previous = this.selected(snapshot);
         if (!SlotManager.validHotbarSlot(previous)) {
            return false;
         }

         if (hotbarSlot == previous) {
            run(action, context);
            return true;
         }

         if (this.latch != null) {
            return false;
         }

         if (!this.canClaim(SwapManager.Mode.SILENT)) {
            return false;
         }

         if (snapshot.usingItem() && snapshot.usingMainHand()) {
            return false;
         }

         if (!this.claim(SwapManager.Mode.SILENT)) {
            return false;
         }

         this.slots.selectNow(context, hotbarSlot);

         try {
            run(action, context);
         } finally {
            this.slots.selectNow(context, previous);
         }

         return true;
      } else {
         return false;
      }
   }

   private CompletableFuture<Boolean> altSwapAsync(int inventorySlot, Result result, NetworkAction action) {
      return awaitOnClientThread(this.session.submit(context -> this.reserveAltSwap(context, inventorySlot, result))).thenCompose(ticket -> {
         if (ticket == null) {
            return CompletableFuture.completedFuture(false);
         }

         if (ticket.menuSlot < 0) {
            return this.session.submit(context -> {
               run(action, context);
               return true;
            });
         }

         CompletableFuture<Boolean> done = new CompletableFuture<>();
         long observedBefore = this.session.limiter().containerClicksObserved();
         runOnClientThread(() -> this.runAltSwap(ticket, observedBefore, action, done));
         return done;
      });
   }

   private static <T> CompletableFuture<T> awaitOnClientThread(CompletableFuture<T> reservation) {
      if (!reservation.isDone() && MinecraftClient.getInstance().isOnThread()) {
         try {
            reservation.get(5000000L, TimeUnit.NANOSECONDS);
         } catch (TimeoutException | ExecutionException var2) {
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
         }

         return reservation;
      } else {
         return reservation;
      }
   }

   private static void runOnClientThread(Runnable work) {
      if (MinecraftClient.getInstance().isOnThread()) {
         work.run();
      } else {
         ContainerClicks.dispatch(work);
      }
   }

   private SwapManager.AltSwapTicket reserveAltSwap(NetworkActionContext context, int inventorySlot, Result result) {
      context.requireNetworkThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (!this.compoundPending
         && this.latch == null
         && snapshot.available()
         && snapshot.cursorEmpty()
         && this.pendingOffhand == null
         && (result == null || this.available(result, snapshot))) {
         int selected = this.selected(snapshot);
         if (!SlotManager.validHotbarSlot(selected)) {
            return null;
         } else if (inventorySlot == selected) {
            return new SwapManager.AltSwapTicket(snapshot.menuId(), -1, selected);
         } else {
            int menuSlot = snapshot.menuSlot(inventorySlot);
            if (menuSlot < 0 || snapshot.menuSlot(selected) < 0) {
               return null;
            } else if (context.limits().remaining(PacketLimiter.Bucket.CLICK_SLOT) < 2) {
               Homovore2.LOGGER.info("[Inventory] altSwap rejected by packet budget required={}", 2);
               return null;
            } else {
               return !this.claim(SwapManager.Mode.ALTSWAP) ? null : new SwapManager.AltSwapTicket(snapshot.menuId(), menuSlot, selected);
            }
         }
      } else {
         return null;
      }
   }

   private boolean runHotbarSequence(NetworkActionContext context, List<SwapManager.Step> steps) {
      context.requireNetworkThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (!this.compoundPending && this.latch == null && this.validSequence(steps, snapshot)) {
         int selected = this.selected(snapshot);
         if (!SlotManager.validHotbarSlot(selected)) {
            return false;
         }

         boolean changesSlot = steps.stream().anyMatch(stepx -> stepx.item().slot() != selected);
         if (changesSlot && !this.claim(SwapManager.Mode.SILENT)) {
            return false;
         }

         try {
            for (SwapManager.Step step : steps) {
               int slot = step.item().slot();
               if (slot != selected) {
                  this.slots.selectNow(context, slot);
               }

               run(step.action(), context);
            }

            return true;
         } finally {
            if (this.slots.selectedSlot() != selected) {
               this.slots.selectNow(context, selected);
            }
         }
      } else {
         return false;
      }
   }

   private SwapManager.SequenceTicket reserveSequence(NetworkActionContext context, List<SwapManager.Step> steps) {
      context.requireNetworkThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (!this.compoundPending
         && this.latch == null
         && this.pendingOffhand == null
         && snapshot.available()
         && snapshot.cursorEmpty()
         && this.validSequence(steps, snapshot)) {
         int selected = this.selected(snapshot);
         if (!SlotManager.validHotbarSlot(selected)) {
            return null;
         }

         int clicks = 0;
         boolean changesHotbar = false;

         for (SwapManager.Step step : steps) {
            Result item = step.item();
            if (item.type() == ResultType.INVENTORY) {
               if (item.menuSlot() < 0 || snapshot.menuSlot(selected) < 0) {
                  return null;
               }

               clicks += 2;
            } else if (item.slot() != selected) {
               changesHotbar = true;
            }
         }

         if (context.limits().canSend(PacketLimiter.Bucket.CLICK_SLOT, clicks)
            && this.canClaim(SwapManager.Mode.ALTSWAP)
            && (!changesHotbar || this.canClaim(SwapManager.Mode.SILENT))) {
            this.claim(SwapManager.Mode.ALTSWAP);
            if (changesHotbar) {
               this.claim(SwapManager.Mode.SILENT);
            }

            this.compoundPending = true;
            this.publish();
            return new SwapManager.SequenceTicket(steps, snapshot.menuId(), selected);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private void runClientSequence(SwapManager.SequenceTicket ticket, long observedBefore, CompletableFuture<Boolean> done) {
      List<CompletableFuture<Boolean>> actions = new ArrayList<>(ticket.steps().size());
      int applied = 0;
      boolean clicksApplied = true;
      Throwable failure = null;

      try {
         for (SwapManager.Step step : ticket.steps()) {
            Result item = step.item();
            if (item.type() == ResultType.INVENTORY) {
               if (!ContainerClicks.cursorEmpty() || !ContainerClicks.apply(ticket.containerId(), item.menuSlot(), ticket.selectedSlot(), SlotActionType.SWAP)) {
                  clicksApplied = false;
                  break;
               }

               applied++;
               actions.add(this.session.submit(context -> {
                  run(step.action(), context);
                  return true;
               }));
               if (!ContainerClicks.apply(ticket.containerId(), item.menuSlot(), ticket.selectedSlot(), SlotActionType.SWAP)) {
                  clicksApplied = false;
                  break;
               }

               applied++;
            } else {
               actions.add(this.session.submit(context -> {
                  this.runHotbarStep(context, ticket.selectedSlot(), step);
                  return true;
               }));
            }
         }
      } catch (RuntimeException | Error throwable) {
         failure = throwable;
      } finally {
         this.slots.reconcileDispatched(observedBefore, applied);
      }

      boolean allClicksApplied = clicksApplied;
      Throwable dispatchFailure = failure;
      CompletableFuture.allOf(actions.toArray(CompletableFuture[]::new))
         .whenComplete((ignored, actionFailure) -> this.releaseSequence(done, allClicksApplied, dispatchFailure != null ? dispatchFailure : actionFailure));
   }

   private void runHotbarStep(NetworkActionContext context, int selected, SwapManager.Step step) {
      context.requireNetworkThread();
      int slot = step.item().slot();
      if (slot != selected) {
         this.slots.selectNow(context, slot);
      }

      try {
         run(step.action(), context);
      } finally {
         if (slot != selected) {
            this.slots.selectNow(context, selected);
         }
      }
   }

   private void releaseSequence(CompletableFuture<Boolean> done, boolean successful, Throwable failure) {
      this.session.<Object>submit(context -> {
         this.compoundPending = false;
         this.publish();
         return null;
      }).whenComplete((ignored, releaseFailure) -> {
         Throwable outcome = failure != null ? failure : releaseFailure;
         if (outcome != null) {
            done.completeExceptionally(outcome);
         } else {
            done.complete(successful);
         }
      });
   }

   private boolean validSequence(List<SwapManager.Step> steps, InventorySnapshot snapshot) {
      for (SwapManager.Step step : steps) {
         Result item = step.item();
         if (item.snapshotVersion() != snapshot.version()
            || item.menuId() != snapshot.menuId()
            || item.type() != ResultType.HOTBAR && item.type() != ResultType.INVENTORY
            || !this.available(item, snapshot)) {
            return false;
         }
      }

      return true;
   }

   private void runAltSwap(SwapManager.AltSwapTicket ticket, long observedBefore, NetworkAction action, CompletableFuture<Boolean> done) {
      if (ContainerClicks.cursorEmpty() && ContainerClicks.apply(ticket.containerId, ticket.menuSlot, ticket.hotbarSlot, SlotActionType.SWAP)) {
         int applied = 1;

         try {
            CompletableFuture<Boolean> acted = this.session.submit(context -> {
               run(action, context);
               return true;
            });
            if (ContainerClicks.apply(ticket.containerId, ticket.menuSlot, ticket.hotbarSlot, SlotActionType.SWAP)) {
               applied++;
            } else {
               Homovore2.LOGGER.warn("[Inventory] altSwap could not restore menuSlot={} hotbarSlot={}", ticket.menuSlot, ticket.hotbarSlot);
            }

            acted.whenComplete((ran, failure) -> {
               if (failure != null) {
                  done.completeExceptionally(failure);
               } else {
                  done.complete(Boolean.TRUE.equals(ran));
               }
            });
         } finally {
            this.slots.reconcileDispatched(observedBefore, applied);
         }
      } else {
         done.complete(false);
      }
   }

   private boolean altSwapRefused(int inventorySlot) {
      Homovore2.LOGGER.info("[Inventory] altSwap refused synchronously inventorySlot={}; container clicks require the client thread", inventorySlot);
      return false;
   }

   private SwapManager.MainhandLease leaseMainhandNow(NetworkActionContext context, int hotbarSlot, Result result) {
      context.requireNetworkThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (!this.compoundPending
         && this.latch == null
         && snapshot.available()
         && !snapshot.usingItem()
         && (result != null || this.pendingOffhand == null || this.pendingOffhand.sourceSlot != hotbarSlot)
         && (result == null || this.available(result, snapshot))) {
         int origin = this.selected(snapshot);
         if (!SlotManager.validHotbarSlot(origin)) {
            return null;
         }

         long id = ++this.nextLeaseId;
         SwapManager.MainhandLease lease = new SwapManager.MainhandLease(this, id, hotbarSlot, origin);
         this.latch = new SwapManager.Latch(id, hotbarSlot, origin, lease.active);
         this.slots.selectNow(context, hotbarSlot);
         this.publishClientSelection(hotbarSlot, true);
         this.publish();
         return lease;
      } else {
         return null;
      }
   }

   private boolean moveToOffhandNow(NetworkActionContext context, Result result) {
      context.requireNetworkThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (!this.compoundPending && this.pendingOffhand == null && snapshot.menuId() == 0 && snapshot.cursorEmpty() && this.available(result, snapshot)) {
         int menuSlot = snapshot.menuSlot(result.slot());
         if (menuSlot >= 0 && this.slots.clickNow(context, snapshot, menuSlot, 40, SlotActionType.SWAP, false)) {
            this.pendingOffhand = new SwapManager.PendingOffhand(
               result.snapshotVersion(), snapshot.menuStateId(), result.slot(), result.stack(), this.tick + 40L
            );
            this.publish();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean refillHotbarNow(NetworkActionContext context, Result target, Result source, boolean quickMove) {
      context.requireNetworkThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      this.checkOffhandConfirmation(snapshot);
      if (!this.compoundPending
         && this.latch == null
         && this.pendingOffhand == null
         && snapshot.available()
         && snapshot.cursorEmpty()
         && target.menuId() == source.menuId()
         && target.menuId() == snapshot.menuId()
         && mergeable(target, source, snapshot)) {
         int from = snapshot.menuSlot(source.slot());
         int to = snapshot.menuSlot(target.slot());
         if (from >= 0 && to >= 0 && from != to && this.claim(SwapManager.Mode.ALTSWAP)) {
            ItemStack currentTarget = snapshot.itemInternal(target.slot());
            ItemStack currentSource = snapshot.itemInternal(source.slot());
            if (currentTarget.isEmpty() || quickMove && currentSource.getCount() > currentTarget.getCount()) {
               Homovore2.LOGGER.info("[Inventory] refill targeted-swap inventorySlot={} -> hotbarSlot={}", source.slot(), target.slot());
               return this.slots.swapInventoryNow(context, snapshot, from, target.slot());
            } else {
               Homovore2.LOGGER
                  .info("[Inventory] refill merge inventorySlot={} -> hotbarSlot={} quickMove={}", new Object[]{source.slot(), target.slot(), quickMove});
               return this.slots.mergeStacksNow(context, snapshot, from, to);
            }
         } else {
            Homovore2.LOGGER.info("[Inventory] refill rejected fromMenuSlot={} toMenuSlot={} altSwapUsed={}/{}", new Object[]{from, to, this.altSwapUsed, 1});
            return false;
         }
      } else {
         Homovore2.LOGGER
            .info(
               "[Inventory] refill rejected latch={} pendingOffhand={} available={} cursorEmpty={} targetMenu={} sourceMenu={} currentMenu={} mergeable={}",
               new Object[]{
                  this.latch != null,
                  this.pendingOffhand != null,
                  snapshot.available(),
                  snapshot.cursorEmpty(),
                  target.menuId(),
                  source.menuId(),
                  snapshot.menuId(),
                  mergeable(target, source, snapshot)
               }
            );
         return false;
      }
   }

   private static boolean mergeable(Result target, Result source, InventorySnapshot snapshot) {
      int targetSlot = target.slot();
      int sourceSlot = source.slot();
      if (targetSlot >= 0 && targetSlot < snapshot.size() && sourceSlot >= 0 && sourceSlot < snapshot.size()) {
         ItemStack targetStack = snapshot.itemInternal(targetSlot);
         ItemStack sourceStack = snapshot.itemInternal(sourceSlot);
         ItemStack template = target.stack();
         return !template.isEmpty()
            && template.isStackable()
            && (targetStack.isEmpty() || targetStack.getCount() < targetStack.getMaxCount() && ItemStack.areItemsAndComponentsEqual(targetStack, template))
            && ItemStack.areItemsAndComponentsEqual(sourceStack, template);
      } else {
         return false;
      }
   }

   private boolean available(Result result, InventorySnapshot snapshot) {
      return InventoryUtil.stillMatches(result, snapshot)
         && (this.pendingOffhand == null || result.type() != ResultType.OFFHAND && result.slot() != this.pendingOffhand.sourceSlot);
   }

   private int selected(InventorySnapshot snapshot) {
      int selected = this.slots.selectedSlot();
      return SlotManager.validHotbarSlot(selected) ? selected : snapshot.selectedSlot();
   }

   private boolean claim(SwapManager.Mode mode) {
      if (!this.canClaim(mode)) {
         return false;
      }

      if (mode == SwapManager.Mode.SILENT) {
         this.silentUsed++;
      } else {
         this.altSwapUsed++;
      }

      this.publish();
      return true;
   }

   public int silentRemaining(NetworkActionContext context) {
      context.requireNetworkThread();
      return !this.compoundPending && this.latch == null ? Math.max(0, 3 - this.silentUsed) : 0;
   }

   private boolean canClaim(SwapManager.Mode mode) {
      return switch (mode) {
         case SILENT -> this.silentUsed < 3;
         case ALTSWAP -> this.altSwapUsed < 1;
      };
   }

   private void checkOffhandConfirmation(InventorySnapshot snapshot) {
      if (this.pendingOffhand != null && snapshot.available()) {
         boolean confirmed = snapshot.version() > this.pendingOffhand.snapshotVersion
            && ItemStack.areEqual(this.pendingOffhand.stack, snapshot.offhandInternal())
            && (
               snapshot.menuStateId() != this.pendingOffhand.menuStateId
                  || !ItemStack.areEqual(this.pendingOffhand.stack, snapshot.itemInternal(this.pendingOffhand.sourceSlot))
            );
         if (confirmed || this.tick >= this.pendingOffhand.expiresAtTick) {
            this.pendingOffhand = null;
            this.publish();
         }
      }
   }

   private void release(long leaseId) {
      this.session.submit(context -> {
         this.releaseNow(context, leaseId);
         return null;
      });
   }

   private void releaseNow(NetworkActionContext context, long leaseId) {
      context.requireNetworkThread();
      if (this.latch != null && (leaseId == 0L || this.latch.id == leaseId)) {
         SwapManager.Latch releasing = this.latch;
         this.latch = null;
         releasing.active.set(false);
         if (this.session.isOpen()) {
            this.slots.selectNow(context, releasing.origin);
         }

         this.publishClientSelection(releasing.origin, false);
         this.publish();
      }
   }

   private void publishClientSelection(int slot, boolean held) {
      this.clientSelection.set(new SwapManager.ClientSelection(this.clientSelectionVersions.incrementAndGet(), slot, held));
   }

   private void publish() {
      this.published
         .set(
            new SwapManager.State(
               this.tick,
               this.silentUsed,
               this.altSwapUsed,
               this.latch == null ? -1 : this.latch.slot,
               this.latch == null ? -1 : this.latch.origin,
               this.latch == null ? -1 : this.latch.ticks,
               this.pendingOffhand != null
            )
         );
   }

   private static void run(NetworkAction action, NetworkActionContext context) {
      try {
         action.run(context);
      } catch (RuntimeException | Error throwable) {
         throw throwable;
      } catch (Exception exception) {
         throw new SwapManager.NetworkActionException(exception);
      }
   }

   private record AltSwapTicket(int containerId, int menuSlot, int hotbarSlot) {
   }

   private record ClientSelection(long version, int slot, boolean held) {
      private static final SwapManager.ClientSelection EMPTY = new SwapManager.ClientSelection(0L, -1, false);
   }

   private static final class Latch {
      private final long id;
      private final int slot;
      private final int origin;
      private final AtomicBoolean active;
      private int ticks;

      private Latch(long id, int slot, int origin, AtomicBoolean active) {
         this.id = id;
         this.slot = slot;
         this.origin = origin;
         this.active = active;
      }
   }

   public static final class MainhandLease implements AutoCloseable {
      private final SwapManager manager;
      private final long id;
      private final int slot;
      private final int origin;
      private final AtomicBoolean active = new AtomicBoolean(true);

      private MainhandLease(SwapManager manager, long id, int slot, int origin) {
         this.manager = manager;
         this.id = id;
         this.slot = slot;
         this.origin = origin;
      }

      public int slot() {
         return this.slot;
      }

      public int origin() {
         return this.origin;
      }

      public boolean isActive() {
         return this.active.get();
      }

      @Override
      public void close() {
         if (this.active.compareAndSet(true, false)) {
            this.manager.release(this.id);
         }
      }
   }

   public enum Mode {
      SILENT,
      ALTSWAP;
   }

   private static final class NetworkActionException extends RuntimeException {
      private NetworkActionException(Exception cause) {
         super(cause);
      }
   }

   private static final class PendingOffhand {
      private final long snapshotVersion;
      private final int menuStateId;
      private final int sourceSlot;
      private final ItemStack stack;
      private final long expiresAtTick;

      private PendingOffhand(long snapshotVersion, int menuStateId, int sourceSlot, ItemStack stack, long expiresAtTick) {
         this.snapshotVersion = snapshotVersion;
         this.menuStateId = menuStateId;
         this.sourceSlot = sourceSlot;
         this.stack = stack.copy();
         this.expiresAtTick = expiresAtTick;
      }
   }

   private record SequenceTicket(List<SwapManager.Step> steps, int containerId, int selectedSlot) {
   }

   public record State(long tick, int silentUsed, int altSwapUsed, int latchedSlot, int latchOrigin, int latchTicks, boolean offhandPending) {
      private static final SwapManager.State EMPTY = new SwapManager.State(0L, 0, 0, -1, -1, -1, false);
   }

   public record Step(Result item, NetworkAction action) {
      public Step {
         Objects.requireNonNull(item, "item");
         Objects.requireNonNull(action, "action");
      }
   }
}
