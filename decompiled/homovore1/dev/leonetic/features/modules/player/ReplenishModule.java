package dev.leonetic.features.modules.player;

import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;

public class ReplenishModule extends Module {
   private final Setting<Integer> percentage = this.num("Percentage", 20, 10, 50);
   private final Setting<Boolean> alternative = this.bool("Alternative", false);
   private final Map<Integer, Integer> hotbarTicks = new HashMap<>();
   private final Map<Integer, class_1792> lastHotbarItems = new HashMap<>();

   public ReplenishModule() {
      super("Replenish", "Automatically refills items in the hotbar.", Module.Category.PLAYER);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (mc.field_1755 == null) {
            if (InventoryUtil.cursor().method_7960()) {
               for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                  class_1799 stack = mc.field_1724.method_31548().method_5438(hotbarSlot);
                  class_1792 currentItem = stack.method_7960() ? null : stack.method_7909();
                  if (this.lastHotbarItems.getOrDefault(hotbarSlot, null) != currentItem) {
                     this.hotbarTicks.put(hotbarSlot, 0);
                     this.lastHotbarItems.put(hotbarSlot, currentItem);
                  } else {
                     this.hotbarTicks.put(hotbarSlot, this.hotbarTicks.getOrDefault(hotbarSlot, 0) + 1);
                  }

                  if (this.hotbarTicks.getOrDefault(hotbarSlot, 0) >= 10 && !stack.method_7960()) {
                     int maxCount = stack.method_7914();
                     int minCount = Math.max(1, (int)(maxCount * (this.percentage.getValue().intValue() / 100.0F)));
                     if (stack.method_7947() < minCount) {
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

   private int findInventorySlotToReplenish(class_1799 target) {
      for (int i = 9; i < 36; i++) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(i);
         if (!stack.method_7960() && class_1799.method_31577(stack, target)) {
            return i;
         }
      }

      return -1;
   }

   private void swap(int hotbarSlot, int invSlot) {
      int realInvSlot = invSlot;
      int realHotbarSlot = hotbarSlot + 36;
      if (this.alternative.getValue()) {
         InventoryUtil.click(realInvSlot, 0, class_1713.field_7794);
      } else {
         InventoryUtil.click(realInvSlot, 0, class_1713.field_7790);
         InventoryUtil.click(realHotbarSlot, 0, class_1713.field_7790);
         InventoryUtil.click(realInvSlot, 0, class_1713.field_7790);
      }
   }
}
