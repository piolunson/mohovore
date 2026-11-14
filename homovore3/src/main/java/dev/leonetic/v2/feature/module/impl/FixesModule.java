package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.network.PacketEvent;
import dev.leonetic.v2.event.system.Subscribe;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.module.ModuleManager;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.TpsManager;
import dev.leonetic.v2.util.ChatUtil;
import dev.leonetic.v2.util.inventory.InventorySnapshot;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;

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
      if (this.silentSwapFix.getValue() && event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket packet && packet.getSyncId() == 0) {
         int slot = packet.getSlot();
         if (slot >= 36 && slot <= 44) {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (snapshot.available()) {
               ItemStack incoming = packet.getStack();
               ItemStack held = snapshot.item(snapshot.selectedSlot());
               boolean redundant = !incoming.isEmpty() && !held.isEmpty() && ItemStack.areItemsEqual(incoming, held) && incoming.getCount() == held.getCount();
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

   private void debug(ScreenHandlerSlotUpdateS2CPacket packet, boolean dropped) {
      int containerId = packet.getSyncId();
      int stateId = packet.getRevision();
      int slot = packet.getSlot();
      String item = packet.getStack().toString();
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
