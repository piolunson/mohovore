package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.PlayerInputEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.mixin.ClientInputAccessor;
import dev.leonetic.util.BaritoneSupport;
import dev.leonetic.util.ChatUtil;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import net.minecraft.class_10185;
import net.minecraft.class_10192;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_241;
import net.minecraft.class_243;
import net.minecraft.class_2848;
import net.minecraft.class_3532;
import net.minecraft.class_746;
import net.minecraft.class_9334;
import net.minecraft.class_2848.class_2849;

public final class BounceModule extends Module {
   public final Setting<Boolean> lockYaw = this.bool("SnapYaw", true);
   public final Setting<Boolean> freeLook = this.bool("FreeLook", true);
   public final Setting<Boolean> obstaclePasser = this.bool("ObstaclePasser", true);
   public final Setting<Boolean> debug = this.bool("Debug", false);
   private static final float PITCH = 89.5F;
   private static final float MOUSE_SENSITIVITY = 0.15F;
   private static final double REALIGN_DISTANCE = 10.0;
   private static final double MAX_REALIGN_DISTANCE = 80.0;
   private static final double STAGING_ARRIVAL_DISTANCE_SQ = 500.0;
   private static final double SPAWN_GUARD_DISTANCE_SQ = 10000.0;
   private static final double PROGRESS_DISTANCE_SQ = 25.0;
   private static final int STUCK_TICKS = 50;
   private boolean gliding;
   private float travelYaw;
   private int travelY;
   private class_243 travelOrigin = class_243.field_1353;
   private float cameraYaw;
   private float cameraPitch;
   private boolean paused;
   private class_2338 stagingGoal;
   private class_243 lastProgressPos = class_243.field_1353;
   private int stuckTicks;
   private boolean woreElytraOnEnable;
   private boolean weEquipped;
   private int stowedSlot = -1;

   public BounceModule() {
      super("ElytraBounce", Category.MOVEMENT, "Elytra bounce travel.");
      this.obstaclePasser.onChange(this::warnWithoutBaritone);
   }

   private void warnWithoutBaritone() {
      if (this.obstaclePasser.getValue() && !BaritoneSupport.isAvailable()) {
         ChatUtil.send("{red}ObstaclePasser does nothing without Baritone installed.");
      }
   }

   @Override
   public void onEnable() {
      class_746 player = mc.field_1724;
      this.warnWithoutBaritone();
      this.gliding = false;
      this.paused = false;
      this.stagingGoal = null;
      this.stuckTicks = 0;
      this.woreElytraOnEnable = player != null && player.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833);
      this.weEquipped = false;
      this.stowedSlot = -1;
      if (player != null) {
         if (!this.woreElytraOnEnable) {
            this.equipElytra();
         }

         this.cameraYaw = player.method_36454();
         this.cameraPitch = player.method_36455();
         this.lastProgressPos = player.method_73189();
         this.travelYaw = Math.round(player.method_36454() / 45.0F) * 45.0F;
         this.travelY = player.method_31478();
         this.travelOrigin = new class_243(player.method_31477() + 0.5, 0.0, player.method_31479() + 0.5);
      }
   }

   @Override
   public void onDisable() {
      this.stagingGoal = null;
      this.restoreChestplate();
      BaritoneSupport.clearGoal();
   }

   private void equipElytra() {
      Result elytra = InventoryUtil.find(class_1802.field_8833, InventoryUtil.MAIN_INVENTORY_SCOPE);
      if (elytra.found() && InventoryUtil.swapWithEquipment(elytra.slot(), class_1304.field_6174)) {
         this.stowedSlot = elytra.slot();
         this.weEquipped = true;
      }
   }

   private void restoreChestplate() {
      if (this.weEquipped) {
         this.weEquipped = false;
         if (mc.field_1724 != null && mc.field_1724.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833)) {
            int slot = this.restoreSlot();
            this.stowedSlot = -1;
            if (slot >= 0) {
               InventoryUtil.swapWithEquipment(slot, class_1304.field_6174);
            }
         } else {
            this.stowedSlot = -1;
         }
      }
   }

   private int restoreSlot() {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!snapshot.available()) {
         return -1;
      }

      if (this.stowedSlot >= 0 && this.stowedSlot < 36) {
         class_1799 stowed = snapshot.item(this.stowedSlot);
         if (stowed.method_7960() || isChestplate(stowed)) {
            return this.stowedSlot;
         }
      }

      Result chestplate = InventoryUtil.find(BounceModule::isChestplate, InventoryUtil.MAIN_INVENTORY_SCOPE);
      return chestplate.found() ? chestplate.slot() : -1;
   }

   private static boolean isChestplate(class_1799 stack) {
      if (stack.method_31574(class_1802.field_8833)) {
         return false;
      }

      class_10192 equippable = (class_10192)stack.method_58694(class_9334.field_54196);
      return equippable != null && equippable.comp_3174() == class_1304.field_6174;
   }

   public boolean isFlying() {
      return this.isEligible() && !this.paused;
   }

   private boolean isEligible() {
      class_746 player = mc.field_1724;
      return this.isEnabled()
         && player != null
         && !player.method_31549().field_7478
         && player.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833);
   }

   public boolean isSpoofingGlide() {
      return this.isFlying() && this.gliding;
   }

   @Subscribe
   public void onPlayerInput(PlayerInputEvent event) {
      class_746 player = mc.field_1724;
      if (player != null) {
         if (!this.woreElytraOnEnable && !this.weEquipped && !player.method_6118(class_1304.field_6174).method_31574(class_1802.field_8833)) {
            this.equipElytra();
         }

         if (this.isEligible()) {
            this.updateObstaclePasser(player);
            if (this.paused) {
               this.gliding = false;
            } else {
               Homovore2Client.rotations().lock();
               player.method_5728(true);
               this.aim(player);
               if (player.method_24828()) {
                  player.method_6043();
                  this.gliding = false;
               }

               if (!this.gliding && !player.method_6128() && !player.method_24828()) {
                  this.gliding = true;
                  player.field_3944.method_52787(new class_2848(player, class_2849.field_12982));
               }
            }
         }
      }
   }

   private void aim(class_746 player) {
      if (this.lockYaw.getValue()) {
         player.method_36456(this.travelYaw);
         player.method_5847(this.travelYaw);
      }

      player.method_36457(89.5F);
   }

   private void updateObstaclePasser(class_746 player) {
      if (this.obstaclePasser.getValue() && BaritoneSupport.isAvailable()) {
         if (this.stagingGoal != null) {
            if (!(player.method_24515().method_10262(this.stagingGoal) < 500.0)) {
               this.setPaused(true, "walking to staging goal");
               return;
            }

            this.stagingGoal = null;
            BaritoneSupport.clearGoal();
         }

         if (BaritoneSupport.isPathing()) {
            this.setPaused(true, "Baritone pathing");
         } else {
            this.updateProgress(player);
            String reason = this.offTrackReason(player);
            if (reason == null) {
               this.setPaused(false, "on track");
            } else {
               this.setPaused(true, reason);
               this.stuckTicks = 0;
               this.lastProgressPos = player.method_73189();
               this.pathBackToHighway(player);
            }
         }
      } else {
         this.setPaused(false, "passer off");
      }
   }

   private void setPaused(boolean value, String reason) {
      if (this.paused != value) {
         this.debug((value ? "Handing over to Baritone: " : "Taking the player back: ") + reason);
      }

      this.paused = value;
   }

   private void debug(String message) {
      if (this.debug.getValue()) {
         ChatUtil.send("%s", message);
      }
   }

   private void updateProgress(class_746 player) {
      if (player.method_73189().method_1025(this.lastProgressPos) < 25.0) {
         this.stuckTicks++;
      } else {
         this.stuckTicks = 0;
         this.lastProgressPos = player.method_73189();
      }
   }

   private String offTrackReason(class_746 player) {
      if (player.method_73189().method_37268() < 10000.0) {
         return null;
      } else if (player.method_23318() < this.travelY || player.method_23318() > this.travelY + 2) {
         return "off the highway floor";
      } else if (player.field_5976 && !player.field_34927) {
         return "hit something";
      } else {
         return this.stuckTicks > 50 ? "going nowhere" : null;
      }
   }

   private void pathBackToHighway(class_746 player) {
      class_1937 level = player.method_73183();
      class_243 axis = yawToDirection(this.travelYaw);
      double travelled = player.method_73189().method_1020(this.travelOrigin).method_18805(1.0, 0.0, 1.0).method_1026(axis);
      class_2338 furthestLoaded = null;

      for (double ahead = 10.0; ahead <= 80.0; ahead++) {
         class_243 pos = this.travelOrigin.method_1019(axis.method_1021(travelled + ahead));
         class_2338 goal = class_2338.method_49637(pos.field_1352, this.travelY, pos.field_1350);
         if (!level.method_8477(goal)) {
            break;
         }

         furthestLoaded = goal;
         if (this.isLandable(level, goal)) {
            this.debug("Goal " + goal.method_23854() + ", " + (int)ahead + " ahead");
            BaritoneSupport.pathTo(goal);
            return;
         }
      }

      if (furthestLoaded != null && player.method_24515().method_10262(furthestLoaded) > 500.0) {
         this.debug("Staging goal " + furthestLoaded.method_23854());
         this.stagingGoal = furthestLoaded;
         BaritoneSupport.pathTo(furthestLoaded);
      } else {
         this.debug("Nothing to path to; carrying on bouncing");
         this.setPaused(false, "no goal ahead");
      }
   }

   private boolean isLandable(class_1937 level, class_2338 pos) {
      class_2338 below = pos.method_10074();
      return level.method_8320(pos).method_26215()
         && !level.method_8320(pos).method_27852(class_2246.field_10316)
         && level.method_8320(below).method_26206(level, below, class_2350.field_11036);
   }

   private static class_243 yawToDirection(double yaw) {
      double radians = Math.toRadians(yaw);
      return new class_243(-Math.sin(radians), 0.0, Math.cos(radians));
   }

   public static void suppressMovementInput(class_746 player) {
      BounceModule bounce = get();
      if (bounce != null && bounce.isFlying()) {
         class_10185 real = player.field_3913.field_54155;
         player.field_3913.field_54155 = new class_10185(false, false, false, false, real.comp_3163(), real.comp_3164(), real.comp_3165());
         ((ClientInputAccessor)player.field_3913).homovore$setMoveVector(class_241.field_1340);
      }
   }

   public boolean isFreeLooking() {
      return this.isFlying() && this.freeLook.getValue();
   }

   public void turnCamera(double yRotDelta, double xRotDelta) {
      this.cameraYaw += (float)yRotDelta * 0.15F;
      this.cameraPitch = class_3532.method_15363(this.cameraPitch + (float)xRotDelta * 0.15F, -90.0F, 90.0F);
   }

   public float getCameraYaw() {
      return this.cameraYaw;
   }

   public float getCameraPitch() {
      return this.cameraPitch;
   }

   public static BounceModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(BounceModule.class);
   }

   public static BounceModule flying() {
      BounceModule bounce = get();
      return bounce != null && bounce.isFlying() ? bounce : null;
   }

   public static BounceModule freeLooking() {
      BounceModule bounce = get();
      return bounce != null && bounce.isFreeLooking() ? bounce : null;
   }

   public static boolean isSpoofingGlideNow() {
      BounceModule bounce = get();
      return bounce != null && bounce.isSpoofingGlide();
   }
}
