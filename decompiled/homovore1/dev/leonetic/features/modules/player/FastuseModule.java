package dev.leonetic.features.modules.player;

import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_1802;

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
         if (this.matches(mc.field_1724.method_6047()) || this.matches(mc.field_1724.method_6079())) {
            mc.field_1752 = 0;
         }
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
