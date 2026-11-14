package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.render.BlockRender;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.world.PlacementWorldSnapshot;
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
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_4184;

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
   private final Set<class_2338> wanted = new LinkedHashSet<>(16);
   private final Set<class_2338> owned = new HashSet<>(16);
   private final Map<class_2338, Long> renderMap = new HashMap<>();
   private final Map<UUID, class_243> velocities = new HashMap<>();
   private final Map<UUID, class_243> previousPositions = new HashMap<>();

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
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1755 == null && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available() && !inventory.deadOrDying() && InventoryUtil.find(class_1802.field_8281, InventoryUtil.HOTBAR_SCOPE).found()) {
            List<class_1657> nearby = this.playersInRange();
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
   public void onRender3D(class_4184 camera, float partialTick) {
      if (!this.render.getValue()) {
         this.renderMap.clear();
      } else {
         long now = System.currentTimeMillis();

         for (class_2338 pos : this.wanted) {
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

   private void plan(List<class_1657> nearby, class_243 eye) {
      this.wanted.clear();
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();

      for (class_1657 target : nearby) {
         if (this.eligible(target)) {
            class_243 feet = target.method_73189();
            double halfWidth = target.method_17681() * 0.5;
            int minX = class_3532.method_15357(feet.field_1352 - halfWidth);
            int maxX = class_3532.method_15357(feet.field_1352 + halfWidth - 1.0E-6);
            int minZ = class_3532.method_15357(feet.field_1350 - halfWidth);
            int maxZ = class_3532.method_15357(feet.field_1350 + halfWidth - 1.0E-6);
            int y = class_3532.method_15357(feet.field_1351) - 1;

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  class_2338 pos = new class_2338(x, y, z);
                  class_2680 state = mc.field_1687.method_8320(pos);
                  if (state.method_45474() && this.withinReach(eye, pos) && this.placeable(world, pos)) {
                     this.wanted.add(pos);
                  }
               }
            }
         }
      }
   }

   private boolean eligible(class_1657 target) {
      if (!target.method_24828() && !target.method_6128() && !target.method_5765()) {
         class_243 velocity = this.velocities.get(target.method_5667());
         if (velocity == null) {
            return false;
         } else {
            return !(Math.abs(velocity.field_1352) > 0.1) && !(Math.abs(velocity.field_1350) > 0.1)
               ? velocity.field_1351 >= -0.35 && velocity.field_1351 <= 0.05
               : false;
         }
      } else {
         return false;
      }
   }

   private void trackVelocities(List<class_1657> nearby) {
      Set<UUID> live = new HashSet<>(nearby.size());

      for (class_1657 player : nearby) {
         UUID id = player.method_5667();
         live.add(id);
         class_243 position = player.method_73189();
         class_243 previous = this.previousPositions.put(id, position);
         if (previous != null) {
            class_243 raw = position.method_1020(previous);
            if (raw.method_1033() > 8.0) {
               this.velocities.remove(id);
            } else {
               class_243 filtered = this.velocities.get(id);
               this.velocities.put(id, filtered == null ? raw : filtered.method_1021(0.55).method_1019(raw.method_1021(0.45)));
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

      for (class_2338 pos : this.wanted) {
         this.owned.add(pos);
         Homovore2Client.placements().enqueue(pos, null, class_1802.field_8281, options);
      }

      Homovore2Client.placements().flush();
   }

   private void releaseUnwanted() {
      Set<class_2338> stale = new HashSet<>(this.owned);
      stale.removeAll(this.wanted);
      if (!stale.isEmpty()) {
         Homovore2Client.placements().dequeueIf(stale::contains);
         this.owned.removeAll(stale);
      }
   }

   private void releaseAll() {
      if (!this.owned.isEmpty()) {
         Set<class_2338> released = Set.copyOf(this.owned);
         Homovore2Client.placements().dequeueIf(released::contains);
         this.owned.clear();
      }

      this.wanted.clear();
   }

   private boolean withinReach(class_243 eye, class_2338 pos) {
      class_238 box = new class_238(pos);
      double dx = Math.max(Math.max(box.field_1323 - eye.field_1352, eye.field_1352 - box.field_1320), 0.0);
      double dy = Math.max(Math.max(box.field_1322 - eye.field_1351, eye.field_1351 - box.field_1325), 0.0);
      double dz = Math.max(Math.max(box.field_1321 - eye.field_1350, eye.field_1350 - box.field_1324), 0.0);
      return dx * dx + dy * dy + dz * dz <= 25.0;
   }

   private boolean placeable(PlacementWorldSnapshot.Snapshot world, class_2338 pos) {
      if (mc.field_1687.method_31606(pos)) {
         return false;
      } else {
         return mc.field_1724.method_5829().method_994(new class_238(pos)) ? false : !world.available() || !world.known(pos) || !world.entityCollision(pos);
      }
   }

   private List<class_1657> playersInRange() {
      double maxSq = 64.0;
      List<class_1657> players = new ArrayList<>();

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player != mc.field_1724
            && player.method_5805()
            && !player.method_7325()
            && !Homovore2Client.friends().isFriend(player)
            && !(mc.field_1724.method_5858(player) > maxSq)) {
            players.add(player);
         }
      }

      players.sort(Comparator.comparingDouble(mc.field_1724::method_5858));
      return players;
   }
}
