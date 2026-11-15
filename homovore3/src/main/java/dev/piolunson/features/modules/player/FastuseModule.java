package dev.piolunson.features.modules.player;

import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class FastuseModule extends Module {
   public final Setting<Boolean> all = this.bool("All", false);
   public final Setting<Boolean> experience = this.bool("Experience", true);
   public final Setting<Boolean> blocks = this.bool("Blocks", false);
   public final Setting<Boolean> enderChests = this.bool("EnderChests", true);

   public FastuseModule() {
      super("Fastuse", "Use items and interact with blocks faster", Module.Category.PLAYER);
   }

   @Override
   public void onTick() {
      if (!nullCheck()) {
         if (this.matches(mc.player.getMainHandStack()) || this.matches(mc.player.getOffHandStack())) {
            mc.itemUseCooldown = 0;
         }
      }
   }

   private boolean matches(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (this.all.getValue()) {
         return true;
      } else if (this.experience.getValue() && stack.isOf(Items.EXPERIENCE_BOTTLE)) {
         return true;
      } else {
         return this.enderChests.getValue() && stack.isOf(Items.ENDER_CHEST) ? true : this.blocks.getValue() && stack.getItem() instanceof BlockItem;
      }
   }
}
