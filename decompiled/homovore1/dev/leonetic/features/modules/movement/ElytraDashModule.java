package dev.leonetic.features.modules.movement;

import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.mixin.entity.FireworkRocketEntityAccessor;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import net.minecraft.class_10192;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1671;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_2848;
import net.minecraft.class_9284;
import net.minecraft.class_9334;
import net.minecraft.class_2848.class_2849;

public class ElytraDashModule extends Module {
   private static final int CHEST_MENU_SLOT = 6;
   private static final double TAKEOFF_VELOCITY = 0.42;
   private static final int OFFHAND_MENU_SLOT = 45;
   private static final int FIRE_TIMEOUT = 40;
   private static final int FRESH_LIFE_TICKS = 6;
   private final Setting<ElytraDashModule.TakeoffMode> takeoffMode = this.mode("Takeoff", ElytraDashModule.TakeoffMode.VELOCITY);
   private final Setting<Integer> minBoostTicks = this.num("MinBoostTicks", 3, 0, 20);
   private ElytraDashModule.Phase phase = ElytraDashModule.Phase.EQUIP;
   private boolean weEquipped;
   private int stowedChestSlot = -1;
   private int ticksSinceRocket = 1073741823;
   private boolean awaitingRocket;

   public ElytraDashModule() {
      super("ElytraDash", "Swap on your elytra, lift straight into flight, and auto-fire fireworks; swaps back on disable.", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.phase = ElytraDashModule.Phase.EQUIP;
      this.weEquipped = false;
      this.stowedChestSlot = -1;
      this.ticksSinceRocket = 1073741823;
   }

   @Override
   public void onDisable() {
      if (this.canClickPlayerInventory() && mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833)) {
         if (this.weEquipped) {
            this.swapChestArmorWith(this.stowedChestSlot);
         } else {
            Result chestplate = InventoryUtil.find(ElytraDashModule::isChestplate, InventoryUtil.FULL_SCOPE);
            if (chestplate.found()) {
               this.swapChestArmorWith(this.containerSlotOf(chestplate));
            }
         }
      }

      this.weEquipped = false;
      this.stowedChestSlot = -1;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         switch (this.phase) {
            case EQUIP:
               this.tickEquip();
               break;
            case TAKEOFF:
               this.tickTakeoff();
               break;
            case FLY:
               this.tickFly();
         }
      }
   }

   private void tickEquip() {
      if (mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833)) {
         this.phase = ElytraDashModule.Phase.TAKEOFF;
      } else if (this.canClickPlayerInventory()) {
         Result elytra = InventoryUtil.find(class_1802.field_8833, InventoryUtil.FULL_SCOPE);
         if (elytra.found()) {
            int elytraSlot = this.containerSlotOf(elytra);
            this.swapChestArmorWith(elytraSlot);
            this.stowedChestSlot = elytraSlot;
            this.weEquipped = true;
            this.phase = ElytraDashModule.Phase.TAKEOFF;
         }
      }
   }

   private void tickTakeoff() {
      if (mc.field_1724.method_6128()) {
         this.enterFlight();
      } else if (mc.method_1562() != null) {
         if (mc.field_1724.method_24828()) {
            switch ((ElytraDashModule.TakeoffMode)this.takeoffMode.getValue()) {
               case VELOCITY:
                  class_243 vel = mc.field_1724.method_18798();
                  mc.field_1724.method_18800(vel.field_1352, 0.42, vel.field_1350);
                  mc.field_1724.method_5728(true);
                  break;
               case VANILLA:
                  mc.field_1724.method_6043();
            }
         } else {
            mc.method_1562().method_52787(new class_2848(mc.field_1724, class_2849.field_12982));
            mc.field_1724.method_23669();
            this.enterFlight();
         }
      }
   }

   private void tickFly() {
      if (!mc.field_1724.method_6128()) {
         this.phase = ElytraDashModule.Phase.TAKEOFF;
      } else {
         this.ticksSinceRocket++;
         if (this.awaitingRocket) {
            if (!this.hasFreshAttachedRocket() && this.ticksSinceRocket < 40) {
               return;
            }

            this.awaitingRocket = false;
         }

         if (!this.hasUsefulBoost()) {
            if (this.fireRocket()) {
               this.awaitingRocket = true;
               this.ticksSinceRocket = 0;
            }
         }
      }
   }

   private boolean hasFreshAttachedRocket() {
      for (class_1297 e : mc.field_1687.method_18112()) {
         if (e instanceof class_1671 rocket
            && ((FireworkRocketEntityAccessor)rocket).homovore$getAttachedToEntity() == mc.field_1724
            && ((FireworkRocketEntityAccessor)rocket).homovore$getLife() <= 6) {
            return true;
         }
      }

      return false;
   }

   private boolean hasUsefulBoost() {
      int bestRemaining = Integer.MIN_VALUE;

      for (class_1297 e : mc.field_1687.method_18112()) {
         if (e instanceof class_1671 rocket && ((FireworkRocketEntityAccessor)rocket).homovore$getAttachedToEntity() == mc.field_1724) {
            int remaining = this.estimateLifetime(rocket) - ((FireworkRocketEntityAccessor)rocket).homovore$getLife();
            if (remaining > bestRemaining) {
               bestRemaining = remaining;
            }
         }
      }

      return bestRemaining > this.minBoostTicks.getValue();
   }

   private int estimateLifetime(class_1671 rocket) {
      class_1799 stack = (class_1799)rocket.method_5841().method_12789(FireworkRocketEntityAccessor.homovore$getFireworksItemData());
      class_9284 fireworks = (class_9284)stack.method_58694(class_9334.field_49616);
      int flight = fireworks != null ? fireworks.comp_2391() : 1;
      return 10 * (flight + 1);
   }

   private void enterFlight() {
      this.phase = ElytraDashModule.Phase.FLY;
      this.ticksSinceRocket = 1073741823;
      this.awaitingRocket = false;
   }

   private boolean fireRocket() {
      Result rocket = InventoryUtil.find(class_1802.field_8639, InventoryUtil.FULL_SCOPE);
      if (!rocket.found()) {
         return false;
      }

      if (!this.canClickPlayerInventory()) {
         return false;
      }

      if (rocket.type() == ResultType.HOTBAR && rocket.slot() == InventoryUtil.selected()) {
         mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
         return true;
      }

      int containerSlot = this.containerSlotOf(rocket);
      InventoryUtil.click(containerSlot, InventoryUtil.selected(), class_1713.field_7791);

      try {
         mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
      } finally {
         InventoryUtil.click(containerSlot, InventoryUtil.selected(), class_1713.field_7791);
      }

      return true;
   }

   private void swapChestArmorWith(int itemContainerSlot) {
      InventoryUtil.click(itemContainerSlot, 0, class_1713.field_7790);
      InventoryUtil.click(6, 0, class_1713.field_7790);
      InventoryUtil.click(itemContainerSlot, 0, class_1713.field_7790);
   }

   private int containerSlotOf(Result result) {
      if (result.type() == ResultType.OFFHAND) {
         return 45;
      }

      int slot = result.slot();
      return slot < 9 ? slot + 36 : slot;
   }

   private static boolean isChestplate(class_1799 stack) {
      if (stack.method_31574(class_1802.field_8833)) {
         return false;
      }

      class_10192 equippable = (class_10192)stack.method_58694(class_9334.field_54196);
      return equippable != null && equippable.comp_3174() == class_1304.field_6174;
   }

   private boolean canClickPlayerInventory() {
      return mc.field_1761 != null && mc.field_1724.field_7512 == mc.field_1724.field_7498;
   }

   @Override
   public String getDisplayInfo() {
      return switch (this.phase) {
         case EQUIP -> "Equipping";
         case TAKEOFF -> "Takeoff";
         case FLY -> "Flying";
      };
   }

   private enum Phase {
      EQUIP,
      TAKEOFF,
      FLY;
   }

   public enum TakeoffMode {
      VELOCITY,
      VANILLA;
   }
}
