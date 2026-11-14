package dev.leonetic.manager;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.mixin.ClientInputAccessor;
import dev.leonetic.mixin.LocalPlayerAccessor;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.NetworkActionContext;
import dev.leonetic.util.network.NetworkThreadExecutor;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

public final class RotationManager {
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final Object requestLock = new Object();
   private final AtomicReference<RotationManager.Angles> serverRotation = new AtomicReference<>();
   private final AtomicReference<RotationManager.Angles> restoreRotation = new AtomicReference<>();
   private boolean silentUsedThisTick;
   private boolean tickEndSent;
   private final AtomicReference<Object> silentReservation = new AtomicReference<>();
   private volatile boolean locked;
   private RotationManager.MotionRequest pendingMotion;
   private RotationManager.ActiveMotion activeMotion;
   private RotationManager.Angles previousRenderRotation;
   private RotationManager.Angles renderRotation;
   private boolean renderRotationActive;

   public RotationManager() {
      Homovore2Client.EVENT_BUS.register(this);
   }

   public void motion(RotationManager.Angles angles, Runnable action) {
      this.motion(angles, 0, action);
   }

   public void motion(RotationManager.Angles angles, int priority, Runnable action) {
      Objects.requireNonNull(angles, "angles");
      this.requireClientThread();
      if (!this.locked) {
         synchronized (this.requestLock) {
            if (this.pendingMotion == null || priority >= this.pendingMotion.priority()) {
               this.pendingMotion = new RotationManager.MotionRequest(angles, priority, action);
            }
         }
      }
   }

   public void motion(RotationManager.Angles angles) {
      this.motion(angles, 0, null);
   }

   public CompletableFuture<Void> silent(RotationManager.Angles angles, NetworkAction action) {
      Objects.requireNonNull(angles, "angles");
      Objects.requireNonNull(action, "action");
      if (this.locked) {
         return CompletableFuture.completedFuture(null);
      }

      InventorySnapshot snapshot;
      try {
         snapshot = this.prepareSilent(angles);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }

      return NetworkThreadExecutor.execute(context -> {
         try (RotationManager.SilentRotation rotation = this.beginSilentNow(context, snapshot, angles)) {
            if (rotation.applied()) {
               action.run(context);
            }
         }
      });
   }

   public CompletableFuture<Boolean> silentWithSwap(RotationManager.Angles angles, Result item, NetworkAction action) {
      Objects.requireNonNull(angles, "angles");
      Objects.requireNonNull(item, "item");
      Objects.requireNonNull(action, "action");
      if (this.locked) {
         return CompletableFuture.completedFuture(false);
      }

      InventorySnapshot snapshot;
      try {
         snapshot = this.prepareSilent(angles);
      } catch (RuntimeException exception) {
         return CompletableFuture.failedFuture(exception);
      }

      return NetworkThreadExecutor.submit(context -> {
         context.requireNetworkThread();
         return this.silentUsedThisTick ? false : InventoryUtil.withSwapNow(context, item, swappedContext -> {
            try (RotationManager.SilentRotation rotation = this.beginSilentNow(swappedContext, snapshot, angles)) {
               if (rotation.applied()) {
                  action.run(swappedContext);
               }
            }
         });
      });
   }

   public CompletableFuture<Void> beginTick() {
      return NetworkThreadExecutor.execute(context -> {
         context.requireNetworkThread();
         if (!this.tickEndSent) {
            this.silentUsedThisTick = false;
         }

         this.tickEndSent = false;
      });
   }

   public void reserveSilent(Object owner) {
      Objects.requireNonNull(owner, "owner");
      this.requireClientThread();
      this.silentReservation.compareAndSet(null, owner);
   }

   public void preemptSilent(Object owner) {
      Objects.requireNonNull(owner, "owner");
      this.requireClientThread();
      this.silentReservation.set(owner);
   }

   public void releaseSilent(Object owner) {
      if (owner != null) {
         this.silentReservation.compareAndSet(owner, null);
      }
   }

   public boolean silentReservedByOther(Object owner) {
      Object reserved = this.silentReservation.get();
      return reserved != null && reserved != owner;
   }

   public RotationManager.SilentRotation beginSilentNow(NetworkActionContext context, InventorySnapshot snapshot, RotationManager.Angles angles) {
      return this.beginSilentNow(context, snapshot, angles, snapshot.horizontalCollision());
   }

   public RotationManager.SilentRotation beginSilentNow(
      NetworkActionContext context, InventorySnapshot snapshot, RotationManager.Angles angles, boolean horizontalCollision
   ) {
      return this.beginSilentNow(context, snapshot, angles, horizontalCollision, null);
   }

   public RotationManager.SilentRotation beginSilentNow(
      NetworkActionContext context, InventorySnapshot snapshot, RotationManager.Angles angles, boolean horizontalCollision, Object owner
   ) {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(angles, "angles");
      context.requireNetworkThread();
      if (snapshot.available() && !this.silentUsedThisTick && !this.locked && !this.silentReservedByOther(owner)) {
         this.silentUsedThisTick = true;
         context.send(silentPacket(snapshot, angles, horizontalCollision));
         return new RotationManager.SilentRotation(angles, true);
      } else {
         return new RotationManager.SilentRotation(angles, false);
      }
   }

   public void applySilentRestoration() {
      this.requireClientThread();
      RotationManager.Angles angles = this.restoreRotation.getAndSet(null);
      ClientPlayerEntity player = this.mc.player;
      if (angles != null && player != null) {
         LocalPlayerAccessor accessor = (LocalPlayerAccessor)player;
         accessor.homovore$setPositionReminder(20);
         accessor.homovore$setYRotLast(angles.yaw());
         accessor.homovore$setXRotLast(angles.pitch());
      }
   }

   public CompletableFuture<Void> silent(RotationManager.Angles angles) {
      return this.silent(angles, context -> {});
   }

   public RotationManager.Angles serverRotation() {
      RotationManager.Angles rotation = this.serverRotation.get();
      if (rotation != null) {
         return rotation;
      }

      InventorySnapshot snapshot = InventoryUtil.snapshot();
      return new RotationManager.Angles(snapshot.yaw(), snapshot.pitch());
   }

   public RotationManager.Angles movementRotation() {
      RotationManager.ActiveMotion active = this.activeMotion;
      return active == null ? null : active.request.angles();
   }

   public RotationManager.Angles lerpedRenderRotation(float partialTick) {
      if (this.renderRotationActive && this.renderRotation != null) {
         RotationManager.Angles previous = this.previousRenderRotation == null ? this.renderRotation : this.previousRenderRotation;
         return new RotationManager.Angles(
            MathHelper.lerpAngleDegrees(partialTick, previous.yaw(), this.renderRotation.yaw()),
            MathHelper.lerp(partialTick, previous.pitch(), this.renderRotation.pitch())
         );
      } else {
         return null;
      }
   }

   public void lock() {
      this.requireClientThread();
      this.locked = true;
   }

   public boolean isLocked() {
      return this.locked;
   }

   public void beginMovementUpdate(ClientPlayerEntity player) {
      if (this.activeMotion == null) {
         if (this.locked) {
            synchronized (this.requestLock) {
               this.pendingMotion = null;
            }

            this.updateRenderRotation(new RotationManager.Angles(player.getYaw(), player.getPitch()), false);
         } else {
            RotationManager.MotionRequest request;
            synchronized (this.requestLock) {
               request = this.pendingMotion;
               this.pendingMotion = null;
            }

            if (request == null) {
               this.updateRenderRotation(new RotationManager.Angles(player.getYaw(), player.getPitch()), false);
            } else {
               RotationManager.Angles visibleRotation = new RotationManager.Angles(player.getYaw(), player.getPitch());
               this.activeMotion = new RotationManager.ActiveMotion(request, visibleRotation);
               this.updateRenderRotation(request.angles(), true);
               player.setYaw(request.angles().yaw());
               player.setPitch(request.angles().pitch());
            }
         }
      }
   }

   public void fixMovementInput(ClientPlayerEntity player) {
      RotationManager.ActiveMotion active = this.activeMotion;
      if (active != null) {
         active.visibleInput = player.input.playerInput;
         active.visibleMoveVector = player.input.getMovementInput();
         PlayerInput fixedInput = fixInput(active.visibleInput, active.visibleRotation.yaw(), active.request.angles().yaw());
         player.input.playerInput = fixedInput;
         ((ClientInputAccessor)player.input).homovore$setMoveVector(moveVector(fixedInput));
      }
   }

   public void endMovementUpdate(ClientPlayerEntity player) {
      this.locked = false;
      RotationManager.ActiveMotion active = this.activeMotion;
      if (active != null) {
         try {
            Runnable action = active.request.action();
            if (action != null) {
               action.run();
            }
         } finally {
            player.setYaw(active.visibleRotation.yaw());
            player.setPitch(active.visibleRotation.pitch());
            if (active.visibleInput != null) {
               player.input.playerInput = active.visibleInput;
            }

            if (active.visibleMoveVector != null) {
               ((ClientInputAccessor)player.input).homovore$setMoveVector(active.visibleMoveVector);
            }

            this.activeMotion = null;
         }
      }
   }

   public void reset() {
      synchronized (this.requestLock) {
         this.pendingMotion = null;
      }

      this.locked = false;
      this.activeMotion = null;
      this.previousRenderRotation = null;
      this.renderRotation = null;
      this.renderRotationActive = false;
      this.serverRotation.set(null);
      this.restoreRotation.set(null);
      this.silentReservation.set(null);
   }

   @Subscribe(priority = -1000)
   public void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ClientTickEndC2SPacket && !event.isCancelled()) {
         this.silentUsedThisTick = false;
         this.tickEndSent = true;
      } else if (event.getPacket() instanceof PlayerMoveC2SPacket packet && packet.changesLook() && !event.isCancelled()) {
         RotationManager.Angles previous = this.serverRotation.get();
         float fallbackYaw = previous == null ? 0.0F : previous.yaw();
         float fallbackPitch = previous == null ? 0.0F : previous.pitch();
         this.serverRotation.set(new RotationManager.Angles(packet.getYaw(fallbackYaw), packet.getPitch(fallbackPitch)));
      }
   }

   private static PlayerInput fixInput(PlayerInput input, float visibleYaw, float serverYaw) {
      int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
      int sideways = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
      if (forward == 0 && sideways == 0) {
         return input;
      }

      float delta = (visibleYaw - serverYaw) * (float) (Math.PI / 180.0);
      int fixedSideways = Math.round(sideways * MathHelper.cos(delta) - forward * MathHelper.sin(delta));
      int fixedForward = Math.round(forward * MathHelper.cos(delta) + sideways * MathHelper.sin(delta));
      fixedSideways = MathHelper.clamp(fixedSideways, -1, 1);
      fixedForward = MathHelper.clamp(fixedForward, -1, 1);
      return new PlayerInput(fixedForward > 0, fixedForward < 0, fixedSideways > 0, fixedSideways < 0, input.jump(), input.sneak(), input.sprint());
   }

   private static Vec2f moveVector(PlayerInput input) {
      int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
      int sideways = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
      return new Vec2f(sideways, forward).normalize();
   }

   private void updateRenderRotation(RotationManager.Angles rotation, boolean active) {
      this.previousRenderRotation = this.renderRotation;
      this.renderRotation = rotation;
      this.renderRotationActive = active;
   }

   private void requireClientThread() {
      if (!this.mc.isOnThread()) {
         throw new IllegalStateException("Rotations must be requested from the client thread");
      }
   }

   private InventorySnapshot prepareSilent(RotationManager.Angles angles) {
      this.requireClientThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!snapshot.available()) {
         throw new IllegalStateException("No local-player snapshot");
      } else {
         ClientPlayerEntity player = this.mc.player;
         if (player == null) {
            throw new IllegalStateException("No local player");
         } else if (!NetworkThreadExecutor.isConnected()) {
            throw new IllegalStateException("No active play connection");
         } else {
            return snapshot;
         }
      }
   }

   private static PlayerMoveC2SPacket silentPacket(InventorySnapshot snapshot, RotationManager.Angles angles, boolean horizontalCollision) {
      return new Full(snapshot.x(), snapshot.y(), snapshot.z(), angles.yaw(), angles.pitch(), snapshot.onGround(), horizontalCollision);
   }

   private static final class ActiveMotion {
      private final RotationManager.MotionRequest request;
      private final RotationManager.Angles visibleRotation;
      private PlayerInput visibleInput;
      private Vec2f visibleMoveVector;

      private ActiveMotion(RotationManager.MotionRequest request, RotationManager.Angles visibleRotation) {
         this.request = request;
         this.visibleRotation = visibleRotation;
      }
   }

   public record Angles(float yaw, float pitch) {
      public Angles(float yaw, float pitch) {
         if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
            this.yaw = yaw;
            this.pitch = pitch;
         } else {
            throw new IllegalArgumentException("Rotation angles must be finite");
         }
      }
   }

   private record MotionRequest(RotationManager.Angles angles, int priority, Runnable action) {
   }

   public final class SilentRotation implements AutoCloseable {
      private final RotationManager.Angles angles;
      private final boolean applied;
      private boolean closed;

      private SilentRotation(RotationManager.Angles angles, boolean applied) {
         this.angles = angles;
         this.applied = applied;
      }

      public boolean applied() {
         return this.applied;
      }

      @Override
      public void close() {
         if (!this.closed) {
            this.closed = true;
            if (this.applied) {
               RotationManager.this.restoreRotation.set(this.angles);
            }
         }
      }
   }
}
