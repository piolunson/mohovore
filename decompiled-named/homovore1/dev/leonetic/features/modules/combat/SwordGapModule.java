package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import java.util.EnumSet;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

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

         boolean useHeld = mc.options.useKey.isPressed();
         if (useHeld && !this.latched) {
            if (!mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) {
               return;
            }

            Result apple = InventoryUtil.find(Items.ENCHANTED_GOLDEN_APPLE, EnumSet.of(ResultType.HOTBAR));
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
