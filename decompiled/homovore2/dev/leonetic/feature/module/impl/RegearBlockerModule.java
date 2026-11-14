package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2480;
import net.minecraft.class_2586;
import net.minecraft.class_2591;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2885;
import net.minecraft.class_3481;
import net.minecraft.class_3489;
import net.minecraft.class_3965;
import net.minecraft.class_4076;
import net.minecraft.class_4184;

public final class RegearBlockerModule extends Module {
   private static final long OWN_CLAIM_MS = 2000L;
   private static final double SCAN_RANGE = 7.0;
   private static final int CHUNK_RADIUS = 1;
   private static final double ENEMY_RANGE = 10.0;
   private static final boolean SWING = false;
   public final Setting<Boolean> enderChests = this.bool("EnderChests", true);
   public final Setting<Boolean> shulkers = this.bool("Shulkers", true);
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.5, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> sideColor = this.color("SideColor", 255, 0, 0, 44).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 0).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)1.0, (double)0.1, (double)8.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(() -> this.render.getValue()).page("Render");
   private final Set<class_2338> wanted = new HashSet<>(8);
   private final List<class_2338> order = new ArrayList<>(8);
   private final Set<class_2338> owned = new HashSet<>(8);
   private final Map<class_2338, Long> renderMap = new HashMap<>();
   private final Set<class_2338> ownShulkers = ConcurrentHashMap.newKeySet();
   private final Map<class_2338, Long> pendingOwnClaims = new ConcurrentHashMap<>();
   private final List<class_2338> containers = new ArrayList<>(8);
   private Set<class_2338> shulkersLastTick = Set.of();

   public RegearBlockerModule() {
      super("RegearBlocker", Category.COMBAT, "Caps the ender chests and shulkers an enemy is standing next to with obsidian.");
   }

   @Override
   public void onEnable() {
      this.clearOwnership();
      this.renderMap.clear();
   }

   @Override
   public void onDisable() {
      this.releaseAll();
      this.clearOwnership();
      this.renderMap.clear();
   }

   private void clearOwnership() {
      this.ownShulkers.clear();
      this.pendingOwnClaims.clear();
      this.containers.clear();
      this.shulkersLastTick = Set.of();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (!inventory.available()) {
            this.releaseAll();
         } else {
            Set<class_2338> shulkersNow = this.scan(inventory.eyePosition());
            this.resolveOwnClaims(shulkersNow);
            this.shulkersLastTick = shulkersNow;
            if (!inventory.deadOrDying() && InventoryUtil.find(class_1802.field_8281, InventoryUtil.HOTBAR_SCOPE).found()) {
               PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
               if (!world.available()) {
                  this.releaseAll();
               } else {
                  this.plan(world);
                  this.releaseUnwanted();
                  if (!this.order.isEmpty()) {
                     this.submit();
                  }
               }
            } else {
               this.releaseAll();
            }
         }
      } else {
         this.releaseAll();
         this.clearOwnership();
      }
   }

   private Set<class_2338> scan(class_243 eye) {
      this.containers.clear();
      Set<class_2338> shulkers = new HashSet<>(4);
      double rangeSq = 49.0;
      int centerX = class_4076.method_42615(mc.field_1724.method_23317());
      int centerZ = class_4076.method_42615(mc.field_1724.method_23321());

      for (int x = centerX - 1; x <= centerX + 1; x++) {
         for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            class_2818 chunk = mc.field_1687.method_2935().method_12126(x, z, false);
            if (chunk != null) {
               for (class_2586 blockEntity : chunk.method_12214().values()) {
                  class_2591<?> type = blockEntity.method_11017();
                  boolean shulker = type == class_2591.field_11896;
                  if (shulker || type == class_2591.field_11901) {
                     class_2338 pos = blockEntity.method_11016();
                     if (!(eye.method_1025(class_243.method_24953(pos)) > rangeSq)) {
                        this.containers.add(pos);
                        if (shulker) {
                           shulkers.add(pos);
                        }
                     }
                  }
               }
            }
         }
      }

      return shulkers;
   }

   private void plan(PlacementWorldSnapshot.Snapshot world) {
      this.wanted.clear();
      this.order.clear();
      List<RegearBlockerModule.Candidate> candidates = new ArrayList<>();

      for (class_2338 pos : this.containers) {
         class_2338 cap = this.capCell(mc.field_1687.method_8320(pos), pos);
         if (cap != null && !this.wanted.contains(cap) && this.fillable(world, cap)) {
            double enemySq = this.nearestEnemySqr(pos);
            if (!(enemySq > 100.0)) {
               this.wanted.add(cap);
               candidates.add(new RegearBlockerModule.Candidate(cap, enemySq));
            }
         }
      }

      candidates.sort(Comparator.comparingDouble(RegearBlockerModule.Candidate::enemyDistanceSqr));

      for (RegearBlockerModule.Candidate candidate : candidates) {
         this.order.add(candidate.pos());
      }
   }

   private class_2338 capCell(class_2680 state, class_2338 pos) {
      if (state.method_27852(class_2246.field_10443)) {
         return this.enderChests.getValue() ? pos.method_10084() : null;
      } else if (!state.method_26164(class_3481.field_21490)) {
         return null;
      } else {
         return this.shulkers.getValue() && !this.ownShulkers.contains(pos) ? pos.method_10093((class_2350)state.method_11654(class_2480.field_11496)) : null;
      }
   }

   private double nearestEnemySqr(class_2338 pos) {
      class_243 center = class_243.method_24953(pos);
      double best = Double.MAX_VALUE;

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player != mc.field_1724 && player.method_5805() && !player.method_7325() && !Homovore2Client.friends().isFriend(player)) {
            best = Math.min(best, player.method_5829().method_49271(center));
         }
      }

      return best;
   }

   private boolean fillable(PlacementWorldSnapshot.Snapshot world, class_2338 pos) {
      return world.known(pos) && world.replaceable(pos) && !world.selfCollision(pos) && !world.entityCollision(pos) ? world.withinPlanReach(pos) : false;
   }

   private void submit() {
      PlacementManager.Options options = new PlacementManager.Options(true, false, false, false);

      for (class_2338 pos : this.order) {
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
      this.order.clear();
   }

   private void resolveOwnClaims(Set<class_2338> shulkersNow) {
      long now = System.currentTimeMillis();
      this.pendingOwnClaims.entrySet().removeIf(entry -> {
         class_2338 pos = entry.getKey();
         if (!mc.field_1687.method_8477(pos)) {
            return false;
         }

         if (mc.field_1687.method_8320(pos).method_26164(class_3481.field_21490)) {
            if (!this.shulkersLastTick.contains(pos)) {
               this.ownShulkers.add(pos);
            }

            return true;
         } else {
            return now - entry.getValue() > 2000L;
         }
      });
      this.ownShulkers.removeIf(pos -> mc.field_1687.method_8477(pos) && !mc.field_1687.method_8320(pos).method_26164(class_3481.field_21490));
   }

   @Subscribe
   public void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof class_2885 useOn) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available()) {
            class_1799 used = useOn.method_12546() == class_1268.field_5810 ? inventory.offhand() : inventory.item(inventory.selectedSlot());
            if (used.method_31573(class_3489.field_54293)) {
               class_3965 hit = useOn.method_12543();
               class_2338 clicked = hit.method_17777().method_10062();
               long now = System.currentTimeMillis();
               this.pendingOwnClaims.put(clicked, now);
               this.pendingOwnClaims.put(clicked.method_10093(hit.method_17780()), now);
            }
         }
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

   private record Candidate(class_2338 pos, double enemyDistanceSqr) {
   }
}
