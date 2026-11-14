package dev.leonetic.util.inventory;

import dev.leonetic.util.traits.Util;
import java.util.EnumSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class InventoryUtil implements Util {
   static final Result NONE = new Result(-1, ItemStack.EMPTY, ResultType.NONE);
   public static final int OFFHAND_SWAP_BUTTON = 40;
   public static final EnumSet<ResultType> HOTBAR_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.HOTBAR);
   public static final EnumSet<ResultType> INVENTORY_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.INVENTORY);
   public static final EnumSet<ResultType> FULL_SCOPE = EnumSet.of(ResultType.OFFHAND, ResultType.HOTBAR, ResultType.INVENTORY);
   public static final EnumSet<ResultType> PLACE_SCOPE = EnumSet.of(ResultType.HOTBAR, ResultType.INVENTORY);

   private InventoryUtil() {
      throw new AssertionError();
   }

   public static ItemStack cursor() {
      return mc.player.currentScreenHandler.getCursorStack();
   }

   public static int selected() {
      return mc.player.getInventory().getSelectedSlot();
   }

   public static int fireworkRefireTicks(ItemStack stack) {
      FireworksComponent fireworks = (FireworksComponent)stack.get(DataComponentTypes.FIREWORKS);
      int flight = fireworks != null ? fireworks.flightDuration() : 1;
      return 10 * (1 + flight) - 1;
   }

   public static void click(int slot, int button, SlotActionType type) {
      int id = mc.player.currentScreenHandler.syncId;
      mc.interactionManager.clickSlot(id, slot, button, type, mc.player);
   }

   public static void swap(int to) {
      if (to >= 0 && to <= 8) {
         mc.player.getInventory().setSelectedSlot(to);
         mc.interactionManager.syncSelectedSlot();
      }
   }

   public static Result find(Item target, EnumSet<ResultType> scopes) {
      return find(stack -> stack.isOf(target), scopes);
   }

   public static Result find(Predicate<ItemStack> predicate, EnumSet<ResultType> scopes) {
      return find((item, scope) -> scopes.contains(scope) && predicate.test(item));
   }

   public static void swapToOffhand(int inventorySlot) {
      int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
      click(containerSlot, 40, SlotActionType.SWAP);
   }

   public static void swapToHotbarSlot(int inventorySlot, int hotbarSlot) {
      int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
      click(containerSlot, hotbarSlot, SlotActionType.SWAP);
   }

   public static Result find(BiPredicate<ItemStack, ResultType> predicate) {
      ItemStack offhand = mc.player.getOffHandStack();
      if (predicate.test(offhand, ResultType.OFFHAND)) {
         return Result.fromOffhand(offhand);
      }

      for (int i = 0; i < 36; i++) {
         ItemStack item = mc.player.getInventory().getStack(i);
         ResultType type = i < 9 ? ResultType.HOTBAR : ResultType.INVENTORY;
         if (predicate.test(item, type)) {
            return new Result(i, item, type);
         }
      }

      return NONE;
   }
}
