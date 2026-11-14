package dev.leonetic.v2.util.network;

import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.manager.PlacementManager;
import dev.leonetic.v2.util.inventory.SlotManager;
import dev.leonetic.v2.util.inventory.SwapManager;
import io.netty.channel.EventLoop;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.util.math.Vec3d;

public final class NetworkThreadExecutor {
   private static final long TELEPORT_CONFIRMATION_DEADLINE_NANOS = 200000000L;
   private static final AtomicReference<NetworkThreadExecutor.Session> CURRENT = new AtomicReference<>();

   private NetworkThreadExecutor() {
      throw new AssertionError();
   }

   public static void bind(ClientPlayNetworkHandler listener) {
      Objects.requireNonNull(listener, "listener");
      NetworkThreadExecutor.Session replacement = new NetworkThreadExecutor.Session(listener);
      NetworkThreadExecutor.Session previous = CURRENT.getAndSet(replacement);
      if (previous != null) {
         previous.close();
      }
   }

   public static void unbind(ClientPlayNetworkHandler listener) {
      if (listener != null) {
         NetworkThreadExecutor.Session session = CURRENT.get();
         if (session != null && session.connection() == listener.getConnection() && CURRENT.compareAndSet(session, null)) {
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

   public static boolean isActiveConnection(ClientConnection connection) {
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

   public static void syncClientSelection(MinecraftClient client) {
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

   public static void observeOutbound(ClientConnection connection, Packet<?> packet) {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null && session.connection() == connection) {
         session.limiter().observeOutbound(packet);
         if (packet instanceof UpdateSelectedSlotC2SPacket selected) {
            session.slotManager().observeSelectedSlot(selected.getSelectedSlot());
         }

         if (packet instanceof TeleportConfirmC2SPacket accepted) {
            session.observeTeleportAccepted(accepted.getTeleportId());
         } else if (packet instanceof PlayerInteractItemC2SPacket use) {
            session.observeSequence(use.getSequence());
         } else if (packet instanceof PlayerInteractBlockC2SPacket useOn) {
            session.observeSequence(useOn.getSequence());
         } else if (packet instanceof PlayerActionC2SPacket playerAction) {
            session.observeSequence(playerAction.getSequence());
         }
      }
   }

   public static void observeInbound(ClientConnection connection, Packet<?> packet) {
      NetworkThreadExecutor.Session session = CURRENT.get();
      if (session != null && session.connection() == connection) {
         observeInbound(session, packet);
      }
   }

   private static void observeInbound(NetworkThreadExecutor.Session session, Packet<?> packet) {
      if (packet instanceof BundleS2CPacket bundle) {
         for (Packet<?> nested : bundle.getPackets()) {
            observeInbound(session, nested);
         }
      } else {
         session.limiter().observeInbound(packet);
         if (packet instanceof PlayerPositionLookS2CPacket position) {
            boolean absolute = !position.relatives().contains(PositionFlag.X)
               && !position.relatives().contains(PositionFlag.Y)
               && !position.relatives().contains(PositionFlag.Z);
            session.observeTeleport(position.teleportId(), absolute ? position.change().position() : null);
         } else if (packet instanceof UpdateSelectedSlotS2CPacket selected) {
            session.slotManager().observeSelectedSlot(selected.slot());
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
      private final ClientConnection connection;
      private final EventLoop eventLoop;
      private final AtomicBoolean open = new AtomicBoolean(true);
      private final AtomicInteger sequence = new AtomicInteger();
      private int pendingTeleportId = -1;
      private Vec3d pendingTeleportPosition;
      private long pendingTeleportDeadline;
      private final SlotManager slotManager;
      private final SwapManager swapManager;
      private final PlacementManager.SessionState placementManager;
      private final PacketLimiter limiter = new PacketLimiter();
      private final NetworkActionContext context;

      private Session(ClientPlayNetworkHandler listener) {
         Objects.requireNonNull(listener, "listener");
         this.connection = Objects.requireNonNull(listener.getConnection(), "connection");
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

      public ClientConnection connection() {
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
         return this.open.get() && this.connection.isOpen();
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

      Vec3d pendingTeleportPosition() {
         return this.pendingTeleportPosition;
      }

      public void ensureOpen() {
         if (!this.isOpen() || NetworkThreadExecutor.CURRENT.get() != (Object)this) {
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

      void observeTeleport(int id, Vec3d absolutePosition) {
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
               if (!this.isOpen() || NetworkThreadExecutor.CURRENT.get() != (Object)this) {
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
