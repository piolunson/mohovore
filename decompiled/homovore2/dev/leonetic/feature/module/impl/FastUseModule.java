package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.setting.Setting;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_746;

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
         class_746 player = mc.field_1724;
         return player != null && (module.matches(player.method_6047()) || module.matches(player.method_6079()));
      } else {
         return false;
      }
   }

   private boolean matches(class_1799 stack) {
      if (stack.method_7960()) {
         return false;
      } else if (this.all.getValue()) {
         return true;
      } else if (this.experience.getValue() && stack.method_31574(class_1802.field_8287)) {
         return true;
      } else {
         return this.enderChests.getValue() && stack.method_31574(class_1802.field_8466)
            ? true
            : this.blocks.getValue() && stack.method_7909() instanceof class_1747;
      }
   }
}
