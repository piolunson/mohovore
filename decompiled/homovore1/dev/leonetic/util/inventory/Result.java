package dev.leonetic.util.inventory;

import net.minecraft.class_1268;
import net.minecraft.class_1799;

public record Result(int slot, class_1799 stack, ResultType type, boolean holding) {
   public Result(int slot, class_1799 stack, ResultType type) {
      this(slot, stack, type, isHolding(type, slot));
   }

   static Result fromOffhand(class_1799 stack) {
      return new Result(-1, stack, ResultType.OFFHAND);
   }

   public class_1268 hand() {
      return this.type == ResultType.OFFHAND ? class_1268.field_5810 : class_1268.field_5808;
   }

   public boolean found() {
      return this.type != ResultType.NONE;
   }

   private static boolean isHolding(ResultType type, int slot) {
      return type == ResultType.OFFHAND || type == ResultType.HOTBAR && slot == InventoryUtil.selected();
   }
}
