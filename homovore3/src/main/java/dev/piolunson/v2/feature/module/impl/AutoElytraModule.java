package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.manager.RotationManager;
import dev.piolunson.v2.mixin.FireworkRocketEntityAccessor;
import dev.piolunson.v2.util.inventory.InventorySnapshot;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.inventory.Result;
import dev.piolunson.v2.util.network.NetworkAction;
import dev.piolunson.v2.util.network.NetworkThreadExecutor;
import dev.piolunson.v2.util.network.PacketLimiter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
      if (mc.player != null && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         int slot = this.restoreSlot();
         if (slot >= 0) {
            InventoryUtil.swapWithEquipment(slot, EquipmentSlot.CHEST);
         }
      }

      this.weEquipped = false;
      this.stowedSlot = -1;
   }

   @Override
   public void onTick() {
      if (mc.player != null && mc.world != null) {
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
      if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         this.phase = AutoElytraModule.Phase.TAKEOFF;
      } else {
         Result elytra = InventoryUtil.find(Items.ELYTRA, InventoryUtil.MAIN_INVENTORY_SCOPE);
         if (elytra.found() && InventoryUtil.swapWithEquipment(elytra.slot(), EquipmentSlot.CHEST)) {
            this.stowedSlot = elytra.slot();
            this.weEquipped = true;
            this.phase = AutoElytraModule.Phase.TAKEOFF;
         }
      }
   }

   private void tickTakeoff() {
      if (mc.player.isGliding()) {
         this.enterFlight();
      } else if (mc.player.isOnGround()) {
         switch ((AutoElytraModule.Takeoff)this.takeoff.getValue()) {
            case VELOCITY:
               Vec3d velocity = mc.player.getVelocity();
               mc.player.setVelocity(velocity.x, 0.42, velocity.z);
               mc.player.setSprinting(true);
               break;
            case VANILLA:
               mc.player.jump();
         }
      } else if (mc.player.checkGliding()) {
         ClientCommandC2SPacket start = new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING);
         NetworkThreadExecutor.execute(context -> context.send(start));
         this.enterFlight();
      }
   }

   private void tickFly() {
      if (!mc.player.isGliding()) {
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
         Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
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
      if (this.phase == AutoElytraModule.Phase.EQUIP || mc.player.isOnGround()) {
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
         if (!mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
            this.awaitingPearlCooldown--;
            return;
         }

         this.awaitingPearlCooldown = 0;
      }

      if (this.wallPearl.getValue() && obstructed && !this.pearlPending.get() && now - this.collisionHeldSince >= this.wallPearlDelay.getValue().intValue()) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (snapshot.available() && snapshot.menuId() == 0 && !snapshot.deadOrDying()) {
            Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
            if (pearl.found() && !mc.player.getItemCooldownManager().isCoolingDown(pearl.stack()) && useItemFits()) {
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
      if (mc.player.horizontalCollision || mc.player.verticalCollision && !mc.player.groundCollision) {
         return true;
      }

      Box box = mc.player.getBoundingBox();
      Box probe = new Box(box.minX - 0.05, box.minY + 1.0E-4, box.minZ - 0.05, box.maxX + 0.05, box.maxY + 0.05, box.maxZ + 0.05);
      return mc.world.getBlockCollisions(mc.player, probe).iterator().hasNext();
   }

   private boolean isEatingOrStarting() {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!snapshot.available()) {
         return false;
      } else {
         ItemStack held = heldItem(snapshot);
         if (snapshot.usingItem()) {
            return (snapshot.usingMainHand() ? held : snapshot.offhand()).contains(DataComponentTypes.FOOD);
         } else {
            return !mc.options.useKey.isPressed() ? false : held.contains(DataComponentTypes.FOOD) || snapshot.offhand().contains(DataComponentTypes.FOOD);
         }
      }
   }

   private static ItemStack heldItem(InventorySnapshot snapshot) {
      int served = NetworkThreadExecutor.selectedSlot();
      return snapshot.item(PlayerInventory.isValidHotbarIndex(served) ? served : snapshot.selectedSlot());
   }

   private boolean hasFreshAttachedRocket() {
      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof FireworkRocketEntity rocket && attachedToPlayer(rocket) && ((FireworkRocketEntityAccessor)rocket).homovore$getLife() <= 6) {
            return true;
         }
      }

      return false;
   }

   private boolean hasUsefulBoost() {
      int bestRemaining = Integer.MIN_VALUE;

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof FireworkRocketEntity rocket && attachedToPlayer(rocket)) {
            int remaining = minimumLifetime(rocket) - ((FireworkRocketEntityAccessor)rocket).homovore$getLife();
            bestRemaining = Math.max(bestRemaining, remaining);
         }
      }

      return bestRemaining > this.minBoostTicks.getValue();
   }

   private static boolean attachedToPlayer(FireworkRocketEntity rocket) {
      return ((FireworkRocketEntityAccessor)rocket).homovore$getAttachedToEntity() == mc.player;
   }

   private static int minimumLifetime(FireworkRocketEntity rocket) {
      FireworksComponent fireworks = (FireworksComponent)rocket.getStack().get(DataComponentTypes.FIREWORKS);
      int flight = fireworks != null ? fireworks.flightDuration() : 1;
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
         ItemStack stowed = snapshot.item(this.stowedSlot);
         if (stowed.isEmpty() || isChestplate(stowed)) {
            return this.stowedSlot;
         }
      }

      Result chestplate = InventoryUtil.find(AutoElytraModule::isChestplate, InventoryUtil.MAIN_INVENTORY_SCOPE);
      return chestplate.found() ? chestplate.slot() : -1;
   }

   private static boolean isChestplate(ItemStack stack) {
      if (stack.isOf(Items.ELYTRA)) {
         return false;
      }

      EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
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
