package dev.leonetic.util.world;

import java.util.Set;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class PlayerGear {
   private static final Set<Item> GEAR = Set.of(
      Items.DIAMOND_HELMET,
      Items.DIAMOND_CHESTPLATE,
      Items.DIAMOND_LEGGINGS,
      Items.DIAMOND_BOOTS,
      Items.NETHERITE_HELMET,
      Items.NETHERITE_CHESTPLATE,
      Items.NETHERITE_LEGGINGS,
      Items.NETHERITE_BOOTS
   );
   private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   private PlayerGear() {
   }

   public static boolean isGeared(PlayerEntity player) {
      for (EquipmentSlot slot : ARMOR) {
         ItemStack stack = player.getEquippedStack(slot);
         if (stack.isEmpty() || !GEAR.contains(stack.getItem())) {
            return false;
         }
      }

      return true;
   }
}
