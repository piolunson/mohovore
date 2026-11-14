package dev.leonetic.feature.module.impl.hud;

import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_310;

final class HudItems {
   private HudItems() {
   }

   static int count(class_1792 item) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null) {
         return 0;
      }

      int total = 0;
      class_1661 inventory = mc.field_1724.method_31548();

      for (int slot = 0; slot < inventory.method_5439(); slot++) {
         class_1799 stack = inventory.method_5438(slot);
         if (stack.method_31574(item)) {
            total += stack.method_7947();
         }
      }

      return total;
   }
}
