package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.PlayerInputEvent;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_10185;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_4184;

public final class ScaffoldModule extends Module {
   public final Setting<Double> radius = this.number("Radius", (double)1.0, (double)1.0, (double)5.0);
   public final Setting<Integer> depth = this.integer("Depth", 1, 1, 5);
   public final Setting<Double> lookahead = this.number("Lookahead", (double)1.0, (double)0.0, (double)5.0);
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.5, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> sideColor = this.color("SideColor", 11, 0, 255, 44).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 0).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)1.5, (double)0.5, (double)5.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", false).visibleWhen(() -> this.render.getValue()).page("Render");
   private final Set<class_2338> ownedQueue = ConcurrentHashMap.newKeySet();
   private final Map<class_2338, Long> renderMap = new ConcurrentHashMap<>();
   private final AtomicReference<Set<class_2338>> desired = new AtomicReference<>(Set.of());
   private final AtomicLong generation = new AtomicLong();

   public ScaffoldModule() {
      super("Scaffold", Category.EXTRA, "Places blocks beneath you as you walk through the shared placement manager.");
   }

   @Subscribe
   public void onPlayerInput(PlayerInputEvent event) {
      if (mc.field_1724 != null
         && !mc.field_1724.method_7325()
         && !mc.field_1724.method_5765()
         && NetworkThreadExecutor.isConnected()
         && findHotbarBlock(InventoryUtil.snapshot()) != null) {
         class_10185 input = mc.field_1724.field_3913.field_54155;
         if (input.comp_3164()) {
            mc.field_1724.field_3913.field_54155 = new class_10185(
               input.comp_3159(), input.comp_3160(), input.comp_3161(), input.comp_3162(), input.comp_3163(), false, input.comp_3165()
            );
         }
      }
   }

   @Override
   public void onEnable() {
      this.generation.incrementAndGet();
      this.desired.set(Set.of());
      this.ownedQueue.clear();
      this.renderMap.clear();
   }

   @Override
   public void onDisable() {
      this.generation.incrementAndGet();
      this.desired.set(Set.of());
      Set<class_2338> queued = Set.copyOf(this.ownedQueue);
      this.ownedQueue.clear();
      this.renderMap.clear();
      dequeue(queued);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && !mc.field_1724.method_7325() && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         class_1792 block = findHotbarBlock(inventory);
         PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
         if (block != null && world.available()) {
            class_243 velocity = mc.field_1724.method_18798();
            double centerX = inventory.x() + velocity.field_1352 * this.lookahead.getValue();
            double centerZ = inventory.z() + velocity.field_1350 * this.lookahead.getValue();
            int baseX = class_3532.method_15357(centerX);
            int baseZ = class_3532.method_15357(centerZ);
            int feetY = class_3532.method_15357(inventory.y());
            int drop = mc.field_1690.field_1832.method_1434() ? 1 : 0;
            double effectiveRadius = this.radius.getValue() - 1.0;
            double radiusSquared = effectiveRadius * effectiveRadius;
            int scanRadius = class_3532.method_15384(effectiveRadius);
            int maxDepth = this.depth.getValue();
            List<class_2338> candidates = new ArrayList<>();

            for (int x = -scanRadius; x <= scanRadius; x++) {
               for (int z = -scanRadius; z <= scanRadius; z++) {
                  if (!(x * x + z * z > radiusSquared)) {
                     for (int dy = 1; dy <= maxDepth; dy++) {
                        class_2338 pos = new class_2338(baseX + x, feetY - dy - drop, baseZ + z);
                        if (world.known(pos)
                           && world.replaceable(pos)
                           && !world.selfCollision(pos)
                           && !world.entityCollision(pos)
                           && world.withinPlanReach(pos)) {
                           candidates.add(pos.method_10062());
                        }
                     }
                  }
               }
            }

            candidates.sort(
               Comparator.comparingDouble(posx -> world.distanceToSqr(posx.method_10263() + 0.5, posx.method_10264() + 0.5, posx.method_10260() + 0.5))
            );
            Set<class_2338> wanted = Set.copyOf(candidates);
            this.desired.set(wanted);
            this.releaseStale(wanted);
            this.pruneRenderMap(System.currentTimeMillis());
            if (!candidates.isEmpty()) {
               long activeGeneration = this.generation.get();
               NetworkThreadExecutor.<List<class_2338>>submit(context -> {
                  List<class_2338> accepted = new ArrayList<>();

                  for (int index = candidates.size() - 1; index >= 0; index--) {
                     class_2338 posx = candidates.get(index);
                     if (Homovore2Client.placements().enqueueNow(context, posx, null, block, PlacementManager.Options.DEFAULT)) {
                        accepted.add(posx);
                     }
                  }

                  return List.copyOf(accepted);
               }).whenComplete((accepted, failure) -> {
                  if (failure == null && accepted != null && !accepted.isEmpty()) {
                     Set<class_2338> current = this.desired.get();
                     if (this.generation.get() != activeGeneration) {
                        dequeue(Set.copyOf((Collection<? extends class_2338>)accepted));
                     } else {
                        long now = System.currentTimeMillis();
                        Set<class_2338> stale = new HashSet<>();

                        for (class_2338 posx : accepted) {
                           if (!current.contains(posx)) {
                              stale.add(posx);
                           } else {
                              this.ownedQueue.add(posx);
                              this.renderMap.put(posx, now);
                           }
                        }

                        dequeue(stale);
                     }
                  }
               });
            }
         } else {
            this.clearDesiredPlacements();
         }
      } else {
         this.clearDesiredPlacements();
      }
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (this.render.getValue() && !this.renderMap.isEmpty()) {
         long now = System.currentTimeMillis();

         for (Entry<class_2338, Long> entry : this.renderMap.entrySet()) {
            if (!BlockRender.expired(entry.getValue(), now, this.fadeTime.getValue())) {
               BlockRender.cuboid(
                  entry.getKey(),
                  this.lineColor.getValue(),
                  this.sideColor.getValue(),
                  this.lineWidth.getValue().floatValue(),
                  BlockRender.alpha(entry.getValue(), now, this.fadeTime.getValue()),
                  this.throughWalls.getValue()
               );
            }
         }

         this.pruneRenderMap(now);
      }
   }

   private void clearDesiredPlacements() {
      this.desired.set(Set.of());
      this.releaseStale(Set.of());
   }

   private void releaseStale(Set<class_2338> wanted) {
      Set<class_2338> stale = new HashSet<>(this.ownedQueue);
      stale.removeAll(wanted);
      if (!stale.isEmpty()) {
         this.ownedQueue.removeAll(stale);
         dequeue(stale);
      }
   }

   private static void dequeue(Set<class_2338> positions) {
      if (!positions.isEmpty() && NetworkThreadExecutor.isConnected()) {
         Homovore2Client.placements().dequeueIf(positions::contains);
      }
   }

   private void pruneRenderMap(long now) {
      long fadeMillis = (long)(this.fadeTime.getValue() * 1000.0);
      this.renderMap.entrySet().removeIf(entry -> now - entry.getValue() > fadeMillis);
   }

   private static class_1792 findHotbarBlock(InventorySnapshot inventory) {
      if (!inventory.available()) {
         return null;
      }

      int selected = inventory.selectedSlot();
      class_1799 held = inventory.item(selected);
      if (held.method_7909() instanceof class_1747) {
         return held.method_7909();
      }

      for (int slot = 0; slot < 9; slot++) {
         class_1799 stack = inventory.item(slot);
         if (stack.method_7909() instanceof class_1747) {
            return stack.method_7909();
         }
      }

      return null;
   }
}
