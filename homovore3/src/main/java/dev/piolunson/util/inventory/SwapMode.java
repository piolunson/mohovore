package dev.piolunson.util.inventory;

import dev.piolunson.util.inventory.strategy.HotbarStrategy;
import dev.piolunson.util.inventory.strategy.InventoryStrategy;
import dev.piolunson.util.inventory.strategy.SwapStrategy;
import java.util.EnumSet;

public enum SwapMode {
   SILENT(HotbarStrategy.INSTANCE, InventoryUtil.HOTBAR_SCOPE, true),
   ALTSILENT(InventoryStrategy.INSTANCE, InventoryUtil.FULL_SCOPE, false),
   LATCH(HotbarStrategy.INSTANCE, InventoryUtil.HOTBAR_SCOPE, true);

   private final SwapStrategy strategy;
   private final EnumSet<ResultType> scope;
   private final boolean pinsSelectedSlot;

   SwapMode(SwapStrategy strategy, EnumSet<ResultType> scope, boolean pinsSelectedSlot) {
      this.strategy = strategy;
      this.scope = scope;
      this.pinsSelectedSlot = pinsSelectedSlot;
   }

   public SwapStrategy strategy() {
      return this.strategy;
   }

   public EnumSet<ResultType> scope() {
      return this.scope;
   }

   public boolean pinsSelectedSlot() {
      return this.pinsSelectedSlot;
   }
}
