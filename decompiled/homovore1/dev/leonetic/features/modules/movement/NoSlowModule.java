package dev.leonetic.features.modules.movement;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_2596;
import net.minecraft.class_2828;
import net.minecraft.class_2868;
import net.minecraft.class_2886;
import net.minecraft.class_9334;
import net.minecraft.class_2828.class_2830;

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
         class_2596<?> packet = event.getPacket();
         if (packet instanceof class_2886) {
            this.useSessionActive = true;
            this.movesUntilSlotSpoof = 1;
            this.slotSpoofsRemaining = 37;
            this.ignoreNextMovementPacket = true;
         } else {
            if (packet instanceof class_2828 move) {
               if (nullCheck() || !this.shouldSpoofActive()) {
                  return;
               }

               if (!move.method_36172() && move.method_36171()) {
                  event.cancel();
                  mc.method_1562()
                     .method_52787(
                        new class_2830(
                           mc.field_1724.method_23317(),
                           mc.field_1724.method_23318(),
                           mc.field_1724.method_23321(),
                           mc.field_1724.method_36454(),
                           mc.field_1724.method_36455(),
                           move.method_12273(),
                           mc.field_1724.field_5976
                        )
                     );
                  return;
               }

               this.handleMovementPacketSend(move);
            }
         }
      }
   }

   private void handleMovementPacketSend(class_2828 packet) {
      if (this.shouldSpoofActive() && packet.method_36171() && packet.method_36172()) {
         if (this.ignoreNextMovementPacket) {
            this.ignoreNextMovementPacket = false;
         } else if (this.movesUntilSlotSpoof > 0) {
            this.movesUntilSlotSpoof--;
         } else if (this.slotSpoofsRemaining > 0) {
            if (mc.field_1724 != null && mc.method_1562() != null) {
               int slot = mc.field_1724.method_31548().method_67532();
               if (slot >= 0 && slot <= 8) {
                  mc.method_1562().method_52787(new class_2868(slot));
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
      if (mc.field_1724 == null) {
         return false;
      } else {
         return mc.field_1724.method_6115() && mc.field_1724.method_6058() == class_1268.field_5808 ? this.isConsumable(mc.field_1724.method_6030()) : false;
      }
   }

   private boolean isHoldingUseOnConsumable() {
      return mc.field_1724 != null && mc.field_1690.field_1904.method_1434() ? this.isConsumable(mc.field_1724.method_6047()) : false;
   }

   private boolean isConsumable(class_1799 stack) {
      return !stack.method_7960() && stack.method_57826(class_9334.field_53964);
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
