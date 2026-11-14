package dev.leonetic.features.modules.render;

import dev.leonetic.features.modules.Module;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_3489;

public class CrystalHandModule extends Module {
   public CrystalHandModule() {
      super("CrystalHand", "Renders your sword as an end crystal if you have crystals in your inventory", Module.Category.RENDER);
   }

   public boolean hasCrystalsInInventory() {
      if (nullCheck()) {
         return false;
      }

      for (int i = 0; i < 36; i++) {
         if (mc.field_1724.method_31548().method_5438(i).method_31574(class_1802.field_8301)) {
            return true;
         }
      }

      return false;
   }

   public class_1799 getDisplayStack(class_1799 original) {
      return original.method_31573(class_3489.field_42611) && this.hasCrystalsInInventory() ? new class_1799(class_1802.field_8301) : original;
   }
}
