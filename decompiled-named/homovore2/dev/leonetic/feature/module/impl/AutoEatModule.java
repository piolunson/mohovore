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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public final class AutoEatModule extends Module {
   private static final List<Item> DEFAULT_BLACKLIST = List.of(
      Items.ENCHANTED_GOLDEN_APPLE,
      Items.GOLDEN_APPLE,
      Items.CHORUS_FRUIT,
      Items.POISONOUS_POTATO,
      Items.PUFFERFISH,
      Items.CHICKEN,
      Items.ROTTEN_FLESH,
      Items.SPIDER_EYE,
      Items.SUSPICIOUS_STEW
   );
   public final RegistrySetting<Item> blacklist = this.register(new RegistrySetting<>("Blacklist", RegistryKind.ITEM, DEFAULT_BLACKLIST));
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

      ClientPlayerEntity player = mc.player;
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (player == null || !snapshot.available() || snapshot.deadOrDying()) {
         this.stopEating();
      } else if (this.eating || !player.isUsingItem()) {
         if (!this.shouldEat(player, snapshot)) {
            this.stopEating();
         } else {
            this.waiting = false;
            Hand hand = this.prepare(player, snapshot);
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

   private boolean shouldEat(ClientPlayerEntity player, InventorySnapshot snapshot) {
      boolean healthLow = player.getHealth() <= this.healthThreshold.getValue();
      boolean hungerLow = player.getHungerManager().getFoodLevel() <= this.hungerThreshold.getValue();
      if (!this.thresholdMode.getValue().test(healthLow, hungerLow)) {
         return false;
      }

      ItemStack food = this.eating ? this.mealStack(player, snapshot) : this.bestStack(snapshot);
      FoodComponent properties = (FoodComponent)food.get(DataComponentTypes.FOOD);
      return properties != null && this.edible(food) ? player.getHungerManager().isNotFull() || properties.canAlwaysEat() : false;
   }

   private Hand prepare(ClientPlayerEntity player, InventorySnapshot snapshot) {
      if (this.edible(player.getOffHandStack())) {
         this.releaseLease();
         return Hand.OFF_HAND;
      }

      SwapManager.MainhandLease held = this.lease.get();
      if (held != null) {
         if (this.edible(snapshot.item(held.slot()))) {
            if (this.edible(player.getMainHandStack())) {
               return Hand.MAIN_HAND;
            }

            this.waiting = true;
            return null;
         }

         this.releaseLease();
      }

      if (!this.acquisitionPending.get() && !this.restockPending.get()) {
         int hotbar = this.findBestFood(snapshot, 0, PlayerInventory.getHotbarSize());
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

   private void startEating(ClientPlayerEntity player) {
      this.eating = true;
      this.wasUseDown = mc.options.useKey.isPressed();
      if (this.pauseBaritone.getValue() && BaritoneSupport.isPathing()) {
         this.pausedBaritone = true;
         BaritoneSupport.pause();
      }
   }

   private void keepEating(ClientPlayerEntity player, Hand hand) {
      mc.options.useKey.setPressed(true);
      if (mc.interactionManager != null && !player.isUsingItem()) {
         mc.interactionManager.interactItem(player, hand);
      }
   }

   private void stopEating() {
      this.wantsLease.set(false);
      this.requestGeneration.incrementAndGet();
      this.releaseLease();
      this.waiting = false;
      if (this.eating) {
         mc.options.useKey.setPressed(this.wasUseDown);
         if (mc.player != null && mc.player.isUsingItem() && mc.interactionManager != null) {
            mc.interactionManager.stopUsingItem(mc.player);
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
      int source = this.findBestFood(snapshot, PlayerInventory.getHotbarSize(), Math.min(36, snapshot.size()));
      if (hotbarSlot != -1 && source != -1 && this.restockPending.compareAndSet(false, true)) {
         this.waiting = true;
         ItemStack template = snapshot.item(source);

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

   private ItemStack mealStack(ClientPlayerEntity player, InventorySnapshot snapshot) {
      SwapManager.MainhandLease held = this.lease.get();
      if (held != null) {
         return snapshot.item(held.slot());
      }

      ItemStack offhand = player.getOffHandStack();
      return this.edible(offhand) ? offhand : player.getMainHandStack();
   }

   private ItemStack bestStack(InventorySnapshot snapshot) {
      if (this.edible(snapshot.offhand())) {
         return snapshot.offhand();
      }

      int hotbar = this.findBestFood(snapshot, 0, PlayerInventory.getHotbarSize());
      if (hotbar != -1) {
         return snapshot.item(hotbar);
      }

      if (!this.searchInventory.getValue()) {
         return ItemStack.EMPTY;
      }

      int inventory = this.findBestFood(snapshot, PlayerInventory.getHotbarSize(), Math.min(36, snapshot.size()));
      return inventory == -1 ? ItemStack.EMPTY : snapshot.item(inventory);
   }

   private int findBestFood(InventorySnapshot snapshot, int from, int to) {
      int best = -1;
      float bestValue = -1.0F;

      for (int slot = from; slot < to; slot++) {
         ItemStack stack = snapshot.item(slot);
         if (this.edible(stack)) {
            float value = this.foodPriority.getValue().value((FoodComponent)stack.get(DataComponentTypes.FOOD));
            if (value > bestValue) {
               bestValue = value;
               best = slot;
            }
         }
      }

      return best;
   }

   private static int findEmptyHotbarSlot(InventorySnapshot snapshot) {
      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         if (snapshot.item(slot).isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private boolean edible(ItemStack stack) {
      return isFood(stack) && !this.blacklist.contains(stack.getItem());
   }

   private static boolean isFood(ItemStack stack) {
      return stack.contains(DataComponentTypes.FOOD) && stack.contains(DataComponentTypes.CONSUMABLE);
   }

   public boolean isEating() {
      return this.eating;
   }

   public enum Priority {
      COMBINED,
      HUNGER,
      SATURATION;

      public float value(FoodComponent food) {
         return switch (this) {
            case COMBINED -> food.nutrition() + food.saturation();
            case HUNGER -> food.nutrition();
            case SATURATION -> food.saturation();
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
