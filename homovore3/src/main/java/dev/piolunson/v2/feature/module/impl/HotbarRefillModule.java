package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.util.inventory.InventorySnapshot;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.network.NetworkThreadExecutor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class HotbarRefillModule extends Module {
   private static final long CONFIRMATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2L);
   public final Setting<Integer> percentage = this.integer("Percentage", 20, 1, 90)
      .describe("Refill when a stack falls below this percentage of its maximum size.");
   public final Setting<Integer> delay = this.integer("Delay", 10, 0, 40).describe("Ticks a hotbar slot must hold the same item before it can be refilled.");
   public final Setting<Boolean> quickMove = this.bool("QuickMove", false).describe("Replace the low stack with one targeted swap instead of merging stacks.");
   public final Setting<Boolean> inScreens = this.bool("InScreens", true).describe("Allow refills through the currently open menu.");
   private final int[] settledTicks = new int[PlayerInventory.getHotbarSize()];
   private final Item[] observedItems = new Item[PlayerInventory.getHotbarSize()];
   private final ItemStack[] refillTemplates = new ItemStack[PlayerInventory.getHotbarSize()];
   private final AtomicReference<HotbarRefillModule.PendingRefill> pending = new AtomicReference<>();
   private String lastDiagnostic;

   public HotbarRefillModule() {
      super("HotbarRefill", Category.EXTRA, "Refills low hotbar stacks from your inventory.");
   }

   @Override
   public void onEnable() {
      this.forget();
   }

   @Override
   public void onDisable() {
      this.pending.set(null);
      this.forget();
   }

   @Override
   public void onTick() {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!snapshot.available()) {
         this.diagnose("waiting for an inventory snapshot");
         this.forget();
      } else if (mc.currentScreen != null && !this.inScreens.getValue()) {
         this.diagnose("paused while a screen is open");
         this.forget();
      } else if (!NetworkThreadExecutor.isConnected()) {
         this.diagnose("waiting for an active connection");
         this.forget();
      } else if (NetworkThreadExecutor.swapsLatched()) {
         this.diagnose("waiting for an active held-item lease");
      } else if (!snapshot.cursorEmpty()) {
         this.diagnose("waiting for the inventory cursor to become empty");
      } else if (!this.awaitingConfirmation(snapshot)) {
         int inventorySize = Math.min(36, snapshot.size());
         int unfilledSlot = -1;
         ItemStack missing = ItemStack.EMPTY;

         for (int hotbarSlot = 0; hotbarSlot < PlayerInventory.getHotbarSize(); hotbarSlot++) {
            ItemStack target = snapshot.item(hotbarSlot);
            if (this.settled(hotbarSlot, target)) {
               ItemStack template = this.refillTemplate(hotbarSlot, target);
               if (this.needsRefill(target, template)) {
                  int sourceSlot = findSource(snapshot, template, inventorySize);
                  if (sourceSlot >= 0) {
                     HotbarRefillModule.PendingRefill marker = new HotbarRefillModule.PendingRefill(
                        snapshot.version(), hotbarSlot, target, sourceSlot, snapshot.item(sourceSlot), System.nanoTime()
                     );
                     if (!this.pending.compareAndSet(null, marker)) {
                        return;
                     }

                     this.lastDiagnostic = null;
                     Homovore2.LOGGER
                        .info(
                           "[Inventory] HotbarRefill submit targetSlot={} target={} sourceSlot={} source={} mode={}",
                           new Object[]{hotbarSlot, target, sourceSlot, snapshot.item(sourceSlot), this.quickMove.getValue() ? "targeted-swap" : "merge"}
                        );
                     InventoryUtil.refillHotbar(snapshot, hotbarSlot, sourceSlot, template, this.quickMove.getValue()).whenComplete((refilled, failure) -> {
                        if (failure != null) {
                           Homovore2.LOGGER.warn("[Inventory] HotbarRefill transaction failed", failure);
                        } else {
                           Homovore2.LOGGER.info("[Inventory] HotbarRefill transaction submitted={}", refilled);
                        }

                        if (failure != null || !Boolean.TRUE.equals(refilled)) {
                           this.pending.compareAndSet(marker, null);
                        }
                     });
                     return;
                  }

                  if (unfilledSlot < 0) {
                     unfilledSlot = hotbarSlot;
                     missing = template;
                  }
               }
            }
         }

         if (unfilledSlot >= 0) {
            this.diagnose("slot " + unfilledSlot + " needs " + missing + " but no matching main-inventory stack exists");
         } else {
            this.diagnose("no settled hotbar stack is below the refill threshold");
         }
      }
   }

   private boolean awaitingConfirmation(InventorySnapshot snapshot) {
      HotbarRefillModule.PendingRefill current = this.pending.get();
      if (current == null) {
         return false;
      } else {
         boolean confirmed = snapshot.version() > current.snapshotVersion() && !ItemStack.areEqual(current.sourceStack(), snapshot.item(current.sourceSlot()));
         if (confirmed) {
            this.pending.compareAndSet(current, null);
            Homovore2.LOGGER.info("[Inventory] HotbarRefill confirmed targetSlot={} sourceSlot={}", current.targetSlot(), current.sourceSlot());
            return true;
         } else if (System.nanoTime() - current.startedAtNanos() < CONFIRMATION_TIMEOUT_NANOS) {
            this.diagnose("waiting for server confirmation of source slot " + current.sourceSlot());
            return true;
         } else {
            Homovore2.LOGGER
               .warn(
                  "[Inventory] HotbarRefill timed out targetSlot={} sourceSlot={} snapshotVersion={}",
                  new Object[]{current.targetSlot(), current.sourceSlot(), snapshot.version()}
               );
            return !this.pending.compareAndSet(current, null);
         }
      }
   }

   private boolean settled(int slot, ItemStack stack) {
      Item item = stack.isEmpty() ? null : stack.getItem();
      if (this.observedItems[slot] != item) {
         this.observedItems[slot] = item;
         this.settledTicks[slot] = 0;
      } else {
         this.settledTicks[slot]++;
      }

      if (!stack.isEmpty()) {
         this.refillTemplates[slot] = stack.copy();
      }

      return this.settledTicks[slot] >= this.delay.getValue();
   }

   private ItemStack refillTemplate(int slot, ItemStack target) {
      if (!target.isEmpty()) {
         return target;
      }

      ItemStack remembered = this.refillTemplates[slot];
      return remembered == null ? ItemStack.EMPTY : remembered.copy();
   }

   private boolean needsRefill(ItemStack target, ItemStack template) {
      if (template.isEmpty() || !template.isStackable()) {
         return false;
      }

      if (target.isEmpty()) {
         return true;
      }

      int minimum = Math.max(1, template.getMaxCount() * this.percentage.getValue() / 100);
      return target.getCount() < minimum;
   }

   private static int findSource(InventorySnapshot snapshot, ItemStack target, int inventorySize) {
      int bestSlot = -1;
      int bestCount = 0;

      for (int slot = PlayerInventory.getHotbarSize(); slot < inventorySize; slot++) {
         ItemStack candidate = snapshot.item(slot);
         if (ItemStack.areItemsAndComponentsEqual(candidate, target) && candidate.getCount() > bestCount) {
            bestSlot = slot;
            bestCount = candidate.getCount();
         }
      }

      return bestSlot;
   }

   private void diagnose(String message) {
      if (!message.equals(this.lastDiagnostic)) {
         this.lastDiagnostic = message;
         Homovore2.LOGGER.info("[Inventory] HotbarRefill {}", message);
      }
   }

   private void forget() {
      Arrays.fill(this.settledTicks, 0);
      Arrays.fill(this.observedItems, null);
      Arrays.fill(this.refillTemplates, null);
      this.lastDiagnostic = null;
   }

   private record PendingRefill(long snapshotVersion, int targetSlot, ItemStack targetStack, int sourceSlot, ItemStack sourceStack, long startedAtNanos) {
      private PendingRefill {
         targetStack = targetStack.copy();
         sourceStack = sourceStack.copy();
      }

      public ItemStack targetStack() {
         return this.targetStack.copy();
      }

      public ItemStack sourceStack() {
         return this.sourceStack.copy();
      }
   }
}
