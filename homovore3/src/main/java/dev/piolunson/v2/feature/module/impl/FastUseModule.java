package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.module.ModuleManager;
import dev.piolunson.v2.feature.setting.Setting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class FastUseModule extends Module {
   public final Setting<Boolean> all = this.bool("All", false).describe("Removes the delay for every item.");
   public final Setting<Boolean> experience = this.bool("Experience", true).visibleWhen(() -> !this.all.getValue());
   public final Setting<Boolean> blocks = this.bool("Blocks", false).visibleWhen(() -> !this.all.getValue());
   public final Setting<Boolean> enderChests = this.bool("EnderChests", true).visibleWhen(() -> !this.all.getValue() && !this.blocks.getValue());

   public FastUseModule() {
      super("FastUse", Category.EXTRA, "Uses items and places blocks every tick while right click is held.");
   }

   public static FastUseModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(FastUseModule.class);
   }

   public static boolean shouldSkipDelay() {
      FastUseModule module = get();
      if (module != null && module.isEnabled()) {
         ClientPlayerEntity player = mc.player;
         return player != null && (module.matches(player.getMainHandStack()) || module.matches(player.getOffHandStack()));
      } else {
         return false;
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
