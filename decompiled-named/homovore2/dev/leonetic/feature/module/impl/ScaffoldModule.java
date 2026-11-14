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
import net.minecraft.client.render.Camera;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

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
   private final Set<BlockPos> ownedQueue = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, Long> renderMap = new ConcurrentHashMap<>();
   private final AtomicReference<Set<BlockPos>> desired = new AtomicReference<>(Set.of());
   private final AtomicLong generation = new AtomicLong();

   public ScaffoldModule() {
      super("Scaffold", Category.EXTRA, "Places blocks beneath you as you walk through the shared placement manager.");
   }

   @Subscribe
   public void onPlayerInput(PlayerInputEvent event) {
      if (mc.player != null
         && !mc.player.isSpectator()
         && !mc.player.hasVehicle()
         && NetworkThreadExecutor.isConnected()
         && findHotbarBlock(InventoryUtil.snapshot()) != null) {
         PlayerInput input = mc.player.input.playerInput;
         if (input.sneak()) {
            mc.player.input.playerInput = new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), input.jump(), false, input.sprint());
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
      Set<BlockPos> queued = Set.copyOf(this.ownedQueue);
      this.ownedQueue.clear();
      this.renderMap.clear();
      dequeue(queued);
   }

   @Override
   public void onTick() {
      if (mc.player != null && mc.world != null && !mc.player.isSpectator() && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         Item block = findHotbarBlock(inventory);
         PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
         if (block != null && world.available()) {
            Vec3d velocity = mc.player.getVelocity();
            double centerX = inventory.x() + velocity.x * this.lookahead.getValue();
            double centerZ = inventory.z() + velocity.z * this.lookahead.getValue();
            int baseX = MathHelper.floor(centerX);
            int baseZ = MathHelper.floor(centerZ);
            int feetY = MathHelper.floor(inventory.y());
            int drop = mc.options.sneakKey.isPressed() ? 1 : 0;
            double effectiveRadius = this.radius.getValue() - 1.0;
            double radiusSquared = effectiveRadius * effectiveRadius;
            int scanRadius = MathHelper.ceil(effectiveRadius);
            int maxDepth = this.depth.getValue();
            List<BlockPos> candidates = new ArrayList<>();

            for (int x = -scanRadius; x <= scanRadius; x++) {
               for (int z = -scanRadius; z <= scanRadius; z++) {
                  if (!(x * x + z * z > radiusSquared)) {
                     for (int dy = 1; dy <= maxDepth; dy++) {
                        BlockPos pos = new BlockPos(baseX + x, feetY - dy - drop, baseZ + z);
                        if (world.known(pos)
                           && world.replaceable(pos)
                           && !world.selfCollision(pos)
                           && !world.entityCollision(pos)
                           && world.withinPlanReach(pos)) {
                           candidates.add(pos.toImmutable());
                        }
                     }
                  }
               }
            }

            candidates.sort(Comparator.comparingDouble(posx -> world.distanceToSqr(posx.getX() + 0.5, posx.getY() + 0.5, posx.getZ() + 0.5)));
            Set<BlockPos> wanted = Set.copyOf(candidates);
            this.desired.set(wanted);
            this.releaseStale(wanted);
            this.pruneRenderMap(System.currentTimeMillis());
            if (!candidates.isEmpty()) {
               long activeGeneration = this.generation.get();
               NetworkThreadExecutor.<List<BlockPos>>submit(context -> {
                  List<BlockPos> accepted = new ArrayList<>();

                  for (int index = candidates.size() - 1; index >= 0; index--) {
                     BlockPos posx = candidates.get(index);
                     if (Homovore2Client.placements().enqueueNow(context, posx, null, block, PlacementManager.Options.DEFAULT)) {
                        accepted.add(posx);
                     }
                  }

                  return List.copyOf(accepted);
               }).whenComplete((accepted, failure) -> {
                  if (failure == null && accepted != null && !accepted.isEmpty()) {
                     Set<BlockPos> current = this.desired.get();
                     if (this.generation.get() != activeGeneration) {
                        dequeue(Set.copyOf((Collection<? extends BlockPos>)accepted));
                     } else {
                        long now = System.currentTimeMillis();
                        Set<BlockPos> stale = new HashSet<>();

                        for (BlockPos posx : accepted) {
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
   public void onRender3D(Camera camera, float partialTick) {
      if (this.render.getValue() && !this.renderMap.isEmpty()) {
         long now = System.currentTimeMillis();

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
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

   private void releaseStale(Set<BlockPos> wanted) {
      Set<BlockPos> stale = new HashSet<>(this.ownedQueue);
      stale.removeAll(wanted);
      if (!stale.isEmpty()) {
         this.ownedQueue.removeAll(stale);
         dequeue(stale);
      }
   }

   private static void dequeue(Set<BlockPos> positions) {
      if (!positions.isEmpty() && NetworkThreadExecutor.isConnected()) {
         Homovore2Client.placements().dequeueIf(positions::contains);
      }
   }

   private void pruneRenderMap(long now) {
      long fadeMillis = (long)(this.fadeTime.getValue() * 1000.0);
      this.renderMap.entrySet().removeIf(entry -> now - entry.getValue() > fadeMillis);
   }

   private static Item findHotbarBlock(InventorySnapshot inventory) {
      if (!inventory.available()) {
         return null;
      }

      int selected = inventory.selectedSlot();
      ItemStack held = inventory.item(selected);
      if (held.getItem() instanceof BlockItem) {
         return held.getItem();
      }

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = inventory.item(slot);
         if (stack.getItem() instanceof BlockItem) {
            return stack.getItem();
         }
      }

      return null;
   }
}
