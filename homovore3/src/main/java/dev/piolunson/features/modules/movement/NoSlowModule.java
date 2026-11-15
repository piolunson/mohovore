package dev.piolunson.features.modules.movement;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.network.PacketEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.util.Hand;

public class NoSlowModule extends Module {
   public final Setting<Boolean> crawl = this.bool("Crawl", true);
   public final Setting<Boolean> eat = this.bool("Eat", true);
   private static final int SPOOF_DELAY_MOVES = 1;
   private static final int SPOOF_PACKET_COUNT = 37;
   private static final float MIN_EAT_TPS = 15.0F;
   private int movesUntilSlotSpoof = 0;
   private int slotSpoofsRemaining = 0;
   private boolean useSessionActive = false;
   private boolean ignoreNextMovementPacket = false;
   private boolean wasConsuming = false;

   public NoSlowModule() {
      super("NoSlow", "remove slowdown", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.resetSpoof();
      this.wasConsuming = false;
   }

   @Override
   public void onDisable() {
      this.resetSpoof();
      this.wasConsuming = false;
   }

   @Override
   public void onTick() {
      boolean consuming = this.isConsumingMainHandItem();
      if (this.wasConsuming && !consuming) {
         this.resetSpoof();
      }

      this.wasConsuming = consuming;
      if (!consuming && !this.isHoldingUseOnConsumable()) {
         this.resetSpoof();
      }
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (this.isEnabled() && this.eatEnabled()) {
         Packet<?> packet = event.getPacket();
         if (packet instanceof PlayerInteractItemC2SPacket) {
            this.useSessionActive = true;
            this.movesUntilSlotSpoof = 1;
            this.slotSpoofsRemaining = 37;
            this.ignoreNextMovementPacket = true;
         } else {
            if (packet instanceof PlayerMoveC2SPacket move) {
               if (nullCheck() || !this.shouldSpoofActive()) {
                  return;
               }

               if (!move.changesLook() && move.changesPosition()) {
                  event.cancel();
                  mc.getNetworkHandler()
                     .sendPacket(
                        new Full(
                           mc.player.getX(),
                           mc.player.getY(),
                           mc.player.getZ(),
                           mc.player.getYaw(),
                           mc.player.getPitch(),
                           move.isOnGround(),
                           mc.player.horizontalCollision
                        )
                     );
                  return;
               }

               this.handleMovementPacketSend(move);
            }
         }
      }
   }

   private void handleMovementPacketSend(PlayerMoveC2SPacket packet) {
      if (this.shouldSpoofActive() && packet.changesPosition() && packet.changesLook()) {
         if (this.ignoreNextMovementPacket) {
            this.ignoreNextMovementPacket = false;
         } else if (this.movesUntilSlotSpoof > 0) {
            this.movesUntilSlotSpoof--;
         } else if (this.slotSpoofsRemaining > 0) {
            if (mc.player != null && mc.getNetworkHandler() != null) {
               int slot = mc.player.getInventory().getSelectedSlot();
               if (slot >= 0 && slot <= 8) {
                  mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                  this.slotSpoofsRemaining--;
               }
            }
         }
      }
   }

   private boolean eatEnabled() {
      return this.eat.getValue() && Homovore.tpsCounterService.getLatestTPS() > 15.0F;
   }

   private boolean shouldSpoofActive() {
      return this.eatEnabled() && (this.isConsumingMainHandItem() || this.movesUntilSlotSpoof > 0 || this.slotSpoofsRemaining > 0 || this.useSessionActive);
   }

   private void resetSpoof() {
      this.movesUntilSlotSpoof = 0;
      this.slotSpoofsRemaining = 0;
      this.useSessionActive = false;
      this.ignoreNextMovementPacket = false;
   }

   private boolean isConsumingMainHandItem() {
      if (mc.player == null) {
         return false;
      } else {
         return mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND ? this.isConsumable(mc.player.getActiveItem()) : false;
      }
   }

   private boolean isHoldingUseOnConsumable() {
      return mc.player != null && mc.options.useKey.isPressed() ? this.isConsumable(mc.player.getMainHandStack()) : false;
   }

   private boolean isConsumable(ItemStack stack) {
      return !stack.isEmpty() && stack.contains(DataComponentTypes.CONSUMABLE);
   }

   public static boolean shouldCancelConsumeSlow() {
      NoSlowModule module = getInstance();
      return module != null && module.isEnabled() && module.shouldSpoofActive();
   }

   public static NoSlowModule getInstance() {
      return Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(NoSlowModule.class);
   }

   public static boolean isActive(Predicate<NoSlowModule> predicate) {
      NoSlowModule module = getInstance();
      return module != null && module.isEnabled() && predicate.test(module);
   }
}
