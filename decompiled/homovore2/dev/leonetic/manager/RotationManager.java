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
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_2828;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_746;
import net.minecraft.class_9836;
import net.minecraft.class_2828.class_2830;

public final class RotationManager {
   private final class_310 mc = class_310.method_1551();
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
      class_746 player = this.mc.field_1724;
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
            class_3532.method_17821(partialTick, previous.yaw(), this.renderRotation.yaw()),
            class_3532.method_16439(partialTick, previous.pitch(), this.renderRotation.pitch())
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

   public void beginMovementUpdate(class_746 player) {
      if (this.activeMotion == null) {
         if (this.locked) {
            synchronized (this.requestLock) {
               this.pendingMotion = null;
            }

            this.updateRenderRotation(new RotationManager.Angles(player.method_36454(), player.method_36455()), false);
         } else {
            RotationManager.MotionRequest request;
            synchronized (this.requestLock) {
               request = this.pendingMotion;
               this.pendingMotion = null;
            }

            if (request == null) {
               this.updateRenderRotation(new RotationManager.Angles(player.method_36454(), player.method_36455()), false);
            } else {
               RotationManager.Angles visibleRotation = new RotationManager.Angles(player.method_36454(), player.method_36455());
               this.activeMotion = new RotationManager.ActiveMotion(request, visibleRotation);
               this.updateRenderRotation(request.angles(), true);
               player.method_36456(request.angles().yaw());
               player.method_36457(request.angles().pitch());
            }
         }
      }
   }

   public void fixMovementInput(class_746 player) {
      RotationManager.ActiveMotion active = this.activeMotion;
      if (active != null) {
         active.visibleInput = player.field_3913.field_54155;
         active.visibleMoveVector = player.field_3913.method_3128();
         class_10185 fixedInput = fixInput(active.visibleInput, active.visibleRotation.yaw(), active.request.angles().yaw());
         player.field_3913.field_54155 = fixedInput;
         ((ClientInputAccessor)player.field_3913).homovore$setMoveVector(moveVector(fixedInput));
      }
   }

   public void endMovementUpdate(class_746 player) {
      this.locked = false;
      RotationManager.ActiveMotion active = this.activeMotion;
      if (active != null) {
         try {
            Runnable action = active.request.action();
            if (action != null) {
               action.run();
            }
         } finally {
            player.method_36456(active.visibleRotation.yaw());
            player.method_36457(active.visibleRotation.pitch());
            if (active.visibleInput != null) {
               player.field_3913.field_54155 = active.visibleInput;
            }

            if (active.visibleMoveVector != null) {
               ((ClientInputAccessor)player.field_3913).homovore$setMoveVector(active.visibleMoveVector);
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
      if (event.getPacket() instanceof class_9836 && !event.isCancelled()) {
         this.silentUsedThisTick = false;
         this.tickEndSent = true;
      } else if (event.getPacket() instanceof class_2828 packet && packet.method_36172() && !event.isCancelled()) {
         RotationManager.Angles previous = this.serverRotation.get();
         float fallbackYaw = previous == null ? 0.0F : previous.yaw();
         float fallbackPitch = previous == null ? 0.0F : previous.pitch();
         this.serverRotation.set(new RotationManager.Angles(packet.method_12271(fallbackYaw), packet.method_12270(fallbackPitch)));
      }
   }

   private static class_10185 fixInput(class_10185 input, float visibleYaw, float serverYaw) {
      int forward = (input.comp_3159() ? 1 : 0) - (input.comp_3160() ? 1 : 0);
      int sideways = (input.comp_3161() ? 1 : 0) - (input.comp_3162() ? 1 : 0);
      if (forward == 0 && sideways == 0) {
         return input;
      }

      float delta = (visibleYaw - serverYaw) * (float) (Math.PI / 180.0);
      int fixedSideways = Math.round(sideways * class_3532.method_15362(delta) - forward * class_3532.method_15374(delta));
      int fixedForward = Math.round(forward * class_3532.method_15362(delta) + sideways * class_3532.method_15374(delta));
      fixedSideways = class_3532.method_15340(fixedSideways, -1, 1);
      fixedForward = class_3532.method_15340(fixedForward, -1, 1);
      return new class_10185(fixedForward > 0, fixedForward < 0, fixedSideways > 0, fixedSideways < 0, input.comp_3163(), input.comp_3164(), input.comp_3165());
   }

   private static class_241 moveVector(class_10185 input) {
      int forward = (input.comp_3159() ? 1 : 0) - (input.comp_3160() ? 1 : 0);
      int sideways = (input.comp_3161() ? 1 : 0) - (input.comp_3162() ? 1 : 0);
      return new class_241(sideways, forward).method_35581();
   }

   private void updateRenderRotation(RotationManager.Angles rotation, boolean active) {
      this.previousRenderRotation = this.renderRotation;
      this.renderRotation = rotation;
      this.renderRotationActive = active;
   }

   private void requireClientThread() {
      if (!this.mc.method_18854()) {
         throw new IllegalStateException("Rotations must be requested from the client thread");
      }
   }

   private InventorySnapshot prepareSilent(RotationManager.Angles angles) {
      this.requireClientThread();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!snapshot.available()) {
         throw new IllegalStateException("No local-player snapshot");
      } else {
         class_746 player = this.mc.field_1724;
         if (player == null) {
            throw new IllegalStateException("No local player");
         } else if (!NetworkThreadExecutor.isConnected()) {
            throw new IllegalStateException("No active play connection");
         } else {
            return snapshot;
         }
      }
   }

   private static class_2828 silentPacket(InventorySnapshot snapshot, RotationManager.Angles angles, boolean horizontalCollision) {
      return new class_2830(snapshot.x(), snapshot.y(), snapshot.z(), angles.yaw(), angles.pitch(), snapshot.onGround(), horizontalCollision);
   }

   private static final class ActiveMotion {
      private final RotationManager.MotionRequest request;
      private final RotationManager.Angles visibleRotation;
      private class_10185 visibleInput;
      private class_241 visibleMoveVector;

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
