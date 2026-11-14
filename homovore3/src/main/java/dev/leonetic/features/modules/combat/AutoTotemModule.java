package dev.leonetic.features.modules.combat;

import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.util.inventory.InventoryUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class AutoTotemModule extends Module {
   public AutoTotemModule() {
      super("AutoTotem", "Ensures a totem is in your offhand.", Module.Category.COMBAT);
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (!nullCheck()) {
         if (event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {
               mc.execute(() -> {
                  if (!nullCheck()) {
                     if (mc.player.currentScreenHandler.syncId == 0) {
                        mc.player.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
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
         if (mc.player.currentScreenHandler.syncId == 0) {
            if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
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
         if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
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
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
            count += stack.getCount();
         }
      }

      return String.valueOf(count);
   }
}
