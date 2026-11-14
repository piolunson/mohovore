package dev.leonetic.feature.module.impl.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

final class HudItems {
   private HudItems() {
   }

   static int count(Item item) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return 0;
      }

      int total = 0;
      PlayerInventory inventory = mc.player.getInventory();

      for (int slot = 0; slot < inventory.size(); slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (stack.isOf(item)) {
            total += stack.getCount();
         }
      }

      return total;
   }
}
