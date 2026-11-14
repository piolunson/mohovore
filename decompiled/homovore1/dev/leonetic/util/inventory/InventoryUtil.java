package dev.leonetic.util.inventory;

import dev.leonetic.util.traits.Util;
import java.util.EnumSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_9284;
import net.minecraft.class_9334;

public final class InventoryUtil implements Util {
   static final Result NONE = new Result(-1, class_1799.field_8037, ResultType.NONE);
   public static final int OFFHAND_SWAP_BUTTON = 40;
   public static final EnumSet<ResultType> HOTBAR_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.HOTBAR);
   public static final EnumSet<ResultType> INVENTORY_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.INVENTORY);
   public static final EnumSet<ResultType> FULL_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.HOTBAR, ResultType.INVENTORY);
   public static final EnumSet<ResultType> PLACE_SCOPE = EnumSet.of(ResultType.HOTBAR, ResultType.INVENTORY);

   private InventoryUtil() {
      throw new AssertionError();
   }

   public static class_1799 cursor() {
      return mc.field_1724.field_7512.method_34255();
   }

   public static int selected() {
      return mc.field_1724.method_31548().method_67532();
   }

   public static int fireworkRefireTicks(class_1799 stack) {
      class_9284 fireworks = (class_9284)stack.method_58694(class_9334.field_49616);
      int flight = fireworks != null ? fireworks.comp_2391() : 1;
      return 10 * (1 + flight) - 1;
   }

   public static void click(int slot, int button, class_1713 type) {
      int id = mc.field_1724.field_7512.field_7763;
      mc.field_1761.method_2906(id, slot, button, type, mc.field_1724);
   }

   public static void swap(int to) {
      if (to >= 0 && to <= 8) {
         mc.field_1724.method_31548().method_61496(to);
         mc.field_1761.method_2911();
      }
   }

   public static Result find(class_1792 target, EnumSet<ResultType> scopes) {
      return find(stack -> stack.method_31574(target), scopes);
   }

   public static Result find(Predicate<class_1799> predicate, EnumSet<ResultType> scopes) {
      return find((item, scope) -> scopes.contains(scope) && predicate.test(item));
   }

   public static void swapToOffhand(int inventorySlot) {
      int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
      click(containerSlot, 40, class_1713.field_7791);
   }

   public static void swapToHotbarSlot(int inventorySlot, int hotbarSlot) {
      int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
      click(containerSlot, hotbarSlot, class_1713.field_7791);
   }

   public static Result find(BiPredicate<class_1799, ResultType> predicate) {
      class_1799 offhand = mc.field_1724.method_6079();
      if (predicate.test(offhand, ResultType.OFFHAND)) {
         return Result.fromOffhand(offhand);
      }

      for (int i = 0; i < 36; i++) {
         class_1799 item = mc.field_1724.method_31548().method_5438(i);
         ResultType type = i < 9 ? ResultType.HOTBAR : ResultType.INVENTORY;
         if (predicate.test(item, type)) {
            return new Result(i, item, type);
         }
      }

      return NONE;
   }
}
