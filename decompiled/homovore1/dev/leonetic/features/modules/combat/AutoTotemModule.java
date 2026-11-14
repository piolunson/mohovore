package dev.leonetic.features.modules.combat;

import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.util.inventory.InventoryUtil;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2663;

public class AutoTotemModule extends Module {
   public AutoTotemModule() {
      super("AutoTotem", "Ensures a totem is in your offhand.", Module.Category.COMBAT);
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (!nullCheck()) {
         if (event.getPacket() instanceof class_2663 packet) {
            if (packet.method_11470() == 35 && packet.method_11469(mc.field_1687) == mc.field_1724) {
               mc.execute(() -> {
                  if (!nullCheck()) {
                     if (mc.field_1724.field_7512.field_7763 == 0) {
                        mc.field_1724.method_5673(class_1304.field_6171, class_1799.field_8037);
                        this.refillOffhandTotem();
                     }
                  }
               });
            }
         }
      }
   }

   @Override
   public void onTick() {
      if (!nullCheck()) {
         if (mc.field_1724.field_7512.field_7763 == 0) {
            if (!mc.field_1724.method_6079().method_31574(class_1802.field_8288)) {
               this.refillOffhandTotem();
            }
         }
      }
   }

   private void refillOffhandTotem() {
      int slot = this.findTotem();
      if (slot != -1) {
         InventoryUtil.swapToOffhand(slot);
      }
   }

   private int findTotem() {
      for (int i = 0; i < 36; i++) {
         if (mc.field_1724.method_31548().method_5438(i).method_31574(class_1802.field_8288)) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public String getDisplayInfo() {
      if (nullCheck()) {
         return null;
      }

      int count = 0;

      for (int i = 0; i < 36; i++) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(class_1802.field_8288)) {
            count += stack.method_7947();
         }
      }

      return String.valueOf(count);
   }
}
