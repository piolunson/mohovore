package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.PlacementManager;
import dev.leonetic.v2.render.BlockRender;
import dev.leonetic.v2.util.inventory.InventorySnapshot;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import dev.leonetic.v2.util.world.PlacementWorldSnapshot;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class FallTrapModule extends Module {
   private static final double TARGET_RANGE = 8.0;
   private static final double REACH = 5.0;
   private static final double MAX_SAMPLE_SPEED = 8.0;
   private static final double MAX_HORIZONTAL_SPEED = 0.1;
   private static final double MAX_FALL_SPEED = 0.35;
   private static final double MAX_RISE_SPEED = 0.05;
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.5, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> sideColor = this.color("SideColor", 255, 0, 0, 44).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 0).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)1.5, (double)0.1, (double)8.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(() -> this.render.getValue()).page("Render");
   private final Set<BlockPos> wanted = new LinkedHashSet<>(16);
   private final Set<BlockPos> owned = new HashSet<>(16);
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private final Map<UUID, Vec3d> velocities = new HashMap<>();
   private final Map<UUID, Vec3d> previousPositions = new HashMap<>();

   public FallTrapModule() {
      super("FallTrap", Category.COMBAT, "Air-places obsidian under players digging down so they cannot get below ground.");
   }

   @Override
   public void onEnable() {
      this.resetTracking();
   }

   @Override
   public void onDisable() {
      this.releaseAll();
      this.resetTracking();
      this.renderMap.clear();
   }

   @Override
   public void onTick() {
      if (mc.player != null && mc.world != null && mc.currentScreen == null && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available() && !inventory.deadOrDying() && InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.HOTBAR_SCOPE).found()) {
            List<PlayerEntity> nearby = this.playersInRange();
            this.trackVelocities(nearby);
            this.plan(nearby, inventory.eyePosition());
            this.releaseUnwanted();
            if (!this.wanted.isEmpty()) {
               this.submit();
            }
         } else {
            this.releaseAll();
         }
      } else {
         this.releaseAll();
         this.resetTracking();
      }
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (!this.render.getValue()) {
         this.renderMap.clear();
      } else {
         long now = System.currentTimeMillis();

         for (BlockPos pos : this.wanted) {
            this.renderMap.put(pos, now);
         }

         this.renderMap
            .entrySet()
            .removeIf(
               entry -> {
                  if (BlockRender.expired(entry.getValue(), now, this.fadeTime.getValue())) {
                     return true;
                  }

                  BlockRender.cuboid(
                     entry.getKey(),
                     this.lineColor.getValue(),
                     this.sideColor.getValue(),
                     this.lineWidth.getValue().floatValue(),
                     BlockRender.alpha(entry.getValue(), now, this.fadeTime.getValue()),
                     this.throughWalls.getValue()
                  );
                  return false;
               }
            );
      }
   }

   private void plan(List<PlayerEntity> nearby, Vec3d eye) {
      this.wanted.clear();
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();

      for (PlayerEntity target : nearby) {
         if (this.eligible(target)) {
            Vec3d feet = target.getEntityPos();
            double halfWidth = target.getWidth() * 0.5;
            int minX = MathHelper.floor(feet.x - halfWidth);
            int maxX = MathHelper.floor(feet.x + halfWidth - 1.0E-6);
            int minZ = MathHelper.floor(feet.z - halfWidth);
            int maxZ = MathHelper.floor(feet.z + halfWidth - 1.0E-6);
            int y = MathHelper.floor(feet.y) - 1;

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  BlockPos pos = new BlockPos(x, y, z);
                  BlockState state = mc.world.getBlockState(pos);
                  if (state.isReplaceable() && this.withinReach(eye, pos) && this.placeable(world, pos)) {
                     this.wanted.add(pos);
                  }
               }
            }
         }
      }
   }

   private boolean eligible(PlayerEntity target) {
      if (!target.isOnGround() && !target.isGliding() && !target.hasVehicle()) {
         Vec3d velocity = this.velocities.get(target.getUuid());
         if (velocity == null) {
            return false;
         } else {
            return !(Math.abs(velocity.x) > 0.1) && !(Math.abs(velocity.z) > 0.1) ? velocity.y >= -0.35 && velocity.y <= 0.05 : false;
         }
      } else {
         return false;
      }
   }

   private void trackVelocities(List<PlayerEntity> nearby) {
      Set<UUID> live = new HashSet<>(nearby.size());

      for (PlayerEntity player : nearby) {
         UUID id = player.getUuid();
         live.add(id);
         Vec3d position = player.getEntityPos();
         Vec3d previous = this.previousPositions.put(id, position);
         if (previous != null) {
            Vec3d raw = position.subtract(previous);
            if (raw.length() > 8.0) {
               this.velocities.remove(id);
            } else {
               Vec3d filtered = this.velocities.get(id);
               this.velocities.put(id, filtered == null ? raw : filtered.multiply(0.55).add(raw.multiply(0.45)));
            }
         }
      }

      this.previousPositions.keySet().retainAll(live);
      this.velocities.keySet().retainAll(live);
   }

   private void resetTracking() {
      this.previousPositions.clear();
      this.velocities.clear();
   }

   private void submit() {
      PlacementManager.Options options = new PlacementManager.Options(true, false, true, false);

      for (BlockPos pos : this.wanted) {
         this.owned.add(pos);
         Homovore2Client.placements().enqueue(pos, null, Items.OBSIDIAN, options);
      }

      Homovore2Client.placements().flush();
   }

   private void releaseUnwanted() {
      Set<BlockPos> stale = new HashSet<>(this.owned);
      stale.removeAll(this.wanted);
      if (!stale.isEmpty()) {
         Homovore2Client.placements().dequeueIf(stale::contains);
         this.owned.removeAll(stale);
      }
   }

   private void releaseAll() {
      if (!this.owned.isEmpty()) {
         Set<BlockPos> released = Set.copyOf(this.owned);
         Homovore2Client.placements().dequeueIf(released::contains);
         this.owned.clear();
      }

      this.wanted.clear();
   }

   private boolean withinReach(Vec3d eye, BlockPos pos) {
      Box box = new Box(pos);
      double dx = Math.max(Math.max(box.minX - eye.x, eye.x - box.maxX), 0.0);
      double dy = Math.max(Math.max(box.minY - eye.y, eye.y - box.maxY), 0.0);
      double dz = Math.max(Math.max(box.minZ - eye.z, eye.z - box.maxZ), 0.0);
      return dx * dx + dy * dy + dz * dz <= 25.0;
   }

   private boolean placeable(PlacementWorldSnapshot.Snapshot world, BlockPos pos) {
      if (mc.world.isOutOfHeightLimit(pos)) {
         return false;
      } else {
         return mc.player.getBoundingBox().intersects(new Box(pos)) ? false : !world.available() || !world.known(pos) || !world.entityCollision(pos);
      }
   }

   private List<PlayerEntity> playersInRange() {
      double maxSq = 64.0;
      List<PlayerEntity> players = new ArrayList<>();

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player
            && player.isAlive()
            && !player.isSpectator()
            && !Homovore2Client.friends().isFriend(player)
            && !(mc.player.squaredDistanceTo(player) > maxSq)) {
            players.add(player);
         }
      }

      players.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
      return players;
   }
}
