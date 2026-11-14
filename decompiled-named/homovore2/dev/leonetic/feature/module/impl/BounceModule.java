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
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
   private Vec3d travelOrigin = Vec3d.ZERO;
   private float cameraYaw;
   private float cameraPitch;
   private boolean paused;
   private BlockPos stagingGoal;
   private Vec3d lastProgressPos = Vec3d.ZERO;
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
      ClientPlayerEntity player = mc.player;
      this.warnWithoutBaritone();
      this.gliding = false;
      this.paused = false;
      this.stagingGoal = null;
      this.stuckTicks = 0;
      this.woreElytraOnEnable = player != null && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
      this.weEquipped = false;
      this.stowedSlot = -1;
      if (player != null) {
         if (!this.woreElytraOnEnable) {
            this.equipElytra();
         }

         this.cameraYaw = player.getYaw();
         this.cameraPitch = player.getPitch();
         this.lastProgressPos = player.getEntityPos();
         this.travelYaw = Math.round(player.getYaw() / 45.0F) * 45.0F;
         this.travelY = player.getBlockY();
         this.travelOrigin = new Vec3d(player.getBlockX() + 0.5, 0.0, player.getBlockZ() + 0.5);
      }
   }

   @Override
   public void onDisable() {
      this.stagingGoal = null;
      this.restoreChestplate();
      BaritoneSupport.clearGoal();
   }

   private void equipElytra() {
      Result elytra = InventoryUtil.find(Items.ELYTRA, InventoryUtil.MAIN_INVENTORY_SCOPE);
      if (elytra.found() && InventoryUtil.swapWithEquipment(elytra.slot(), EquipmentSlot.CHEST)) {
         this.stowedSlot = elytra.slot();
         this.weEquipped = true;
      }
   }

   private void restoreChestplate() {
      if (this.weEquipped) {
         this.weEquipped = false;
         if (mc.player != null && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            int slot = this.restoreSlot();
            this.stowedSlot = -1;
            if (slot >= 0) {
               InventoryUtil.swapWithEquipment(slot, EquipmentSlot.CHEST);
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
         ItemStack stowed = snapshot.item(this.stowedSlot);
         if (stowed.isEmpty() || isChestplate(stowed)) {
            return this.stowedSlot;
         }
      }

      Result chestplate = InventoryUtil.find(BounceModule::isChestplate, InventoryUtil.MAIN_INVENTORY_SCOPE);
      return chestplate.found() ? chestplate.slot() : -1;
   }

   private static boolean isChestplate(ItemStack stack) {
      if (stack.isOf(Items.ELYTRA)) {
         return false;
      }

      EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
   }

   public boolean isFlying() {
      return this.isEligible() && !this.paused;
   }

   private boolean isEligible() {
      ClientPlayerEntity player = mc.player;
      return this.isEnabled() && player != null && !player.getAbilities().allowFlying && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
   }

   public boolean isSpoofingGlide() {
      return this.isFlying() && this.gliding;
   }

   @Subscribe
   public void onPlayerInput(PlayerInputEvent event) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         if (!this.woreElytraOnEnable && !this.weEquipped && !player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            this.equipElytra();
         }

         if (this.isEligible()) {
            this.updateObstaclePasser(player);
            if (this.paused) {
               this.gliding = false;
            } else {
               Homovore2Client.rotations().lock();
               player.setSprinting(true);
               this.aim(player);
               if (player.isOnGround()) {
                  player.jump();
                  this.gliding = false;
               }

               if (!this.gliding && !player.isGliding() && !player.isOnGround()) {
                  this.gliding = true;
                  player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
               }
            }
         }
      }
   }

   private void aim(ClientPlayerEntity player) {
      if (this.lockYaw.getValue()) {
         player.setYaw(this.travelYaw);
         player.setHeadYaw(this.travelYaw);
      }

      player.setPitch(89.5F);
   }

   private void updateObstaclePasser(ClientPlayerEntity player) {
      if (this.obstaclePasser.getValue() && BaritoneSupport.isAvailable()) {
         if (this.stagingGoal != null) {
            if (!(player.getBlockPos().getSquaredDistance(this.stagingGoal) < 500.0)) {
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
               this.lastProgressPos = player.getEntityPos();
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

   private void updateProgress(ClientPlayerEntity player) {
      if (player.getEntityPos().squaredDistanceTo(this.lastProgressPos) < 25.0) {
         this.stuckTicks++;
      } else {
         this.stuckTicks = 0;
         this.lastProgressPos = player.getEntityPos();
      }
   }

   private String offTrackReason(ClientPlayerEntity player) {
      if (player.getEntityPos().horizontalLengthSquared() < 10000.0) {
         return null;
      } else if (player.getY() < this.travelY || player.getY() > this.travelY + 2) {
         return "off the highway floor";
      } else if (player.horizontalCollision && !player.collidedSoftly) {
         return "hit something";
      } else {
         return this.stuckTicks > 50 ? "going nowhere" : null;
      }
   }

   private void pathBackToHighway(ClientPlayerEntity player) {
      World level = player.getEntityWorld();
      Vec3d axis = yawToDirection(this.travelYaw);
      double travelled = player.getEntityPos().subtract(this.travelOrigin).multiply(1.0, 0.0, 1.0).dotProduct(axis);
      BlockPos furthestLoaded = null;

      for (double ahead = 10.0; ahead <= 80.0; ahead++) {
         Vec3d pos = this.travelOrigin.add(axis.multiply(travelled + ahead));
         BlockPos goal = BlockPos.ofFloored(pos.x, this.travelY, pos.z);
         if (!level.isPosLoaded(goal)) {
            break;
         }

         furthestLoaded = goal;
         if (this.isLandable(level, goal)) {
            this.debug("Goal " + goal.toShortString() + ", " + (int)ahead + " ahead");
            BaritoneSupport.pathTo(goal);
            return;
         }
      }

      if (furthestLoaded != null && player.getBlockPos().getSquaredDistance(furthestLoaded) > 500.0) {
         this.debug("Staging goal " + furthestLoaded.toShortString());
         this.stagingGoal = furthestLoaded;
         BaritoneSupport.pathTo(furthestLoaded);
      } else {
         this.debug("Nothing to path to; carrying on bouncing");
         this.setPaused(false, "no goal ahead");
      }
   }

   private boolean isLandable(World level, BlockPos pos) {
      BlockPos below = pos.down();
      return level.getBlockState(pos).isAir()
         && !level.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)
         && level.getBlockState(below).isSideSolidFullSquare(level, below, Direction.UP);
   }

   private static Vec3d yawToDirection(double yaw) {
      double radians = Math.toRadians(yaw);
      return new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians));
   }

   public static void suppressMovementInput(ClientPlayerEntity player) {
      BounceModule bounce = get();
      if (bounce != null && bounce.isFlying()) {
         PlayerInput real = player.input.playerInput;
         player.input.playerInput = new PlayerInput(false, false, false, false, real.jump(), real.sneak(), real.sprint());
         ((ClientInputAccessor)player.input).homovore$setMoveVector(Vec2f.ZERO);
      }
   }

   public boolean isFreeLooking() {
      return this.isFlying() && this.freeLook.getValue();
   }

   public void turnCamera(double yRotDelta, double xRotDelta) {
      this.cameraYaw += (float)yRotDelta * 0.15F;
      this.cameraPitch = MathHelper.clamp(this.cameraPitch + (float)xRotDelta * 0.15F, -90.0F, 90.0F);
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
