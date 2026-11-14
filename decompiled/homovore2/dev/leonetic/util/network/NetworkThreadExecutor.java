package dev.leonetic.util.network;

import dev.leonetic.Homovore2;
import dev.leonetic.Homovore2Client;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.util.inventory.SlotManager;
import dev.leonetic.util.inventory.SwapManager;
import io.netty.channel.EventLoop;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.class_243;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_2708;
import net.minecraft.class_2709;
import net.minecraft.class_2735;
import net.minecraft.class_2793;
import net.minecraft.class_2846;
import net.minecraft.class_2868;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_310;
import net.minecraft.class_634;
import net.minecraft.class_8042;

public final class NetworkThreadExecutor {
   private static final long TELEPORT_CONFIRMATION_DEADLINE_NANOS = 200000000L;
   private static final AtomicReference<NetworkThreadExecutor.Session> CURRENT = new AtomicReference<>();

   private NetworkThreadExecutor() {
      throw new AssertionError();
   }

   public static void bind(class_634 listener) {
      Objects.requireNonNull(listener, "listener");
      NetworkThreadExecutor.Session replacement = new NetworkThreadExecutor.Session(listener);
      NetworkThreadExecutor.Session previous = CURRENT.getAndSet(replacement);
      if (previous != null) {
         previous.close();
      }
   }

   public static void unbind(class_634 listener) {
      if (listener != null) {
         NetworkThreadExecutor.Session session = CURRENT.get();
         if (session != null && session.connection() == listener.method_48296() && CURRENT.compareAndSet(session, null)) {
            session.close();
         }
      }
   }

   public static boolean isConnected() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session != null && session.isOpen();
   }

   public static boolean isNetworkThread() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session != null && session.inEventLoop();
   }

   public static boolean isActiveConnection(class_2535 connection) {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session != null && session.connection() == connection && session.isOpen();
   }

   public static SlotManager slots() {
      NetworkThreadExecutor.Session session = requireSession();
      return session.slotManager();
   }

   public static SwapManager swaps() {
      NetworkThreadExecutor.Session session = requireSession();
      return session.swapManager();
   }

   public static boolean swapsLatched() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session != null && session.isOpen() && session.swapManager().isLatched();
   }

   public static int selectedSlot() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session != null && session.isOpen() ? session.slotManager().selectedSlot() : -1;
   }

   public static PacketLimiter limiter() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session == null ? null : session.limiter();
   }

   public static long containerClicksObserved() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session == null ? 0L : session.limiter().containerClicksObserved();
   }

   public static void reconcileDispatchedClicks(long observedBefore, int applied) {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null && session.isOpen() && applied > 0) {
         session.submit(context -> {
            context.limits().reconcileDispatchedClicks(observedBefore, applied);
            return null;
         });
      }
   }

   public static CompletableFuture<Void> beginTick() {
      NetworkThreadExecutor.Session session;
      try {
         session = requireSession();
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }

      return session.submit(context -> {
         session.swapManager().beginTick(context);
         return null;
      });
   }

   public static void syncClientSelection(class_310 client) {
      Objects.requireNonNull(client, "client");
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null && session.isOpen()) {
         session.swapManager().syncClientSelection(client);
      }
   }

   public static CompletableFuture<Void> execute(NetworkAction action) {
      Objects.requireNonNull(action, "action");
      return submit(context -> {
         try {
            action.run(context);
            return null;
         } catch (RuntimeException | Error throwable) {
            throw throwable;
         } catch (Exception exception) {
            throw new NetworkThreadExecutor.NetworkActionException(exception);
         }
      });
   }

   public static void executeNow(NetworkAction action) {
      Objects.requireNonNull(action, "action");
      NetworkThreadExecutor.Session session = requireSession();
      if (!session.inEventLoop()) {
         throw new IllegalStateException("Immediate game action requires the network thread");
      }

      try {
         action.run(session.context());
      } catch (RuntimeException | Error throwable) {
         throw throwable;
      } catch (Exception exception) {
         throw new NetworkThreadExecutor.NetworkActionException(exception);
      }
   }

   public static <T> CompletableFuture<T> submit(Function<NetworkActionContext, T> action) {
      Objects.requireNonNull(action, "action");

      NetworkThreadExecutor.Session session;
      try {
         session = requireSession();
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }

      return session.submit(action);
   }

   public static int reserveSequence() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      return session == null ? -1 : session.nextSequence();
   }

   public static void resetSequence() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null) {
         session.resetSequence();
      }
   }

   public static void observeOutbound(class_2535 connection, class_2596<?> packet) {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null && session.connection() == connection) {
         session.limiter().observeOutbound(packet);
         if (packet instanceof class_2868 selected) {
            session.slotManager().observeSelectedSlot(selected.method_12442());
         }

         if (packet instanceof class_2793 accepted) {
            session.observeTeleportAccepted(accepted.method_12086());
         } else if (packet instanceof class_2886 use) {
            session.observeSequence(use.method_42081());
         } else if (packet instanceof class_2885 useOn) {
            session.observeSequence(useOn.method_42080());
         } else if (packet instanceof class_2846 playerAction) {
            session.observeSequence(playerAction.method_42079());
         }
      }
   }

   public static void observeInbound(class_2535 connection, class_2596<?> packet) {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null && session.connection() == connection) {
         observeInbound(session, packet);
      }
   }

   private static void observeInbound(NetworkThreadExecutor.Session session, class_2596<?> packet) {
      if (packet instanceof class_8042 bundle) {
         for (class_2596<?> nested : bundle.method_48324()) {
            observeInbound(session, nested);
         }
      } else {
         session.limiter().observeInbound(packet);
         if (packet instanceof class_2708 position) {
            boolean absolute = !position.comp_3229().contains(class_2709.field_12400)
               && !position.comp_3229().contains(class_2709.field_12398)
               && !position.comp_3229().contains(class_2709.field_12403);
            session.observeTeleport(position.comp_3133(), absolute ? position.comp_3228().comp_3148() : null);
         } else if (packet instanceof class_2735 selected) {
            session.slotManager().observeSelectedSlot(selected.comp_3325());
         }
      }
   }

   public static void initializeSelectedSlot(int slot) {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null) {
         session.slotManager().initializeSelectedSlot(slot);
      }
   }

   private static NetworkThreadExecutor.Session requireSession() {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null && session.isOpen()) {
         return session;
      } else {
         throw new IllegalStateException("No active play connection");
      }
   }

   private static final class NetworkActionException extends RuntimeException {
      private NetworkActionException(Exception cause) {
         super(cause);
      }
   }

   public static final class Session {
      private final class_2535 connection;
      private final EventLoop eventLoop;
      private final AtomicBoolean open = new AtomicBoolean(true);
      private final AtomicInteger sequence = new AtomicInteger();
      private int pendingTeleportId = -1;
      private class_243 pendingTeleportPosition;
      private long pendingTeleportDeadline;
      private final SlotManager slotManager;
      private final SwapManager swapManager;
      private final PlacementManager.SessionState placementManager;
      private final PacketLimiter limiter = new PacketLimiter();
      private final NetworkActionContext context;

      private Session(class_634 listener) {
         Objects.requireNonNull(listener, "listener");
         this.connection = Objects.requireNonNull(listener.method_48296(), "connection");
         if (this.connection instanceof NetworkConnectionAccess access) {
            this.eventLoop = Objects.requireNonNull(access.homovore$getEventLoop(), "connection event loop");
            this.slotManager = new SlotManager(this);
            this.swapManager = new SwapManager(this, this.slotManager);
            this.placementManager = Homovore2Client.placements().createSessionState();
            this.context = new NetworkActionContext(this);
         } else {
            throw new IllegalStateException("Connection mixin was not applied");
         }
      }

      public class_2535 connection() {
         return this.connection;
      }

      public SlotManager slotManager() {
         return this.slotManager;
      }

      public SwapManager swapManager() {
         return this.swapManager;
      }

      public PlacementManager.SessionState placementManager() {
         return this.placementManager;
      }

      public PacketLimiter limiter() {
         return this.limiter;
      }

      NetworkActionContext context() {
         return this.context;
      }

      public boolean inEventLoop() {
         return this.eventLoop.inEventLoop();
      }

      public boolean isOpen() {
         return this.open.get() && this.connection.method_10758();
      }

      boolean awaitingPositionConfirmation() {
         if (this.pendingTeleportId < 0) {
            return false;
         } else if (System.nanoTime() >= this.pendingTeleportDeadline) {
            this.clearPendingTeleport();
            return false;
         } else {
            return true;
         }
      }

      class_243 pendingTeleportPosition() {
         return this.pendingTeleportPosition;
      }

      public void ensureOpen() {
         if (!this.isOpen() || NetworkThreadExecutor.CURRENT.get() != this) {
            throw new IllegalStateException("The play connection changed while an action was pending");
         }
      }

      int nextSequence() {
         return this.sequence.incrementAndGet();
      }

      void resetSequence() {
         this.sequence.set(0);
      }

      void observeSequence(int seen) {
         this.sequence.accumulateAndGet(seen, Math::max);
      }

      void observeTeleport(int id, class_243 absolutePosition) {
         this.pendingTeleportId = id;
         this.pendingTeleportPosition = absolutePosition;
         this.pendingTeleportDeadline = System.nanoTime() + 200000000L;
      }

      void observeTeleportAccepted(int id) {
         if (this.pendingTeleportId == id) {
            this.clearPendingTeleport();
         }
      }

      private void clearPendingTeleport() {
         this.pendingTeleportId = -1;
         this.pendingTeleportPosition = null;
         this.pendingTeleportDeadline = 0L;
      }

      public <T> CompletableFuture<T> submit(Function<NetworkActionContext, T> action) {
         CompletableFuture<T> future = new CompletableFuture<>();
         Runnable task = () -> {
            try {
               this.ensureOpen();
               future.complete(action.apply(this.context));
            } catch (Throwable throwable) {
               future.completeExceptionally(throwable);
            }
         };
         if (this.inEventLoop()) {
            task.run();
         } else {
            try {
               this.eventLoop.execute(task);
            } catch (RuntimeException exception) {
               future.completeExceptionally(exception);
            }
         }

         return future;
      }

      void schedule(NetworkAction action, long delayNanos) {
         Objects.requireNonNull(action, "action");
         this.eventLoop.schedule(() -> {
            try {
               if (!this.isOpen() || NetworkThreadExecutor.CURRENT.get() != this) {
                  return;
               }

               action.run(this.context);
            } catch (Throwable throwable) {
               Homovore2.LOGGER.error("Scheduled network action failed", throwable);
            }
         }, Math.max(0L, delayNanos), TimeUnit.NANOSECONDS);
      }

      void close() {
         this.open.set(false);
      }
   }
}
