package dev.leonetic.v2.util.inventory;

import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.util.network.NetworkAction;
import dev.leonetic.v2.util.network.NetworkActionContext;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import dev.leonetic.v2.util.network.PacketLimiter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public final class SlotManager {
   private static final int UNKNOWN_SLOT = -1;
   private final NetworkThreadExecutor.Session session;
   private final AtomicInteger selectedSlot = new AtomicInteger(-1);

   public SlotManager(NetworkThreadExecutor.Session session) {
      this.session = Objects.requireNonNull(session, "session");
   }

   public int selectedSlot() {
      return this.selectedSlot.get();
   }

   public CompletableFuture<Boolean> select(int hotbarSlot) {
      return !validHotbarSlot(hotbarSlot) ? CompletableFuture.completedFuture(false) : this.session.submit(context -> {
         this.selectNow(context, hotbarSlot);
         return true;
      });
   }

   public CompletableFuture<Boolean> click(int menuSlot, int button, SlotActionType input) {
      Objects.requireNonNull(input, "input");
      return this.session.submit(context -> this.clickNow(context, InventoryUtil.snapshot(), menuSlot, button, input));
   }

   public CompletableFuture<Boolean> withSlot(Result result, NetworkAction action) {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(action, "action");
      return this.session.swapManager().withSwap(result, action);
   }

   public CompletableFuture<Boolean> moveToOffhand(Result result) {
      Objects.requireNonNull(result, "result");
      return this.session.swapManager().moveToOffhand(result);
   }

   public void observeSelectedSlot(int slot) {
      if (validHotbarSlot(slot)) {
         this.selectedSlot.set(slot);
      }
   }

   public void initializeSelectedSlot(int slot) {
      if (validHotbarSlot(slot)) {
         this.selectedSlot.compareAndSet(-1, slot);
      }
   }

   void selectNow(NetworkActionContext context, int slot) {
      context.requireNetworkThread();
      if (!validHotbarSlot(slot)) {
         throw new IllegalArgumentException("Hotbar slot must be in [0, 8]");
      }

      if (this.selectedSlot.get() != slot) {
         Homovore2.LOGGER.info("[Inventory] select hotbarSlot={} previous={}", slot, this.selectedSlot.get());
         context.send(new UpdateSelectedSlotC2SPacket(slot));
         this.selectedSlot.set(slot);
      }
   }

   boolean swapInventoryNow(NetworkActionContext context, InventorySnapshot snapshot, int menuSlot, int hotbarSlot) {
      return this.clickNow(context, snapshot, menuSlot, hotbarSlot, SlotActionType.SWAP);
   }

   boolean clickNow(NetworkActionContext context, InventorySnapshot snapshot, int menuSlot, int button, SlotActionType input) {
      return this.clickNow(context, snapshot, menuSlot, button, input, true);
   }

   boolean clickNow(NetworkActionContext context, InventorySnapshot snapshot, int menuSlot, int button, SlotActionType input, boolean budgeted) {
      context.requireNetworkThread();
      if (!snapshot.available() || !snapshot.cursorEmpty() || menuSlot < 0 || menuSlot > 32767 || button < -128 || button > 127) {
         Homovore2.LOGGER
            .info(
               "[Inventory] click rejected input={} menuSlot={} button={} available={} cursorEmpty={}",
               new Object[]{input, menuSlot, button, snapshot.available(), snapshot.cursorEmpty()}
            );
         return false;
      } else if (input == SlotActionType.SWAP && !validSwapTarget(snapshot, menuSlot, button)) {
         return false;
      } else if (budgeted && !context.limits().canSend(PacketLimiter.Bucket.CLICK_SLOT)) {
         Homovore2.LOGGER.info("[Inventory] click rejected by packet budget input={} menuSlot={} button={}", new Object[]{input, menuSlot, button});
         return false;
      } else {
         int containerId = snapshot.menuId();
         long observedBefore = context.limits().containerClicksObserved();
         Homovore2.LOGGER.info("[Inventory] click dispatch container={} input={} menuSlot={} button={}", new Object[]{containerId, input, menuSlot, button});
         ContainerClicks.dispatch(() -> {
            int applied = ContainerClicks.apply(containerId, menuSlot, button, input) ? 1 : 0;
            this.reconcileDispatched(observedBefore, applied);
         });
         return true;
      }
   }

   void reconcileDispatched(long observedBefore, int applied) {
      if (applied > 0) {
         this.session.submit(context -> {
            context.limits().reconcileDispatchedClicks(observedBefore, applied);
            return null;
         });
      }
   }

   private static boolean validSwapTarget(InventorySnapshot snapshot, int menuSlot, int button) {
      int sourceInventorySlot = snapshot.inventorySlot(menuSlot);
      if (sourceInventorySlot >= 0 && (validHotbarSlot(button) || button == 40)) {
         int targetMenuSlot = snapshot.menuSlot(button);
         if (targetMenuSlot >= 0 && targetMenuSlot != menuSlot) {
            return true;
         }

         Homovore2.LOGGER.info("[Inventory] swap rejected targetMenuSlot={} sourceMenuSlot={}", targetMenuSlot, menuSlot);
         return false;
      } else {
         Homovore2.LOGGER.info("[Inventory] swap rejected sourceInventorySlot={} targetInventorySlot={}", sourceInventorySlot, button);
         return false;
      }
   }

   boolean mergeStacksNow(NetworkActionContext context, InventorySnapshot snapshot, int sourceMenuSlot, int targetMenuSlot) {
      context.requireNetworkThread();
      if (snapshot.available() && snapshot.cursorEmpty()) {
         int sourceInventorySlot = snapshot.inventorySlot(sourceMenuSlot);
         int targetInventorySlot = snapshot.inventorySlot(targetMenuSlot);
         if (sourceInventorySlot >= 0 && targetInventorySlot >= 0 && sourceMenuSlot != targetMenuSlot) {
            ItemStack source = snapshot.itemInternal(sourceInventorySlot);
            ItemStack target = snapshot.itemInternal(targetInventorySlot);
            if (source.isEmpty() || target.isEmpty() || !ItemStack.areItemsAndComponentsEqual(source, target) || target.getCount() >= target.getMaxCount()) {
               return false;
            } else if (context.limits().remaining(PacketLimiter.Bucket.CLICK_SLOT) < 3) {
               Homovore2.LOGGER.info("[Inventory] merge rejected by packet budget required={}", 3);
               return false;
            } else {
               int containerId = snapshot.menuId();
               long observedBefore = context.limits().containerClicksObserved();
               Homovore2.LOGGER.info("[Inventory] merge dispatch sourceMenuSlot={} targetMenuSlot={}", sourceMenuSlot, targetMenuSlot);
               ContainerClicks.dispatch(() -> {
                  int applied = 0;

                  try {
                     if (!ContainerClicks.cursorEmpty()) {
                        return;
                     }

                     if (ContainerClicks.apply(containerId, sourceMenuSlot, 0, SlotActionType.PICKUP)) {
                        applied++;
                        if (ContainerClicks.apply(containerId, targetMenuSlot, 0, SlotActionType.PICKUP)) {
                           applied++;
                        }

                        if (!ContainerClicks.cursorEmpty() && ContainerClicks.apply(containerId, sourceMenuSlot, 0, SlotActionType.PICKUP)) {
                           applied++;
                        }

                        if (!ContainerClicks.cursorEmpty()) {
                           Homovore2.LOGGER.warn("[Inventory] merge left an item on the cursor");
                        }

                        return;
                     }
                  } finally {
                     this.reconcileDispatched(observedBefore, applied);
                  }
               });
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static boolean validHotbarSlot(int slot) {
      return slot >= 0 && slot < 9;
   }
}
