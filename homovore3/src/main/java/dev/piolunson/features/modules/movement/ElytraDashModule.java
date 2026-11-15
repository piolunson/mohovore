package dev.piolunson.features.modules.movement;

import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.mixin.entity.FireworkRocketEntityAccessor;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.ResultType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

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
      if (this.canClickPlayerInventory() && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
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
      if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         this.phase = ElytraDashModule.Phase.TAKEOFF;
      } else if (this.canClickPlayerInventory()) {
         Result elytra = InventoryUtil.find(Items.ELYTRA, InventoryUtil.FULL_SCOPE);
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
      if (mc.player.isGliding()) {
         this.enterFlight();
      } else if (mc.getNetworkHandler() != null) {
         if (mc.player.isOnGround()) {
            switch ((ElytraDashModule.TakeoffMode)this.takeoffMode.getValue()) {
               case VELOCITY:
                  Vec3d vel = mc.player.getVelocity();
                  mc.player.setVelocity(vel.x, 0.42, vel.z);
                  mc.player.setSprinting(true);
                  break;
               case VANILLA:
                  mc.player.jump();
            }
         } else {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
            mc.player.startGliding();
            this.enterFlight();
         }
      }
   }

   private void tickFly() {
      if (!mc.player.isGliding()) {
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
      for (Entity e : mc.world.getEntities()) {
         if (e instanceof FireworkRocketEntity rocket
            && ((FireworkRocketEntityAccessor)rocket).homovore$getAttachedToEntity() == mc.player
            && ((FireworkRocketEntityAccessor)rocket).homovore$getLife() <= 6) {
            return true;
         }
      }

      return false;
   }

   private boolean hasUsefulBoost() {
      int bestRemaining = Integer.MIN_VALUE;

      for (Entity e : mc.world.getEntities()) {
         if (e instanceof FireworkRocketEntity rocket && ((FireworkRocketEntityAccessor)rocket).homovore$getAttachedToEntity() == mc.player) {
            int remaining = this.estimateLifetime(rocket) - ((FireworkRocketEntityAccessor)rocket).homovore$getLife();
            if (remaining > bestRemaining) {
               bestRemaining = remaining;
            }
         }
      }

      return bestRemaining > this.minBoostTicks.getValue();
   }

   private int estimateLifetime(FireworkRocketEntity rocket) {
      ItemStack stack = (ItemStack)rocket.getDataTracker().get(FireworkRocketEntityAccessor.homovore$getFireworksItemData());
      FireworksComponent fireworks = (FireworksComponent)stack.get(DataComponentTypes.FIREWORKS);
      int flight = fireworks != null ? fireworks.flightDuration() : 1;
      return 10 * (flight + 1);
   }

   private void enterFlight() {
      this.phase = ElytraDashModule.Phase.FLY;
      this.ticksSinceRocket = 1073741823;
      this.awaitingRocket = false;
   }

   private boolean fireRocket() {
      Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
      if (!rocket.found()) {
         return false;
      }

      if (!this.canClickPlayerInventory()) {
         return false;
      }

      if (rocket.type() == ResultType.HOTBAR && rocket.slot() == InventoryUtil.selected()) {
         mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
         return true;
      }

      int containerSlot = this.containerSlotOf(rocket);
      InventoryUtil.click(containerSlot, InventoryUtil.selected(), SlotActionType.SWAP);

      try {
         mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
      } finally {
         InventoryUtil.click(containerSlot, InventoryUtil.selected(), SlotActionType.SWAP);
      }

      return true;
   }

   private void swapChestArmorWith(int itemContainerSlot) {
      InventoryUtil.click(itemContainerSlot, 0, SlotActionType.PICKUP);
      InventoryUtil.click(6, 0, SlotActionType.PICKUP);
      InventoryUtil.click(itemContainerSlot, 0, SlotActionType.PICKUP);
   }

   private int containerSlotOf(Result result) {
      if (result.type() == ResultType.OFFHAND) {
         return 45;
      }

      int slot = result.slot();
      return slot < 9 ? slot + 36 : slot;
   }

   private static boolean isChestplate(ItemStack stack) {
      if (stack.isOf(Items.ELYTRA)) {
         return false;
      }

      EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
   }

   private boolean canClickPlayerInventory() {
      return mc.interactionManager != null && mc.player.currentScreenHandler == mc.player.playerScreenHandler;
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
