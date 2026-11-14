package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.network.NetworkThreadExecutor;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;

public final class HotbarRefillModule extends Module {
   private static final long CONFIRMATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2L);
   public final Setting<Integer> percentage = this.integer("Percentage", 20, 1, 90)
      .describe("Refill when a stack falls below this percentage of its maximum size.");
   public final Setting<Integer> delay = this.integer("Delay", 10, 0, 40).describe("Ticks a hotbar slot must hold the same item before it can be refilled.");
   public final Setting<Boolean> quickMove = this.bool("QuickMove", false).describe("Replace the low stack with one targeted swap instead of merging stacks.");
   public final Setting<Boolean> inScreens = this.bool("InScreens", true).describe("Allow refills through the currently open menu.");
   private final int[] settledTicks = new int[class_1661.method_7368()];
   private final class_1792[] observedItems = new class_1792[class_1661.method_7368()];
   private final class_1799[] refillTemplates = new class_1799[class_1661.method_7368()];
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
      } else if (mc.field_1755 != null && !this.inScreens.getValue()) {
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
         class_1799 missing = class_1799.field_8037;

         for (int hotbarSlot = 0; hotbarSlot < class_1661.method_7368(); hotbarSlot++) {
            class_1799 target = snapshot.item(hotbarSlot);
            if (this.settled(hotbarSlot, target)) {
               class_1799 template = this.refillTemplate(hotbarSlot, target);
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
         boolean confirmed = snapshot.version() > current.snapshotVersion()
            && !class_1799.method_7973(current.sourceStack(), snapshot.item(current.sourceSlot()));
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

   private boolean settled(int slot, class_1799 stack) {
      class_1792 item = stack.method_7960() ? null : stack.method_7909();
      if (this.observedItems[slot] != item) {
         this.observedItems[slot] = item;
         this.settledTicks[slot] = 0;
      } else {
         this.settledTicks[slot]++;
      }

      if (!stack.method_7960()) {
         this.refillTemplates[slot] = stack.method_7972();
      }

      return this.settledTicks[slot] >= this.delay.getValue();
   }

   private class_1799 refillTemplate(int slot, class_1799 target) {
      if (!target.method_7960()) {
         return target;
      }

      class_1799 remembered = this.refillTemplates[slot];
      return remembered == null ? class_1799.field_8037 : remembered.method_7972();
   }

   private boolean needsRefill(class_1799 target, class_1799 template) {
      if (template.method_7960() || !template.method_7946()) {
         return false;
      }

      if (target.method_7960()) {
         return true;
      }

      int minimum = Math.max(1, template.method_7914() * this.percentage.getValue() / 100);
      return target.method_7947() < minimum;
   }

   private static int findSource(InventorySnapshot snapshot, class_1799 target, int inventorySize) {
      int bestSlot = -1;
      int bestCount = 0;

      for (int slot = class_1661.method_7368(); slot < inventorySize; slot++) {
         class_1799 candidate = snapshot.item(slot);
         if (class_1799.method_31577(candidate, target) && candidate.method_7947() > bestCount) {
            bestSlot = slot;
            bestCount = candidate.method_7947();
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

   private record PendingRefill(long snapshotVersion, int targetSlot, class_1799 targetStack, int sourceSlot, class_1799 sourceStack, long startedAtNanos) {
      private PendingRefill {
         targetStack = targetStack.method_7972();
         sourceStack = sourceStack.method_7972();
      }

      public class_1799 targetStack() {
         return this.targetStack.method_7972();
      }

      public class_1799 sourceStack() {
         return this.sourceStack.method_7972();
      }
   }
}
