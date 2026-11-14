package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.ChatUtil;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_2879;
import net.minecraft.class_746;

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
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1761 == null || !mc.field_1761.method_2920().method_8388()) {
         this.awaitingPop = false;
      } else if (player.method_29504()) {
         this.setEnabled(false);
      } else if (this.health.getValue() && player.method_6032() + player.method_6067() <= this.healthThreshold.getValue()) {
         this.leave("health");
      } else if (!this.totems.getValue()) {
         this.awaitingPop = false;
      } else {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (snapshot.available()) {
            int count = countTotems(snapshot);
            if (this.awaitingPop) {
               boolean popped = count < this.totemsAtWaitStart;
               boolean rechambered = snapshot.offhand().method_31574(class_1802.field_8288);
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

      if (snapshot.offhand().method_31574(class_1802.field_8288)) {
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

   private static int totemsIn(class_1799 stack) {
      return stack.method_31574(class_1802.field_8288) ? stack.method_7947() : 0;
   }

   private void leave(String reason) {
      this.awaitingPop = false;
      class_746 player = mc.field_1724;
      if (player == null) {
         this.setEnabled(false);
      } else {
         ChatUtil.send("Logging out: low %s.", reason);
         if (this.method.getValue() == AutoLogModule.Method.QUIT) {
            player.field_3944.method_48296().method_10747(class_2561.method_43470("AutoLog"));
         } else {
            for (int i = 0; i < 1250; i++) {
               player.field_3944.method_52787(new class_2879(class_1268.field_5808));
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
