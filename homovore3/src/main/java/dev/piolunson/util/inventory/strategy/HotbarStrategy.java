package dev.piolunson.util.inventory.strategy;

import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.ResultType;

public final class HotbarStrategy implements SwapStrategy {
   public static final HotbarStrategy INSTANCE = new HotbarStrategy();

   private HotbarStrategy() {
   }

   @Override
   public boolean swap(Result result) {
      if (result.type() == ResultType.HOTBAR) {
         InventoryUtil.swap(result.slot());
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean swapBack(int last, Result result) {
      if (result.type() == ResultType.HOTBAR) {
         InventoryUtil.swap(last);
         return true;
      } else {
         return false;
      }
   }
}
