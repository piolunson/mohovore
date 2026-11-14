package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.TpsManager;
import dev.leonetic.util.ChatUtil;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import java.util.function.Predicate;
import net.minecraft.class_1799;
import net.minecraft.class_2653;

public final class FixesModule extends Module {
   private static final int PLAYER_CONTAINER_ID = 0;
   private static final int FIRST_HOTBAR_SLOT = 36;
   private static final int LAST_HOTBAR_SLOT = 44;
   public final Setting<Boolean> grimAttackVelocity = this.bool("GrimAttackVelocity", true)
      .describe(
         "Skips the slowdown and sprint reset vanilla applies to a knockback hit. Grim does not simulate them, so applying them locally is what gets set back."
      );
   public final Setting<Boolean> slotDragDesync = this.bool("SlotDragDesync", true)
      .describe("Swallows slot drags that carry an unstackable stack, a map or a banner, which desync the container on servers that reject the drag.");
   public final Setting<Boolean> silentSwapFix = this.bool("SilentSwapFix", true)
      .describe("Drops a redundant hotbar SetSlot that would undo a silent swap the client has already predicted.");
   public final Setting<Boolean> setSlotDebug = this.bool("SetSlotDebug", false)
      .describe("Prints every inbound SetSlot, and whether it was dropped, to chat.")
      .visibleWhen(this.silentSwapFix::getValue);
   public final Setting<FixesModule.CooldownSync> cooldownSync = this.mode("CooldownSync", FixesModule.CooldownSync.LATEST)
      .describe("Stretches item cooldowns by how far behind 20 TPS the server is running, so a local cooldown expires no earlier than the server's own.");

   public FixesModule() {
      super("Fixes", Category.EXTRA, "Hotfixes to pick from based on the server and anticheat you are on.");
   }

   public static FixesModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(FixesModule.class);
   }

   private static boolean active(Predicate<FixesModule> fix) {
      FixesModule module = get();
      return module != null && module.isEnabled() && fix.test(module);
   }

   public static boolean cancelsAttackVelocity() {
      return active(module -> module.grimAttackVelocity.getValue());
   }

   public static boolean swallowsSlotDrag() {
      return active(module -> module.slotDragDesync.getValue());
   }

   public static int syncCooldown(int ticks) {
      FixesModule module = get();
      if (module != null && module.isEnabled()) {
         FixesModule.CooldownSync mode = module.cooldownSync.getValue();
         if (mode == FixesModule.CooldownSync.DISABLED) {
            return ticks;
         }

         TpsManager tps = Homovore2Client.tps();
         if (tps == null) {
            return ticks;
         }

         float rate = mode == FixesModule.CooldownSync.LATEST ? tps.latest() : tps.average();
         return !(rate <= 0.0F) && !(rate >= 20.0F) ? Math.round(ticks * (20.0F / rate)) : ticks;
      } else {
         return ticks;
      }
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (this.silentSwapFix.getValue() && event.getPacket() instanceof class_2653 packet && packet.method_11452() == 0) {
         int slot = packet.method_11450();
         if (slot >= 36 && slot <= 44) {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (snapshot.available()) {
               class_1799 incoming = packet.method_11449();
               class_1799 held = snapshot.item(snapshot.selectedSlot());
               boolean redundant = !incoming.method_7960()
                  && !held.method_7960()
                  && class_1799.method_7984(incoming, held)
                  && incoming.method_7947() == held.method_7947();
               if (this.setSlotDebug.getValue()) {
                  this.debug(packet, redundant);
               }

               if (redundant) {
                  event.cancel();
               }
            }
         }
      }
   }

   private void debug(class_2653 packet, boolean dropped) {
      int containerId = packet.method_11452();
      int stateId = packet.method_37439();
      int slot = packet.method_11450();
      String item = packet.method_11449().toString();
      mc.execute(
         () -> ChatUtil.sendPersistent(
            "fixes-setslot",
            ChatUtil.withPrefix("SetSlot container=%s state=%s slot=%s item=%s %s", containerId, stateId, slot, item, dropped ? "(dropped)" : "(passed)")
         )
      );
   }

   public enum CooldownSync {
      DISABLED,
      LATEST,
      AVERAGE;
   }
}
