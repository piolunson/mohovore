package dev.leonetic.util.inventory;

import net.minecraft.class_1268;
import net.minecraft.class_1799;

public final class Result {
   private final int slot;
   private final class_1799 stack;
   private final ResultType type;
   private final boolean holding;
   private final long snapshotVersion;
   private final int menuId;
   private final int menuSlot;

   Result(int slot, class_1799 stack, ResultType type, boolean holding, long snapshotVersion, int menuId, int menuSlot) {
      this.slot = slot;
      this.stack = stack.method_7972();
      this.type = type;
      this.holding = holding;
      this.snapshotVersion = snapshotVersion;
      this.menuId = menuId;
      this.menuSlot = menuSlot;
   }

   public int slot() {
      return this.slot;
   }

   public class_1799 stack() {
      return this.stack.method_7972();
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

   public class_1268 hand() {
      return this.type == ResultType.OFFHAND ? class_1268.field_5810 : class_1268.field_5808;
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
