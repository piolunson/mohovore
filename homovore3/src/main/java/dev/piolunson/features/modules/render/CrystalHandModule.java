package dev.piolunson.features.modules.render;

import dev.piolunson.features.modules.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public class CrystalHandModule extends Module {
   public CrystalHandModule() {
      super("CrystalHand", "Renders your sword as an end crystal if you have crystals in your inventory", Module.Category.RENDER);
   }

   public boolean hasCrystalsInInventory() {
      if (nullCheck()) {
         return false;
      }

      for (int i = 0; i < 36; i++) {
         if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
            return true;
         }
      }

      return false;
   }

   public ItemStack getDisplayStack(ItemStack original) {
      return original.isIn(ItemTags.SWORDS) && this.hasCrystalsInInventory() ? new ItemStack(Items.END_CRYSTAL) : original;
   }
}
