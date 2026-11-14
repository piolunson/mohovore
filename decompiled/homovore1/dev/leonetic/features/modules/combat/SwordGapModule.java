package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import java.util.EnumSet;
import net.minecraft.class_1802;
import net.minecraft.class_3489;

public class SwordGapModule extends Module {
   private boolean latched;

   public SwordGapModule() {
      super("SwordGap", "Switches from sword to gap while holding right click.", Module.Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.releaseLatch();
   }

   @Override
   public void onTick() {
      if (nullCheck()) {
         this.releaseLatch();
      } else {
         if (this.latched && !Homovore.swapManager.isLatched()) {
            this.latched = false;
         }

         boolean useHeld = mc.field_1690.field_1904.method_1434();
         if (useHeld && !this.latched) {
            if (!mc.field_1724.method_6047().method_31573(class_3489.field_42611)) {
               return;
            }

            Result apple = InventoryUtil.find(class_1802.field_8367, EnumSet.of(ResultType.HOTBAR));
            if (apple.found() && Homovore.swapManager.latch(apple)) {
               this.latched = true;
            }
         } else if (!useHeld && this.latched) {
            this.releaseLatch();
         }
      }
   }

   private void releaseLatch() {
      if (this.latched) {
         Homovore.swapManager.release();
         this.latched = false;
      }
   }
}
