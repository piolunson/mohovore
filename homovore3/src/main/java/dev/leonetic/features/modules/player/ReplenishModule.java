package dev.leonetic.features.modules.player;

import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class ReplenishModule extends Module {
   private final Setting<Integer> percentage = this.num("Percentage", 20, 10, 50);
   private final Setting<Boolean> alternative = this.bool("Alternative", false);
   private final Map<Integer, Integer> hotbarTicks = new HashMap<>();
   private final Map<Integer, Item> lastHotbarItems = new HashMap<>();

   public ReplenishModule() {
      super("Replenish", "Automatically refills items in the hotbar.", Module.Category.PLAYER);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (mc.currentScreen == null) {
            if (InventoryUtil.cursor().isEmpty()) {
               for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                  ItemStack stack = mc.player.getInventory().getStack(hotbarSlot);
                  Item currentItem = stack.isEmpty() ? null : stack.getItem();
                  if (this.lastHotbarItems.getOrDefault(hotbarSlot, null) != currentItem) {
                     this.hotbarTicks.put(hotbarSlot, 0);
                     this.lastHotbarItems.put(hotbarSlot, currentItem);
                  } else {
                     this.hotbarTicks.put(hotbarSlot, this.hotbarTicks.getOrDefault(hotbarSlot, 0) + 1);
                  }

                  if (this.hotbarTicks.getOrDefault(hotbarSlot, 0) >= 10 && !stack.isEmpty()) {
                     int maxCount = stack.getMaxCount();
                     int minCount = Math.max(1, (int)(maxCount * (this.percentage.getValue().intValue() / 100.0F)));
                     if (stack.getCount() < minCount) {
                        int invSlot = this.findInventorySlotToReplenish(stack);
                        if (invSlot != -1) {
                           this.swap(hotbarSlot, invSlot);
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private int findInventorySlotToReplenish(ItemStack target) {
      for (int i = 9; i < 36; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, target)) {
            return i;
         }
      }

      return -1;
   }

   private void swap(int hotbarSlot, int invSlot) {
      int realInvSlot = invSlot;
      int realHotbarSlot = hotbarSlot + 36;
      if (this.alternative.getValue()) {
         InventoryUtil.click(realInvSlot, 0, SlotActionType.QUICK_MOVE);
      } else {
         InventoryUtil.click(realInvSlot, 0, SlotActionType.PICKUP);
         InventoryUtil.click(realHotbarSlot, 0, SlotActionType.PICKUP);
         InventoryUtil.click(realInvSlot, 0, SlotActionType.PICKUP);
      }
   }
}
