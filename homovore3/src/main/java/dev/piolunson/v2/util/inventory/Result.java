package dev.piolunson.v2.util.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class Result {
   private final int slot;
   private final ItemStack stack;
   private final ResultType type;
   private final boolean holding;
   private final long snapshotVersion;
   private final int menuId;
   private final int menuSlot;

   Result(int slot, ItemStack stack, ResultType type, boolean holding, long snapshotVersion, int menuId, int menuSlot) {
      this.slot = slot;
      this.stack = stack.copy();
      this.type = type;
      this.holding = holding;
      this.snapshotVersion = snapshotVersion;
      this.menuId = menuId;
      this.menuSlot = menuSlot;
   }

   public int slot() {
      return this.slot;
   }

   public ItemStack stack() {
      return this.stack.copy();
   }

   public ResultType type() {
      return this.type;
   }

   public boolean holding() {
      return this.holding;
   }

   public boolean found() {
      return this.type != ResultType.NONE;
   }

   public Hand hand() {
      return this.type == ResultType.OFFHAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
   }

   public long snapshotVersion() {
      return this.snapshotVersion;
   }

   int menuId() {
      return this.menuId;
   }

   int menuSlot() {
      return this.menuSlot;
   }
}
