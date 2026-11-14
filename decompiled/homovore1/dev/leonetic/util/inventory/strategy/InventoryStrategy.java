package dev.leonetic.util.inventory.strategy;

import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import net.minecraft.class_1713;

public final class InventoryStrategy implements SwapStrategy {
   public static final InventoryStrategy INSTANCE = new InventoryStrategy();

   private InventoryStrategy() {
   }

   @Override
   public boolean swap(Result result) {
      if (result.type() != ResultType.INVENTORY && result.type() != ResultType.HOTBAR) {
         return false;
      }

      int slot = inventorySlot(result);
      InventoryUtil.click(slot, InventoryUtil.selected(), class_1713.field_7791);
      return true;
   }

   @Override
   public boolean swapBack(int last, Result result) {
      return this.swap(result);
   }

   private static int inventorySlot(Result result) {
      return result.type() == ResultType.HOTBAR ? result.slot() + 36 : result.slot();
   }
}
