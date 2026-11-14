package dev.leonetic.feature.module.impl;

import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.RegistryKind;
import dev.leonetic.feature.setting.RegistrySetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.BaritoneSupport;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.inventory.SwapManager;
import dev.leonetic.util.network.NetworkThreadExecutor;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_4174;
import net.minecraft.class_746;
import net.minecraft.class_9334;

public final class AutoEatModule extends Module {
   private static final List<class_1792> DEFAULT_BLACKLIST = List.of(
      class_1802.field_8367,
      class_1802.field_8463,
      class_1802.field_8233,
      class_1802.field_8635,
      class_1802.field_8323,
      class_1802.field_8726,
      class_1802.field_8511,
      class_1802.field_8680,
      class_1802.field_8766
   );
   public final RegistrySetting<class_1792> blacklist = this.register(new RegistrySetting<>("Blacklist", RegistryKind.ITEM, DEFAULT_BLACKLIST));
   public final Setting<Boolean> searchInventory = this.bool("SearchInventory", false)
      .describe("Moves food out of the main inventory into an empty hotbar slot when the hotbar has none.");
   public final Setting<AutoEatModule.Priority> foodPriority = this.mode("FoodPriority", AutoEatModule.Priority.SATURATION)
      .describe("Which aspect of a food makes it the better one to eat.");
   public final Setting<AutoEatModule.Threshold> thresholdMode = this.mode("ThresholdMode", AutoEatModule.Threshold.HEALTH)
      .describe("How the health and hunger thresholds combine into the decision to eat.");
   public final Setting<Double> healthThreshold = this.number("HealthThreshold", (double)10.0, (double)1.0, (double)19.0)
      .describe("Half-hearts at or below which health counts as low.");
   public final Setting<Integer> hungerThreshold = this.integer("HungerThreshold", 7, 1, 19).describe("Hunger points at or below which hunger counts as low.");
   public final Setting<Boolean> pauseBaritone = this.bool("PauseBaritone", false).describe("Holds a Baritone path where it is for the length of the meal.");
   private final AtomicReference<SwapManager.MainhandLease> lease = new AtomicReference<>();
   private final AtomicBoolean acquisitionPending = new AtomicBoolean();
   private final AtomicBoolean restockPending = new AtomicBoolean();
   private final AtomicBoolean wantsLease = new AtomicBoolean();
   private final AtomicLong requestGeneration = new AtomicLong();
   private boolean eating;
   private boolean waiting;
   private boolean wasUseDown;
   private boolean pausedBaritone;

   public AutoEatModule() {
      super("AutoEat", Category.SAFETY, "Eats when health or hunger runs low.");
   }

   @Override
   public void onDisable() {
      this.stopEating();
   }

   @Override
   public void onTick() {
      SwapManager.MainhandLease current = this.lease.get();
      if (current != null && !current.isActive()) {
         this.lease.compareAndSet(current, null);
      }

      class_746 player = mc.field_1724;
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (player == null || !snapshot.available() || snapshot.deadOrDying()) {
         this.stopEating();
      } else if (this.eating || !player.method_6115()) {
         if (!this.shouldEat(player, snapshot)) {
            this.stopEating();
         } else {
            this.waiting = false;
            class_1268 hand = this.prepare(player, snapshot);
            if (hand == null) {
               if (!this.waiting) {
                  this.stopEating();
               }
            } else {
               if (!this.eating) {
                  this.startEating(player);
               }

               this.keepEating(player, hand);
            }
         }
      }
   }

   private boolean shouldEat(class_746 player, InventorySnapshot snapshot) {
      boolean healthLow = player.method_6032() <= this.healthThreshold.getValue();
      boolean hungerLow = player.method_7344().method_7586() <= this.hungerThreshold.getValue();
      if (!this.thresholdMode.getValue().test(healthLow, hungerLow)) {
         return false;
      }

      class_1799 food = this.eating ? this.mealStack(player, snapshot) : this.bestStack(snapshot);
      class_4174 properties = (class_4174)food.method_58694(class_9334.field_50075);
      return properties != null && this.edible(food) ? player.method_7344().method_7587() || properties.comp_2493() : false;
   }

   private class_1268 prepare(class_746 player, InventorySnapshot snapshot) {
      if (this.edible(player.method_6079())) {
         this.releaseLease();
         return class_1268.field_5810;
      }

      SwapManager.MainhandLease held = this.lease.get();
      if (held != null) {
         if (this.edible(snapshot.item(held.slot()))) {
            if (this.edible(player.method_6047())) {
               return class_1268.field_5808;
            }

            this.waiting = true;
            return null;
         }

         this.releaseLease();
      }

      if (!this.acquisitionPending.get() && !this.restockPending.get()) {
         int hotbar = this.findBestFood(snapshot, 0, class_1661.method_7368());
         if (hotbar != -1) {
            this.requestLease(InventoryUtil.resultAt(snapshot, hotbar));
            return null;
         }

         if (this.searchInventory.getValue()) {
            this.restock(snapshot);
         }

         return null;
      } else {
         this.waiting = true;
         return null;
      }
   }

   private void startEating(class_746 player) {
      this.eating = true;
      this.wasUseDown = mc.field_1690.field_1904.method_1434();
      if (this.pauseBaritone.getValue() && BaritoneSupport.isPathing()) {
         this.pausedBaritone = true;
         BaritoneSupport.pause();
      }
   }

   private void keepEating(class_746 player, class_1268 hand) {
      mc.field_1690.field_1904.method_23481(true);
      if (mc.field_1761 != null && !player.method_6115()) {
         mc.field_1761.method_2919(player, hand);
      }
   }

   private void stopEating() {
      this.wantsLease.set(false);
      this.requestGeneration.incrementAndGet();
      this.releaseLease();
      this.waiting = false;
      if (this.eating) {
         mc.field_1690.field_1904.method_23481(this.wasUseDown);
         if (mc.field_1724 != null && mc.field_1724.method_6115() && mc.field_1761 != null) {
            mc.field_1761.method_2897(mc.field_1724);
         }

         if (this.pausedBaritone) {
            this.pausedBaritone = false;
            BaritoneSupport.resume();
         }

         this.eating = false;
      }
   }

   private void requestLease(Result food) {
      if (food.found()
         && food.type() == ResultType.HOTBAR
         && NetworkThreadExecutor.isConnected()
         && !NetworkThreadExecutor.swapsLatched()
         && this.acquisitionPending.compareAndSet(false, true)) {
         this.wantsLease.set(true);
         this.waiting = true;
         long generation = this.requestGeneration.incrementAndGet();

         try {
            InventoryUtil.leaseMainhand(food).whenComplete((acquired, failure) -> {
               this.acquisitionPending.set(false);
               if (acquired != null && (!this.wantsLease.get() || this.requestGeneration.get() != generation || !this.lease.compareAndSet(null, acquired))) {
                  acquired.close();
               }
            });
         } catch (RuntimeException exception) {
            this.acquisitionPending.set(false);
         }
      }
   }

   private void restock(InventorySnapshot snapshot) {
      int hotbarSlot = findEmptyHotbarSlot(snapshot);
      int source = this.findBestFood(snapshot, class_1661.method_7368(), Math.min(36, snapshot.size()));
      if (hotbarSlot != -1 && source != -1 && this.restockPending.compareAndSet(false, true)) {
         this.waiting = true;
         class_1799 template = snapshot.item(source);

         try {
            InventoryUtil.refillHotbar(snapshot, hotbarSlot, source, template, false).whenComplete((moved, failure) -> this.restockPending.set(false));
         } catch (RuntimeException exception) {
            this.restockPending.set(false);
         }
      }
   }

   private void releaseLease() {
      SwapManager.MainhandLease current = this.lease.getAndSet(null);
      if (current != null) {
         current.close();
      }
   }

   private class_1799 mealStack(class_746 player, InventorySnapshot snapshot) {
      SwapManager.MainhandLease held = this.lease.get();
      if (held != null) {
         return snapshot.item(held.slot());
      }

      class_1799 offhand = player.method_6079();
      return this.edible(offhand) ? offhand : player.method_6047();
   }

   private class_1799 bestStack(InventorySnapshot snapshot) {
      if (this.edible(snapshot.offhand())) {
         return snapshot.offhand();
      }

      int hotbar = this.findBestFood(snapshot, 0, class_1661.method_7368());
      if (hotbar != -1) {
         return snapshot.item(hotbar);
      }

      if (!this.searchInventory.getValue()) {
         return class_1799.field_8037;
      }

      int inventory = this.findBestFood(snapshot, class_1661.method_7368(), Math.min(36, snapshot.size()));
      return inventory == -1 ? class_1799.field_8037 : snapshot.item(inventory);
   }

   private int findBestFood(InventorySnapshot snapshot, int from, int to) {
      int best = -1;
      float bestValue = -1.0F;

      for (int slot = from; slot < to; slot++) {
         class_1799 stack = snapshot.item(slot);
         if (this.edible(stack)) {
            float value = this.foodPriority.getValue().value((class_4174)stack.method_58694(class_9334.field_50075));
            if (value > bestValue) {
               bestValue = value;
               best = slot;
            }
         }
      }

      return best;
   }

   private static int findEmptyHotbarSlot(InventorySnapshot snapshot) {
      for (int slot = 0; slot < class_1661.method_7368(); slot++) {
         if (snapshot.item(slot).method_7960()) {
            return slot;
         }
      }

      return -1;
   }

   private boolean edible(class_1799 stack) {
      return isFood(stack) && !this.blacklist.contains(stack.method_7909());
   }

   private static boolean isFood(class_1799 stack) {
      return stack.method_57826(class_9334.field_50075) && stack.method_57826(class_9334.field_53964);
   }

   public boolean isEating() {
      return this.eating;
   }

   public enum Priority {
      COMBINED,
      HUNGER,
      SATURATION;

      public float value(class_4174 food) {
         return switch (this) {
            case COMBINED -> food.comp_2491() + food.comp_2492();
            case HUNGER -> food.comp_2491();
            case SATURATION -> food.comp_2492();
         };
      }
   }

   public enum Threshold {
      HEALTH,
      HUNGER,
      ANY,
      BOTH;

      public boolean test(boolean healthLow, boolean hungerLow) {
         return switch (this) {
            case HEALTH -> healthLow;
            case HUNGER -> hungerLow;
            case ANY -> healthLow || hungerLow;
            case BOTH -> healthLow && hungerLow;
         };
      }
   }
}
