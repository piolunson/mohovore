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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

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
   private final Set<BlockPos> wanted = new HashSet<>(8);
   private final List<BlockPos> order = new ArrayList<>(8);
   private final Set<BlockPos> owned = new HashSet<>(8);
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private final Set<BlockPos> ownShulkers = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, Long> pendingOwnClaims = new ConcurrentHashMap<>();
   private final List<BlockPos> containers = new ArrayList<>(8);
   private Set<BlockPos> shulkersLastTick = Set.of();

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
      if (mc.player != null && mc.world != null && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (!inventory.available()) {
            this.releaseAll();
         } else {
            Set<BlockPos> shulkersNow = this.scan(inventory.eyePosition());
            this.resolveOwnClaims(shulkersNow);
            this.shulkersLastTick = shulkersNow;
            if (!inventory.deadOrDying() && InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.HOTBAR_SCOPE).found()) {
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

   private Set<BlockPos> scan(Vec3d eye) {
      this.containers.clear();
      Set<BlockPos> shulkers = new HashSet<>(4);
      double rangeSq = 49.0;
      int centerX = ChunkSectionPos.getSectionCoordFloored(mc.player.getX());
      int centerZ = ChunkSectionPos.getSectionCoordFloored(mc.player.getZ());

      for (int x = centerX - 1; x <= centerX + 1; x++) {
         for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z, false);
            if (chunk != null) {
               for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                  BlockEntityType<?> type = blockEntity.getType();
                  boolean shulker = type == BlockEntityType.SHULKER_BOX;
                  if (shulker || type == BlockEntityType.ENDER_CHEST) {
                     BlockPos pos = blockEntity.getPos();
                     if (!(eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > rangeSq)) {
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

      for (BlockPos pos : this.containers) {
         BlockPos cap = this.capCell(mc.world.getBlockState(pos), pos);
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

   private BlockPos capCell(BlockState state, BlockPos pos) {
      if (state.isOf(Blocks.ENDER_CHEST)) {
         return this.enderChests.getValue() ? pos.up() : null;
      } else if (!state.isIn(BlockTags.SHULKER_BOXES)) {
         return null;
      } else {
         return this.shulkers.getValue() && !this.ownShulkers.contains(pos) ? pos.offset((Direction)state.get(ShulkerBoxBlock.FACING)) : null;
      }
   }

   private double nearestEnemySqr(BlockPos pos) {
      Vec3d center = Vec3d.ofCenter(pos);
      double best = Double.MAX_VALUE;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player && player.isAlive() && !player.isSpectator() && !Homovore2Client.friends().isFriend(player)) {
            best = Math.min(best, player.getBoundingBox().squaredMagnitude(center));
         }
      }

      return best;
   }

   private boolean fillable(PlacementWorldSnapshot.Snapshot world, BlockPos pos) {
      return world.known(pos) && world.replaceable(pos) && !world.selfCollision(pos) && !world.entityCollision(pos) ? world.withinPlanReach(pos) : false;
   }

   private void submit() {
      PlacementManager.Options options = new PlacementManager.Options(true, false, false, false);

      for (BlockPos pos : this.order) {
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
      this.order.clear();
   }

   private void resolveOwnClaims(Set<BlockPos> shulkersNow) {
      long now = System.currentTimeMillis();
      this.pendingOwnClaims.entrySet().removeIf(entry -> {
         BlockPos pos = entry.getKey();
         if (!mc.world.isPosLoaded(pos)) {
            return false;
         }

         if (mc.world.getBlockState(pos).isIn(BlockTags.SHULKER_BOXES)) {
            if (!this.shulkersLastTick.contains(pos)) {
               this.ownShulkers.add(pos);
            }

            return true;
         } else {
            return now - entry.getValue() > 2000L;
         }
      });
      this.ownShulkers.removeIf(pos -> mc.world.isPosLoaded(pos) && !mc.world.getBlockState(pos).isIn(BlockTags.SHULKER_BOXES));
   }

   @Subscribe
   public void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof PlayerInteractBlockC2SPacket useOn) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available()) {
            ItemStack used = useOn.getHand() == Hand.OFF_HAND ? inventory.offhand() : inventory.item(inventory.selectedSlot());
            if (used.isIn(ItemTags.SHULKER_BOXES)) {
               BlockHitResult hit = useOn.getBlockHitResult();
               BlockPos clicked = hit.getBlockPos().toImmutable();
               long now = System.currentTimeMillis();
               this.pendingOwnClaims.put(clicked, now);
               this.pendingOwnClaims.put(clicked.offset(hit.getSide()), now);
            }
         }
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

   private record Candidate(BlockPos pos, double enemyDistanceSqr) {
   }
}
