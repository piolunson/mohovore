package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.mixin.FireworkRocketEntityAccessor;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.class_10192;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1661;
import net.minecraft.class_1671;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2848;
import net.minecraft.class_9284;
import net.minecraft.class_9334;
import net.minecraft.class_2848.class_2849;

public final class AutoElytraModule extends Module {
   private static final double TAKEOFF_VELOCITY = 0.42;
   private static final int FIRE_TIMEOUT = 40;
   private static final int FRESH_LIFE_TICKS = 6;
   private static final int PEARL_COOLDOWN_TIMEOUT = 20;
   private static final double WALL_PEARL_PROBE = 0.05;
   private static final long WALL_PEARL_COLLISION_GRACE_MS = 150L;
   private static final double WALL_PEARL_FLOOR_CLEARANCE = 1.0E-4;
   public final Setting<AutoElytraModule.Takeoff> takeoff = this.mode("Takeoff", AutoElytraModule.Takeoff.VELOCITY)
      .describe("VELOCITY sets a jump's upward speed directly; VANILLA performs a real jump.");
   public final Setting<Integer> minBoostTicks = this.integer("MinBoostTicks", 7, 0, 20)
      .describe("Fires the next rocket once the current boost has this many ticks or fewer left.");
   public final Setting<Boolean> pauseOnEat = this.bool("PauseOnEat", true)
      .describe("Holds rockets back while you eat. A rocket has to take the main hand for its use packet, and that cancels the meal.");
   public final Setting<Boolean> wallPearl = this.bool("WallPearl", true)
      .describe("Silently throws a pearl where you are looking when you fly into a wall, corner, or ceiling, phased or not; floors are ignored.");
   public final Setting<Integer> wallPearlDelay = this.integer("WallPearlDelay", 400, 0, 3000)
      .describe("Waits until the collision has been held for this many milliseconds before throwing; a collision that breaks off resets the wait.")
      .visibleWhen(this.wallPearl::getValue);
   private final AtomicBoolean pending = new AtomicBoolean();
   private final AtomicBoolean fired = new AtomicBoolean();
   private final AtomicLong generation = new AtomicLong();
   private final AtomicBoolean pearlPending = new AtomicBoolean();
   private final AtomicBoolean pearlThrown = new AtomicBoolean();
   private final AtomicLong pearlGeneration = new AtomicLong();
   private AutoElytraModule.Phase phase = AutoElytraModule.Phase.EQUIP;
   private boolean weEquipped;
   private int stowedSlot = -1;
   private int ticksSinceRocket = Integer.MAX_VALUE;
   private boolean awaitingRocket;
   private int awaitingPearlCooldown;
   private long collisionHeldSince;
   private long collisionSeenAt;
   private boolean flightEstablished;

   public AutoElytraModule() {
      super("AutoElytra", Category.MOVEMENT, "Equips an elytra, takes off and keeps firing rockets; swaps back on disable.");
   }

   @Override
   public int getTickPriority() {
      return 80;
   }

   @Override
   public void onEnable() {
      this.phase = AutoElytraModule.Phase.EQUIP;
      this.weEquipped = false;
      this.stowedSlot = -1;
      this.resetRocket();
      this.resetWallPearl();
   }

   @Override
   public void onDisable() {
      this.resetRocket();
      this.resetWallPearl();
      if (mc.field_1724 != null && mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833)) {
         int slot = this.restoreSlot();
         if (slot >= 0) {
            InventoryUtil.swapWithEquipment(slot, class_1304.field_6174);
         }
      }

      this.weEquipped = false;
      this.stowedSlot = -1;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         switch (this.phase) {
            case EQUIP:
               this.tickEquip();
               break;
            case TAKEOFF:
               this.tickTakeoff();
               break;
            case FLY:
               this.tickFly();
         }

         this.tickWallPearl();
      }
   }

   private void tickEquip() {
      if (mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833)) {
         this.phase = AutoElytraModule.Phase.TAKEOFF;
      } else {
         Result elytra = InventoryUtil.find(class_1802.field_8833, InventoryUtil.MAIN_INVENTORY_SCOPE);
         if (elytra.found() && InventoryUtil.swapWithEquipment(elytra.slot(), class_1304.field_6174)) {
            this.stowedSlot = elytra.slot();
            this.weEquipped = true;
            this.phase = AutoElytraModule.Phase.TAKEOFF;
         }
      }
   }

   private void tickTakeoff() {
      if (mc.field_1724.method_6128()) {
         this.enterFlight();
      } else if (mc.field_1724.method_24828()) {
         switch ((AutoElytraModule.Takeoff)this.takeoff.getValue()) {
            case VELOCITY:
               class_243 velocity = mc.field_1724.method_18798();
               mc.field_1724.method_18800(velocity.field_1352, 0.42, velocity.field_1350);
               mc.field_1724.method_5728(true);
               break;
            case VANILLA:
               mc.field_1724.method_6043();
         }
      } else if (mc.field_1724.method_23668()) {
         class_2848 start = new class_2848(mc.field_1724, class_2849.field_12982);
         NetworkThreadExecutor.execute(context -> context.send(start));
         this.enterFlight();
      }
   }

   private void tickFly() {
      if (!mc.field_1724.method_6128()) {
         this.phase = AutoElytraModule.Phase.TAKEOFF;
      } else {
         if (this.fired.getAndSet(false)) {
            this.awaitingRocket = true;
            this.ticksSinceRocket = 0;
         }

         if (this.ticksSinceRocket < Integer.MAX_VALUE) {
            this.ticksSinceRocket++;
         }

         if (this.awaitingRocket) {
            if (!this.hasFreshAttachedRocket() && this.ticksSinceRocket < 40) {
               return;
            }

            this.awaitingRocket = false;
         }

         if (!this.pending.get() && !this.hasUsefulBoost() && (!this.pauseOnEat.getValue() || !this.isEatingOrStarting())) {
            this.fireRocket();
         }
      }
   }

   private void fireRocket() {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (snapshot.available() && snapshot.menuId() == 0 && !snapshot.deadOrDying()) {
         Result rocket = InventoryUtil.find(class_1802.field_8639, InventoryUtil.FULL_SCOPE);
         if (rocket.found() && useItemFits() && this.pending.compareAndSet(false, true)) {
            long submission = this.generation.get();
            boolean[] sent = new boolean[]{false};
            NetworkAction action = context -> {
               if (this.generation.get() == submission) {
                  sent[0] = context.useItem(rocket.hand());
               }
            };

            CompletableFuture<Boolean> submitted;
            try {
               submitted = InventoryUtil.withSwap(rocket, action);
            } catch (RuntimeException exception) {
               this.pending.set(false);
               return;
            }

            submitted.whenComplete((swapped, failure) -> {
               if (failure == null && Boolean.TRUE.equals(swapped) && sent[0] && this.generation.get() == submission) {
                  this.fired.set(true);
               }

               this.pending.set(false);
            });
         }
      }
   }

   private void tickWallPearl() {
      long now = System.currentTimeMillis();
      if (this.phase == AutoElytraModule.Phase.EQUIP || mc.field_1724.method_24828()) {
         this.flightEstablished = false;
         this.collisionSeenAt = 0L;
      }

      boolean directCollision = this.flightEstablished && this.hasWallPearlCollision();
      if (directCollision) {
         this.collisionSeenAt = now;
      }

      boolean obstructed = this.flightEstablished && this.collisionSeenAt != 0L && now - this.collisionSeenAt <= 150L;
      if (!obstructed) {
         this.collisionHeldSince = 0L;
      } else if (this.collisionHeldSince == 0L) {
         this.collisionHeldSince = now;
      }

      if (this.pearlThrown.getAndSet(false)) {
         this.awaitingPearlCooldown = 20;
      }

      if (this.awaitingPearlCooldown > 0) {
         if (!mc.field_1724.method_7357().method_7904(class_1802.field_8634.method_7854())) {
            this.awaitingPearlCooldown--;
            return;
         }

         this.awaitingPearlCooldown = 0;
      }

      if (this.wallPearl.getValue() && obstructed && !this.pearlPending.get() && now - this.collisionHeldSince >= this.wallPearlDelay.getValue().intValue()) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (snapshot.available() && snapshot.menuId() == 0 && !snapshot.deadOrDying()) {
            Result pearl = InventoryUtil.find(class_1802.field_8634, InventoryUtil.FULL_SCOPE);
            if (pearl.found() && !mc.field_1724.method_7357().method_7904(pearl.stack()) && useItemFits()) {
               Homovore2Client.rotations().reserveSilent(this);
               if (!Homovore2Client.rotations().silentReservedByOther(this)) {
                  if (!this.pearlPending.compareAndSet(false, true)) {
                     Homovore2Client.rotations().releaseSilent(this);
                  } else {
                     long submission = this.pearlGeneration.get();
                     RotationManager.Angles angles = new RotationManager.Angles(snapshot.yaw(), snapshot.pitch());
                     boolean[] sent = new boolean[]{false};
                     NetworkAction action = context -> {
                        if (this.pearlGeneration.get() == submission) {
                           try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
                                 .beginSilentNow(context, snapshot, angles, snapshot.horizontalCollision(), this)) {
                              if (rotation.applied()) {
                                 sent[0] = context.useItem(pearl.hand(), angles.yaw(), angles.pitch());
                              }
                           }
                        }
                     };

                     CompletableFuture<Boolean> submitted;
                     try {
                        submitted = InventoryUtil.withSwap(pearl, action);
                     } catch (RuntimeException exception) {
                        this.pearlPending.set(false);
                        Homovore2Client.rotations().releaseSilent(this);
                        return;
                     }

                     submitted.whenComplete((swapped, failure) -> {
                        if (failure == null && Boolean.TRUE.equals(swapped) && sent[0] && this.pearlGeneration.get() == submission) {
                           this.pearlThrown.set(true);
                        }

                        this.pearlPending.set(false);
                        Homovore2Client.rotations().releaseSilent(this);
                     });
                  }
               }
            }
         }
      }
   }

   private boolean hasWallPearlCollision() {
      if (mc.field_1724.field_5976 || mc.field_1724.field_5992 && !mc.field_1724.field_36331) {
         return true;
      }

      class_238 box = mc.field_1724.method_5829();
      class_238 probe = new class_238(
         box.field_1323 - 0.05, box.field_1322 + 1.0E-4, box.field_1321 - 0.05, box.field_1320 + 0.05, box.field_1325 + 0.05, box.field_1324 + 0.05
      );
      return mc.field_1687.method_20812(mc.field_1724, probe).iterator().hasNext();
   }

   private boolean isEatingOrStarting() {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!snapshot.available()) {
         return false;
      } else {
         class_1799 held = heldItem(snapshot);
         if (snapshot.usingItem()) {
            return (snapshot.usingMainHand() ? held : snapshot.offhand()).method_57826(class_9334.field_50075);
         } else {
            return !mc.field_1690.field_1904.method_1434()
               ? false
               : held.method_57826(class_9334.field_50075) || snapshot.offhand().method_57826(class_9334.field_50075);
         }
      }
   }

   private static class_1799 heldItem(InventorySnapshot snapshot) {
      int served = NetworkThreadExecutor.selectedSlot();
      return snapshot.item(class_1661.method_7380(served) ? served : snapshot.selectedSlot());
   }

   private boolean hasFreshAttachedRocket() {
      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1671 rocket && attachedToPlayer(rocket) && ((FireworkRocketEntityAccessor)rocket).homovore$getLife() <= 6) {
            return true;
         }
      }

      return false;
   }

   private boolean hasUsefulBoost() {
      int bestRemaining = Integer.MIN_VALUE;

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1671 rocket && attachedToPlayer(rocket)) {
            int remaining = minimumLifetime(rocket) - ((FireworkRocketEntityAccessor)rocket).homovore$getLife();
            bestRemaining = Math.max(bestRemaining, remaining);
         }
      }

      return bestRemaining > this.minBoostTicks.getValue();
   }

   private static boolean attachedToPlayer(class_1671 rocket) {
      return ((FireworkRocketEntityAccessor)rocket).homovore$getAttachedToEntity() == mc.field_1724;
   }

   private static int minimumLifetime(class_1671 rocket) {
      class_9284 fireworks = (class_9284)rocket.method_7495().method_58694(class_9334.field_49616);
      int flight = fireworks != null ? fireworks.comp_2391() : 1;
      return 10 * (flight + 1);
   }

   private static boolean useItemFits() {
      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      if (limiter == null) {
         return false;
      }

      PacketLimiter.Snapshot budget = limiter.snapshot();
      return budget.useItemLimit() <= 0 || budget.useItemUsed() < budget.useItemLimit();
   }

   private int restoreSlot() {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!snapshot.available()) {
         return -1;
      }

      if (this.weEquipped && this.stowedSlot >= 0 && this.stowedSlot < 36) {
         class_1799 stowed = snapshot.item(this.stowedSlot);
         if (stowed.method_7960() || isChestplate(stowed)) {
            return this.stowedSlot;
         }
      }

      Result chestplate = InventoryUtil.find(AutoElytraModule::isChestplate, InventoryUtil.MAIN_INVENTORY_SCOPE);
      return chestplate.found() ? chestplate.slot() : -1;
   }

   private static boolean isChestplate(class_1799 stack) {
      if (stack.method_31574(class_1802.field_8833)) {
         return false;
      }

      class_10192 equippable = (class_10192)stack.method_58694(class_9334.field_54196);
      return equippable != null && equippable.comp_3174() == class_1304.field_6174;
   }

   private void enterFlight() {
      this.phase = AutoElytraModule.Phase.FLY;
      this.flightEstablished = true;
      this.resetRocket();
   }

   private void resetRocket() {
      this.generation.incrementAndGet();
      this.fired.set(false);
      this.pending.set(false);
      this.awaitingRocket = false;
      this.ticksSinceRocket = Integer.MAX_VALUE;
   }

   private void resetWallPearl() {
      this.pearlGeneration.incrementAndGet();
      this.pearlThrown.set(false);
      this.pearlPending.set(false);
      Homovore2Client.rotations().releaseSilent(this);
      this.collisionHeldSince = 0L;
      this.collisionSeenAt = 0L;
      this.flightEstablished = false;
      this.awaitingPearlCooldown = 0;
   }

   private enum Phase {
      EQUIP,
      TAKEOFF,
      FLY;
   }

   public enum Takeoff {
      VELOCITY,
      VANILLA;
   }
}
