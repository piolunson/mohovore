package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.util.ChatUtil;
import dev.leonetic.v2.util.inventory.InventorySnapshot;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

public final class AutoLogModule extends Module {
   private static final int FLOOD_COUNT = 1250;
   private static final long RECHAMBER_TIMEOUT_MS = 200L;
   private static final int INVENTORY_SIZE = 36;
   public final Setting<AutoLogModule.Method> method = this.mode("Method", AutoLogModule.Method.QUIT)
      .describe("Flood gets you kicked for spam; Quit closes the connection cleanly.");
   public final Setting<Boolean> health = this.bool("Health", false).describe("Leave when health and absorption together fall to the threshold.");
   public final Setting<Double> healthThreshold = this.number("HealthThreshold", (double)6.0, (double)1.0, (double)36.0)
      .describe("Health plus absorption at or below which the leave fires.")
      .visibleWhen(() -> this.health.getValue());
   public final Setting<Boolean> totems = this.bool("Totems", false).describe("Leave when the totems you are carrying run down to the threshold.");
   public final Setting<Integer> totemThreshold = this.integer("TotemThreshold", 1, 0, 36)
      .describe("Totems left, inventory and offhand together, at or below which to leave.")
      .visibleWhen(() -> this.totems.getValue());
   private boolean awaitingPop;
   private int totemsAtWaitStart;
   private long popDeadline;

   public AutoLogModule() {
      super("AutoLog", Category.SAFETY, "Leaves the server when health or totems run low.");
   }

   @Override
   public void onEnable() {
      this.awaitingPop = false;
   }

   @Override
   public void onDisable() {
      this.awaitingPop = false;
   }

   @Override
   public void onTick() {
      ClientPlayerEntity player = mc.player;
      if (player == null || mc.interactionManager == null || !mc.interactionManager.getCurrentGameMode().isSurvivalLike()) {
         this.awaitingPop = false;
      } else if (player.isDead()) {
         this.setEnabled(false);
      } else if (this.health.getValue() && player.getHealth() + player.getAbsorptionAmount() <= this.healthThreshold.getValue()) {
         this.leave("health");
      } else if (!this.totems.getValue()) {
         this.awaitingPop = false;
      } else {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (snapshot.available()) {
            int count = countTotems(snapshot);
            if (this.awaitingPop) {
               boolean popped = count < this.totemsAtWaitStart;
               boolean rechambered = snapshot.offhand().isOf(Items.TOTEM_OF_UNDYING);
               if (popped || rechambered || count == 0 || System.currentTimeMillis() >= this.popDeadline) {
                  this.leave("totems");
               }
            } else if (count <= this.totemThreshold.getValue()) {
               if (this.shouldWaitForPop(snapshot)) {
                  this.awaitingPop = true;
                  this.totemsAtWaitStart = count;
                  this.popDeadline = System.currentTimeMillis() + 200L;
               } else {
                  this.leave("totems");
               }
            }
         }
      }
   }

   private boolean shouldWaitForPop(InventorySnapshot snapshot) {
      if (this.totemThreshold.getValue() < 2) {
         return false;
      }

      if (snapshot.offhand().isOf(Items.TOTEM_OF_UNDYING)) {
         return false;
      }

      AutoTotemModule autoTotem = Homovore2Client.modules().get(AutoTotemModule.class);
      return autoTotem != null && autoTotem.isEnabled();
   }

   private static int countTotems(InventorySnapshot snapshot) {
      int count = 0;

      for (int slot = 0; slot < Math.min(36, snapshot.size()); slot++) {
         count += totemsIn(snapshot.item(slot));
      }

      return count + totemsIn(snapshot.offhand());
   }

   private static int totemsIn(ItemStack stack) {
      return stack.isOf(Items.TOTEM_OF_UNDYING) ? stack.getCount() : 0;
   }

   private void leave(String reason) {
      this.awaitingPop = false;
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         this.setEnabled(false);
      } else {
         ChatUtil.send("Logging out: low %s.", reason);
         if (this.method.getValue() == AutoLogModule.Method.QUIT) {
            player.networkHandler.getConnection().disconnect(Text.literal("AutoLog"));
         } else {
            for (int i = 0; i < 1250; i++) {
               player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
         }

         this.setEnabled(false);
      }
   }

   public enum Method {
      FLOOD,
      QUIT;
   }
}
