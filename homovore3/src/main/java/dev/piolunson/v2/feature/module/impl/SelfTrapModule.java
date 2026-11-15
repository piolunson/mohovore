package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.network.PacketEvent;
import dev.piolunson.v2.event.system.Subscribe;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.manager.PlacementManager;
import dev.piolunson.v2.manager.RotationManager;
import dev.piolunson.v2.render.BlockRender;
import dev.piolunson.v2.util.RotationUtil;
import dev.piolunson.v2.util.inventory.InventorySnapshot;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.inventory.Result;
import dev.piolunson.v2.util.network.NetworkActionContext;
import dev.piolunson.v2.util.network.NetworkThreadExecutor;
import dev.piolunson.v2.util.network.PacketLimiter;
import dev.piolunson.v2.util.world.PlacementWorldSnapshot;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SelfTrapModule extends Module {
   private static final double ANTI_PISTON_TRIGGER_RANGE_SQ = 25.0;
   private static final long FIREWORK_APPEAR_TIMEOUT_MS = 1500L;
   private static final long REBREAK_WINDOW_MS = 1000L;
   private static final int REBREAK_THRESHOLD = 3;
   private static final long BREAK_THREAT_MS = 1000L;
   private static final long EXTEND_HOLD_MS = 1000L;
   private static final long ATTACK_RETRY_MS = 250L;
   private static final long EXPLOSION_REPAIR_MS = 100L;
   private static final Direction[] HORIZONTALS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   public final Setting<Boolean> attack = this.bool("Attack", true);
   public final Setting<Boolean> reactive = this.bool("Reactive", true)
      .describe("Maintains each defensive cell and repairs it directly from its block update.");
   public final Setting<Boolean> mineProtect = this.bool("MineProtect", true).describe("Builds extra cover around a side another player is mining.");
   public final Setting<Boolean> fireworks = this.bool("Fireworks", true).describe("Uses rockets to occupy repeatedly broken or crystal-threatened cells.");
   public final Setting<Boolean> crystalProtect = this.bool("CrystalProtect", true).visibleWhen(() -> this.fireworks.getValue());
   public final Setting<Boolean> avoidHelping = this.bool("AvoidHelpingOpponents", true);
   public final Setting<Boolean> pauseOnEat = this.bool("PauseOnEat", true).describe("Pauses placements, fireworks and attacks while eating.");
   public final Setting<Boolean> selfTrap = this.bool("SelfTrap", true).page("SelfTrap");
   public final Setting<SelfTrapModule.SelfTrapMode> selfTrapMode = this.mode("SelfTrapMode", SelfTrapModule.SelfTrapMode.SMART)
      .visibleWhen(() -> this.selfTrap.getValue())
      .page("SelfTrap");
   public final Setting<Boolean> selfTrapHead = this.bool("SelfTrapHead", true).visibleWhen(() -> this.selfTrap.getValue()).page("SelfTrap");
   public final Setting<Boolean> antiPiston = this.bool("AntiPiston", false)
      .describe(
         "Arms when an opponent places a piston within 5 blocks, then keeps the face and head rings filled against that opponent for the rest of the fight."
      )
      .visibleWhen(() -> this.selfTrap.getValue())
      .page("SelfTrap");
   public final Setting<Boolean> crawlTrap = this.bool("CrawlTrap", false)
      .visibleWhen(() -> this.selfTrap.getValue() && (this.selfTrapHead.getValue() || this.antiPiston.getValue()))
      .page("SelfTrap");
   public final Setting<Boolean> extend = this.bool("Extend", true).page("Extend");
   public final Setting<SelfTrapModule.ExtendMode> extendMode = this.mode("ExtendMode", SelfTrapModule.ExtendMode.SMART)
      .visibleWhen(() -> this.extend.getValue())
      .page("Extend");
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.2, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> sideColor = this.color("SideColor", 255, 255, 255, 44).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 0).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)1.0, (double)0.1, (double)8.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(() -> this.render.getValue()).page("Render");
   private final Set<BlockPos> managed = new HashSet<>();
   private final Set<BlockPos> queued = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, ArrayDeque<Long>> breakTimes = new HashMap<>();
   private final Map<BlockPos, Long> breakThreats = new ConcurrentHashMap<>();
   private final Map<BlockPos, SelfTrapModule.FireworkDeployment> fireworkDeployments = new ConcurrentHashMap<>();
   private final Map<BlockPos, Long> renderMap = new ConcurrentHashMap<>();
   private final Map<Integer, Long> attackedCrystals = new ConcurrentHashMap<>();
   private final Map<BlockPos, Long> explosionRepairs = new ConcurrentHashMap<>();
   private final ConcurrentLinkedQueue<BlockPos> placedPistons = new ConcurrentLinkedQueue<>();
   private final AtomicReference<SelfTrapModule.ReactiveState> reactiveState = new AtomicReference<>(SelfTrapModule.ReactiveState.DISABLED);
   private final AtomicLong generation = new AtomicLong();
   private Set<BlockPos> wanted = Set.of();
   private BlockPos lastExtendOffset;
   private long lastExtendThreat;
   private long lastCrystalNearHead;
   private long lastAttack;
   private long antiPistonEpoch;
   private final Set<UUID> pistonUsers = new HashSet<>();
   private PlacementManager.Options lastOptions;
   private final PlacementManager.PlacementListener placementListener = new PlacementManager.PlacementListener() {
      @Override
      public void onBlockUpdate(BlockPos pos, BlockState blockState, boolean wasReplaceable) {
         if (wasReplaceable && SelfTrapModule.isPiston(blockState)) {
            SelfTrapModule.this.placedPistons.add(pos);
         }

         SelfTrapModule.this.queued.remove(pos);
         boolean nowAir = blockState.isAir();
         if (nowAir) {
            SelfTrapModule.ReactiveState state = SelfTrapModule.this.reactiveState.get();
            if (state.enabled && state.protectedCells.contains(pos)) {
               long now = System.currentTimeMillis();
               SelfTrapModule.this.renderMap.put(pos, now);
               if (!SelfTrapModule.this.fireworkDeployments.containsKey(pos)) {
                  SelfTrapModule.this.recordBreakNotice(pos, now);
                  BlockHitResult fireworkHit = state.fireworkHits.get(pos);
                  if (state.fireworks && fireworkHit != null) {
                     SelfTrapModule.FireworkDeployment deployment = SelfTrapModule.this.reserveFirework(pos, now);
                     if (deployment != null) {
                        Homovore2Client.placements().unmaintain(pos);
                        SelfTrapModule.this.deployFirework(List.of(fireworkHit), pos, deployment, state.generation);
                     }
                  }
               }
            }
         }
      }

      @Override
      public void onBreakProgress(BlockPos pos, int breakerId, int progress) {
         SelfTrapModule.ReactiveState state = SelfTrapModule.this.reactiveState.get();
         if (state.enabled && breakerId != state.playerId) {
            if (progress >= 0 && progress <= 9) {
               SelfTrapModule.this.breakThreats.put(pos, System.currentTimeMillis());
            } else {
               SelfTrapModule.this.breakThreats.remove(pos);
            }
         }
      }
   };

   public SelfTrapModule() {
      super("SelfTrap", Category.SAFETY, "Surrounds and traps you in obsidian with reactive repairs and defensive fireworks.");
   }

   public boolean isSurroundPos(BlockPos pos) {
      return this.wanted.contains(pos);
   }

   @Override
   public void onEnable() {
      this.resetLocalState();
      this.antiPistonEpoch = PlacementWorldSnapshot.snapshot().epoch();
      this.generation.incrementAndGet();
      Homovore2Client.placements().addListener(this.placementListener);
   }

   @Override
   public void onDisable() {
      Homovore2Client.placements().removeListener(this.placementListener);
      this.reactiveState.set(SelfTrapModule.ReactiveState.DISABLED);
      this.generation.incrementAndGet();
      this.releaseAllPlacements();
      this.resetLocalState();
   }

   @Override
   public void onTick() {
      if (mc.player != null && mc.world != null && mc.currentScreen == null && NetworkThreadExecutor.isConnected()) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (!inventory.available() || inventory.deadOrDying()) {
            this.reactiveState.set(SelfTrapModule.ReactiveState.DISABLED);
            this.releaseAllPlacements();
            this.wanted = Set.of();
         } else if (this.pauseOnEat.getValue() && this.isEatingOrStarting(inventory)) {
            this.reactiveState.set(SelfTrapModule.ReactiveState.DISABLED);
            this.releaseAllPlacements();
            this.wanted = Set.of();
         } else {
            boolean hasObsidian = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.HOTBAR_SCOPE).found();
            long now = System.currentTimeMillis();
            this.collectExpired(now);
            this.refreshFireworkDeployments(now);
            boolean antiPistonActive = this.updateAntiPistonThreats();
            Box bounds = mc.player.getBoundingBox();
            int feetY = mc.player.getBlockPos().getY();
            int minX = minCell(bounds.minX);
            int maxX = maxCell(bounds.maxX);
            int minZ = minCell(bounds.minZ);
            int maxZ = maxCell(bounds.maxZ);
            List<BlockPos> feet = footCells(minX, maxX, minZ, maxZ, feetY);
            LinkedHashSet<BlockPos> desired = new LinkedHashSet<>();
            Set<BlockPos> extensionCells = new HashSet<>();
            this.baseSurround(feet, minX, maxX, minZ, maxZ, desired, now, antiPistonActive);
            this.computeExtend(feet, desired, extensionCells, now);
            this.applyMineProtect(feet, desired, extensionCells, now);
            this.addRebreakCover(feet, desired, now);
            this.addHeadTrap(feet, minX, maxX, minZ, maxZ, desired, antiPistonActive);
            Set<BlockPos> helpBlocked = this.computeHelpBlocked(minX, maxX, minZ, maxZ, feetY, desired);
            desired.removeAll(helpBlocked);
            DoubleMineModule mine = Homovore2Client.modules().get(DoubleMineModule.class);
            if (mine != null && mine.isEnabled()) {
               desired.removeIf(mine::isMining);
            }

            Set<BlockPos> protectedCells = new HashSet<>(desired);
            Set<BlockPos> packetReactiveCells = new HashSet<>(protectedCells);
            this.addPacketReactiveSelfTrapCells(feet, minX, maxX, minZ, maxZ, packetReactiveCells);
            if (mine != null && mine.isEnabled()) {
               packetReactiveCells.removeIf(mine::isMining);
            }

            PlacementWorldSnapshot.Snapshot placementWorld = PlacementWorldSnapshot.snapshot();
            if (placementWorld.available()) {
               Set<BlockPos> crystalOccupied = new HashSet<>();

               for (BlockPos pos : desired) {
                  if (placementWorld.entityCollision(pos) && this.hasBlockingCrystal(pos)) {
                     crystalOccupied.add(pos);
                  }
               }

               desired.removeIf(pos -> placementWorld.selfCollision(pos) || placementWorld.entityCollision(pos) && !crystalOccupied.contains(pos));
            }

            Vec3d predicted = mc.player.getEntityPos().add(mc.player.getVelocity().multiply(0.5));
            List<BlockPos> ordered = desired.stream().sorted(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(predicted))).toList();
            Result firework = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
            boolean canLaunchFireworks = this.fireworks.getValue() && firework.found();
            List<BlockPos> fireworkCandidates = new ArrayList<>(ordered);
            if (this.fireworks.getValue()) {
               for (BlockPos foot : feet) {
                  for (Direction direction : HORIZONTALS) {
                     fireworkCandidates.add(foot.offset(direction, 2).toImmutable());
                  }
               }
            }

            Map<BlockPos, BlockHitResult> fireworkHits = this.fireworks.getValue() ? this.fireworkTargets(fireworkCandidates, bounds, now) : Map.of();
            protectedCells.addAll(fireworkHits.keySet());
            protectedCells = Set.copyOf(protectedCells);
            packetReactiveCells.addAll(protectedCells);
            long activeGeneration = this.generation.get();
            this.reactiveState
               .set(
                  new SelfTrapModule.ReactiveState(
                     true,
                     this.attack.getValue(),
                     canLaunchFireworks,
                     hasObsidian,
                     inventory.playerId(),
                     activeGeneration,
                     Set.copyOf(packetReactiveCells),
                     Map.copyOf(fireworkHits)
                  )
               );
            this.wanted = protectedCells;
            PlacementManager.Options options = new PlacementManager.Options(true, false, false, false);
            if (hasObsidian) {
               this.reconcilePlacements(ordered, fireworkHits.keySet(), options, now);
            } else {
               this.releaseAllPlacements();
               this.lastOptions = null;
            }

            if (canLaunchFireworks) {
               this.deployReadyFireworks(fireworkHits, firework, now, activeGeneration);
            }

            this.attackThreateningCrystal(inventory, bounds, now);
         }
      } else {
         this.reactiveState.set(SelfTrapModule.ReactiveState.DISABLED);
      }
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof EntitiesDestroyS2CPacket removed) {
         IntListIterator var14 = removed.getEntityIds().iterator();

         while (var14.hasNext()) {
            int id = var14.next();
            this.attackedCrystals.remove(id);
         }
      } else if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
         this.refillVacatedCells(explosion.center());
      } else if (event.getPacket() instanceof EntitySpawnS2CPacket packet && packet.getEntityType() == EntityType.END_CRYSTAL) {
         SelfTrapModule.ReactiveState state = this.reactiveState.get();
         if (state.enabled && state.attack) {
            BlockPos cell = new BlockPos(MathHelper.floor(packet.getX()), MathHelper.floor(packet.getY()), MathHelper.floor(packet.getZ()));
            if (threatens(cell, state.protectedCells)) {
               Box crystalBox = EntityType.END_CRYSTAL.getDimensions().getBoxAt(packet.getX(), packet.getY(), packet.getZ());
               List<BlockPos> repairCells = this.crystalRepairCells(crystalBox, state.protectedCells);
               long now = System.currentTimeMillis();
               Long previous = this.attackedCrystals.putIfAbsent(packet.getEntityId(), now);
               if (previous == null || now - previous >= 250L) {
                  this.attackedCrystals.put(packet.getEntityId(), now);
                  InventorySnapshot inventory = InventoryUtil.snapshot();
                  if (inventory.available() && inventory.playerId() == state.playerId && !inventory.deadOrDying()) {
                     RotationManager.Angles angles = RotationUtil.anglesTo(
                        inventory.eyePosition(), new Vec3d(packet.getX(), packet.getY() + 1.0, packet.getZ())
                     );
                     NetworkThreadExecutor.executeNow(context -> {
                        if (!this.attackAndRepairNow(context, inventory, angles, packet.getEntityId(), repairCells, now)) {
                           this.attackedCrystals.remove(packet.getEntityId(), now);
                        }
                     });
                  } else {
                     this.attackedCrystals.remove(packet.getEntityId(), now);
                  }
               }
            }
         }
      }
   }

   private void refillVacatedCells(Vec3d center) {
      SelfTrapModule.ReactiveState state = this.reactiveState.get();
      if (state.enabled && state.prePlace) {
         Box blast = EntityType.END_CRYSTAL.getDimensions().getBoxAt(center.x, center.y, center.z);
         List<BlockPos> repairCells = this.crystalRepairCells(blast, state.protectedCells);
         if (!repairCells.isEmpty()) {
            InventorySnapshot inventory = InventoryUtil.snapshot();
            if (inventory.available() && inventory.playerId() == state.playerId && !inventory.deadOrDying()) {
               long now = System.currentTimeMillis();
               PlacementManager.Options options = new PlacementManager.Options(true, true, false, false);
               NetworkThreadExecutor.executeNow(context -> {
                  List<BlockPos> due = new ArrayList<>(repairCells.size());

                  for (BlockPos pos : repairCells) {
                     Long previous = this.explosionRepairs.get(pos);
                     if (previous == null || now - previous >= 100L) {
                        due.add(pos);
                     }
                  }

                  if (!due.isEmpty()) {
                     for (BlockPos pos : Homovore2Client.placements().placeAllNow(context, due, null, Items.OBSIDIAN, options)) {
                        this.explosionRepairs.put(pos, now);
                        this.renderMap.put(pos, now);
                     }
                  }
               });
            }
         }
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
      }
   }

   private void baseSurround(List<BlockPos> feet, int minX, int maxX, int minZ, int maxZ, Set<BlockPos> desired, long now, boolean antiPistonActive) {
      for (BlockPos foot : feet) {
         for (Direction direction : HORIZONTALS) {
            BlockPos adjacent = foot.offset(direction);
            if (adjacent.getX() < minX || adjacent.getX() > maxX || adjacent.getZ() < minZ || adjacent.getZ() > maxZ) {
               desired.add(adjacent.toImmutable());
               if (this.selfTrap.getValue()) {
                  if (antiPistonActive) {
                     desired.add(adjacent.up().toImmutable());
                  } else if (this.selfTrapMode.getValue() != SelfTrapModule.SelfTrapMode.NONE) {
                     this.addUpperTrap(adjacent, desired, now);
                  }
               }
            }
         }

         desired.add(foot.down().toImmutable());
      }
   }

   private void addUpperTrap(BlockPos adjacent, Set<BlockPos> desired, long now) {
      boolean should = this.selfTrapMode.getValue() == SelfTrapModule.SelfTrapMode.ALWAYS;
      if (this.selfTrapMode.getValue() == SelfTrapModule.SelfTrapMode.SMART) {
         if (this.hasCrystalAt(adjacent.up())) {
            this.lastCrystalNearHead = now;
         }

         should = now - this.lastCrystalNearHead < 1000L;
      }

      if (should) {
         desired.add(adjacent.up().toImmutable());
      }
   }

   private void addHeadTrap(List<BlockPos> feet, int minX, int maxX, int minZ, int maxZ, Set<BlockPos> desired, boolean antiPistonActive) {
      if (this.selfTrap.getValue() && (this.selfTrapHead.getValue() || antiPistonActive)) {
         boolean prone = this.crawlTrap.getValue() && (mc.player.isCrawling() || mc.player.isGliding());
         int height = prone ? 1 : 2;
         if (this.selfTrapHead.getValue()) {
            desired.add(mc.player.getBlockPos().up(height).toImmutable());
         }

         if (antiPistonActive) {
            for (BlockPos foot : feet) {
               BlockPos head = foot.up(height);

               for (Direction direction : HORIZONTALS) {
                  BlockPos cardinal = head.offset(direction);
                  if (cardinal.getX() < minX || cardinal.getX() > maxX || cardinal.getZ() < minZ || cardinal.getZ() > maxZ) {
                     desired.add(cardinal.toImmutable());
                  }
               }
            }
         }
      }
   }

   private boolean updateAntiPistonThreats() {
      long worldEpoch = PlacementWorldSnapshot.snapshot().epoch();
      if (this.antiPistonEpoch != worldEpoch) {
         this.antiPistonEpoch = worldEpoch;
         this.placedPistons.clear();
         this.pistonUsers.clear();
      }

      if (this.selfTrap.getValue() && this.antiPiston.getValue()) {
         Set<UUID> alive = new HashSet<>();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && player.isAlive()) {
               alive.add(player.getUuid());
            }
         }

         this.pistonUsers.retainAll(alive);

         BlockPos piston;
         while ((piston = this.placedPistons.poll()) != null) {
            Vec3d center = Vec3d.ofCenter(piston);
            if (!(mc.player.getEntityPos().squaredDistanceTo(center) > 25.0)) {
               PlayerEntity user = null;
               double closest = Double.POSITIVE_INFINITY;

               for (PlayerEntity player : mc.world.getPlayers()) {
                  if (player != mc.player && player.isAlive() && !Homovore2Client.friends().isFriend(player)) {
                     double distance = player.getEyePos().squaredDistanceTo(center);
                     if (!(distance > 25.0) && !(distance >= closest)) {
                        closest = distance;
                        user = player;
                     }
                  }
               }

               if (user != null) {
                  this.pistonUsers.add(user.getUuid());
               }
            }
         }

         return !this.pistonUsers.isEmpty();
      } else {
         this.placedPistons.clear();
         this.pistonUsers.clear();
         return false;
      }
   }

   private static boolean isPiston(BlockState state) {
      return state.isOf(Blocks.PISTON) || state.isOf(Blocks.STICKY_PISTON);
   }

   private void addPacketReactiveSelfTrapCells(List<BlockPos> feet, int minX, int maxX, int minZ, int maxZ, Set<BlockPos> reactiveCells) {
      if (this.selfTrap.getValue() && this.selfTrapMode.getValue() != SelfTrapModule.SelfTrapMode.NONE) {
         for (BlockPos foot : feet) {
            for (Direction direction : HORIZONTALS) {
               BlockPos adjacent = foot.offset(direction);
               if (adjacent.getX() < minX || adjacent.getX() > maxX || adjacent.getZ() < minZ || adjacent.getZ() > maxZ) {
                  reactiveCells.add(adjacent.up().toImmutable());
               }
            }
         }
      }
   }

   private void computeExtend(List<BlockPos> feet, Set<BlockPos> desired, Set<BlockPos> extensionCells, long now) {
      if (this.extend.getValue() && this.extendMode.getValue() != SelfTrapModule.ExtendMode.NONE) {
         if (this.extendMode.getValue() == SelfTrapModule.ExtendMode.ALWAYS) {
            for (BlockPos foot : feet) {
               for (Direction direction : HORIZONTALS) {
                  this.addExtendBlocks(foot, direction.getOffsetX(), direction.getOffsetZ(), desired, extensionCells);
               }
            }
         } else {
            EndCrystalEntity crystal = this.findExtendCrystal();
            if (crystal != null) {
               this.lastExtendOffset = crystal.getBlockPos().subtract(mc.player.getBlockPos());
               this.lastExtendThreat = now;
            }

            if (this.lastExtendOffset != null && now - this.lastExtendThreat < 1000L) {
               int dx = Integer.signum(this.lastExtendOffset.getX());
               int dz = Integer.signum(this.lastExtendOffset.getZ());

               for (BlockPos foot : feet) {
                  this.addExtendBlocks(foot, dx, dz, desired, extensionCells);
               }
            }
         }
      }
   }

   private void addExtendBlocks(BlockPos foot, int dx, int dz, Set<BlockPos> desired, Set<BlockPos> extensionCells) {
      if (dx != 0 && dz != 0) {
         this.addSupported(foot.add(dx, 0, dz), desired, extensionCells);
         this.addSupported(foot.add(dx * 2, 0, 0), desired, extensionCells);
         this.addSupported(foot.add(0, 0, dz * 2), desired, extensionCells);
      } else if (dx != 0 ^ dz != 0) {
         if (dx != 0) {
            this.addSupported(foot.add(dx, 0, 1), desired, extensionCells);
            this.addSupported(foot.add(dx, 0, -1), desired, extensionCells);
            this.addSupported(foot.add(dx * 2, 0, 0), desired, extensionCells);
         } else {
            this.addSupported(foot.add(1, 0, dz), desired, extensionCells);
            this.addSupported(foot.add(-1, 0, dz), desired, extensionCells);
            this.addSupported(foot.add(0, 0, dz * 2), desired, extensionCells);
         }
      }
   }

   private void addSupported(BlockPos pos, Set<BlockPos> desired, Set<BlockPos> extensionCells) {
      BlockState below = mc.world.getBlockState(pos.down());
      if (below.isOf(Blocks.OBSIDIAN) || below.isOf(Blocks.BEDROCK)) {
         desired.add(pos.toImmutable());
         extensionCells.add(pos.toImmutable());
      }
   }

   private void applyMineProtect(List<BlockPos> feet, Set<BlockPos> desired, Set<BlockPos> extensionCells, long now) {
      if (this.mineProtect.getValue()) {
         for (Entry<BlockPos, Long> entry : this.breakThreats.entrySet()) {
            if (now - entry.getValue() <= 1000L) {
               BlockPos mined = entry.getKey();

               for (BlockPos foot : feet) {
                  if (mined.getY() == foot.getY()) {
                     int dx = mined.getX() - foot.getX();
                     int dz = mined.getZ() - foot.getZ();
                     if (Math.abs(dx) + Math.abs(dz) == 1) {
                        addDefense(mined.up(), desired, extensionCells);
                        addDefense(mined.down(), desired, extensionCells);
                        if (dx != 0) {
                           addDefense(foot.add(dx, 0, 1), desired, extensionCells);
                           addDefense(foot.add(dx, 0, -1), desired, extensionCells);
                           addDefense(foot.add(dx * 2, 0, 0), desired, extensionCells);
                        } else {
                           addDefense(foot.add(1, 0, dz), desired, extensionCells);
                           addDefense(foot.add(-1, 0, dz), desired, extensionCells);
                           addDefense(foot.add(0, 0, dz * 2), desired, extensionCells);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void addDefense(BlockPos pos, Set<BlockPos> desired, Set<BlockPos> extensionCells) {
      desired.add(pos.toImmutable());
      extensionCells.add(pos.toImmutable());
   }

   private void addRebreakCover(List<BlockPos> feet, Set<BlockPos> desired, long now) {
      if (this.fireworks.getValue()) {
         Set<BlockPos> footSet = Set.copyOf(feet);

         for (BlockPos hot : desired.stream().filter(pos -> isSideOfFootprint(pos, feet)).filter(pos -> this.isHot(pos, now)).toList()) {
            for (Direction direction : HORIZONTALS) {
               BlockPos neighbor = hot.offset(direction).toImmutable();
               if (!footSet.contains(neighbor)) {
                  desired.add(neighbor);
               }
            }
         }
      }
   }

   private Set<BlockPos> computeHelpBlocked(int minX, int maxX, int minZ, int maxZ, int feetY, Set<BlockPos> desired) {
      if (this.avoidHelping.getValue() && (minX != maxX || minZ != maxZ) && !(mc.player.getHealth() < 10.0F)) {
         Set<BlockPos> opponents = new HashSet<>();
         BlockPos self = mc.player.getBlockPos();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player
               && !(player.getBlockPos().getSquaredDistance(self) > 25.0)
               && !this.isPhased(player)
               && !Homovore2Client.friends().isFriend(player)) {
               Box box = player.getBoundingBox();
               int pMinX = minCell(box.minX);
               int pMaxX = maxCell(box.maxX);
               int pMinZ = minCell(box.minZ);
               int pMaxZ = maxCell(box.maxZ);
               int y = player.getBlockPos().getY();

               for (int x = pMinX; x <= pMaxX; x++) {
                  for (int z = pMinZ; z <= pMaxZ; z++) {
                     BlockPos foot = new BlockPos(x, y, z);

                     for (Direction direction : HORIZONTALS) {
                        BlockPos adjacent = foot.offset(direction);
                        if (adjacent.getX() < pMinX || adjacent.getX() > pMaxX || adjacent.getZ() < pMinZ || adjacent.getZ() > pMaxZ) {
                           opponents.add(adjacent.toImmutable());
                        }
                     }

                     opponents.add(foot.down().toImmutable());
                     opponents.add(foot.toImmutable());
                  }
               }
            }
         }

         Set<BlockPos> myFootCells = new HashSet<>(footCells(minX, maxX, minZ, maxZ, feetY));
         Set<BlockPos> blocked = new HashSet<>();

         for (BlockPos position : opponents) {
            if (desired.contains(position) && !nearOwnPerimeter(position, myFootCells)) {
               blocked.add(position);
            }
         }

         return blocked;
      } else {
         return Set.of();
      }
   }

   private Map<BlockPos, BlockHitResult> fireworkTargets(List<BlockPos> positions, Box playerBounds, long now) {
      Map<BlockPos, BlockHitResult> hits = new LinkedHashMap<>();

      for (BlockPos pos : positions) {
         if (this.canRocket(pos, playerBounds, now)) {
            hits.put(pos, this.fireworkHit(pos));
         }
      }

      return hits;
   }

   private void reconcilePlacements(List<BlockPos> ordered, Set<BlockPos> fireworkTargets, PlacementManager.Options options, long now) {
      Set<BlockPos> desiredObsidian = new LinkedHashSet<>(ordered);
      desiredObsidian.removeAll(fireworkTargets);

      for (BlockPos old : Set.copyOf(this.managed)) {
         if (!this.reactive.getValue() || !desiredObsidian.contains(old)) {
            Homovore2Client.placements().unmaintain(old);
            this.managed.remove(old);
         }
      }

      for (BlockPos old : Set.copyOf(this.queued)) {
         if (this.reactive.getValue() || !desiredObsidian.contains(old)) {
            Homovore2Client.placements().unmaintain(old);
            this.queued.remove(old);
         }
      }

      boolean optionsChanged = !options.equals(this.lastOptions);
      Set<BlockPos> inFlight = Homovore2Client.placements().pending();

      for (BlockPos pos : desiredObsidian) {
         BlockState state = mc.world.getBlockState(pos);
         if (this.reactive.getValue()) {
            if (this.managed.add(pos) || optionsChanged) {
               Homovore2Client.placements().maintain(pos, null, Items.OBSIDIAN, options);
            }
         } else if (state.isReplaceable() && !inFlight.contains(pos) && this.queued.add(pos)) {
            Homovore2Client.placements().enqueue(pos, null, Items.OBSIDIAN, options).thenAccept(accepted -> {
               if (!accepted) {
                  this.queued.remove(pos);
               }
            });
         }

         if (state.isReplaceable()) {
            this.renderMap.put(pos, now);
         }
      }

      this.lastOptions = options;
   }

   private void deployReadyFireworks(Map<BlockPos, BlockHitResult> targets, Result firework, long now, long activeGeneration) {
      List<SelfTrapModule.FireworkLaunch> ready = new ArrayList<>();

      for (Entry<BlockPos, BlockHitResult> entry : targets.entrySet()) {
         BlockPos pos = entry.getKey();
         if (!this.fireworkDeployments.containsKey(pos) && !Homovore2Client.placements().isPending(pos) && mc.world.getBlockState(pos).isReplaceable()) {
            if (this.hasLiveFireworkAt(pos)) {
               this.fireworkDeployments.putIfAbsent(pos, new SelfTrapModule.FireworkDeployment(now, true));
               this.clearBreakHistory(pos);
            } else {
               SelfTrapModule.FireworkDeployment deployment = this.reserveFirework(pos, now);
               if (deployment != null) {
                  ready.add(new SelfTrapModule.FireworkLaunch(pos, entry.getValue(), deployment));
               }
            }
         }
      }

      if (!ready.isEmpty()) {
         List<BlockHitResult> hits = ready.stream().map(SelfTrapModule.FireworkLaunch::hit).toList();
         Homovore2Client.placements().useItemOn(hits, firework, false).whenComplete((sent, error) -> {
            if (this.generation.get() == activeGeneration && error == null) {
               long stamp = System.currentTimeMillis();

               for (int i = 0; i < Math.min(sent, ready.size()); i++) {
                  SelfTrapModule.FireworkLaunch launch = ready.get(i);
                  BlockPos posx = launch.pos();
                  this.fireworkDeployments.replace(posx, launch.deployment(), new SelfTrapModule.FireworkDeployment(stamp, false));
                  this.clearBreakHistory(posx);
               }

               for (int i = Math.min(sent, ready.size()); i < ready.size(); i++) {
                  this.releaseFirework(ready.get(i));
               }
            } else {
               for (SelfTrapModule.FireworkLaunch launch : ready) {
                  this.releaseFirework(launch);
               }
            }
         });
      }
   }

   private void deployFirework(List<BlockHitResult> hits, BlockPos pos, SelfTrapModule.FireworkDeployment deployment, long activeGeneration) {
      Result firework = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
      Homovore2Client.placements().useItemOn(hits, firework, false).whenComplete((sent, error) -> {
         if (error == null && sent > 0 && this.generation.get() == activeGeneration) {
            this.fireworkDeployments.replace(pos, deployment, new SelfTrapModule.FireworkDeployment(System.currentTimeMillis(), false));
            this.clearBreakHistory(pos);
         } else {
            this.fireworkDeployments.remove(pos, deployment);
         }
      });
   }

   private SelfTrapModule.FireworkDeployment reserveFirework(BlockPos pos, long now) {
      SelfTrapModule.FireworkDeployment deployment = new SelfTrapModule.FireworkDeployment(now, false);
      return this.fireworkDeployments.putIfAbsent(pos.toImmutable(), deployment) == null ? deployment : null;
   }

   private void releaseFirework(SelfTrapModule.FireworkLaunch launch) {
      this.fireworkDeployments.remove(launch.pos(), launch.deployment());
   }

   private void refreshFireworkDeployments(long now) {
      for (Entry<BlockPos, SelfTrapModule.FireworkDeployment> entry : this.fireworkDeployments.entrySet()) {
         BlockPos pos = entry.getKey();
         SelfTrapModule.FireworkDeployment deployment = entry.getValue();
         if (this.hasLiveFireworkAt(pos)) {
            if (!deployment.observed()) {
               this.fireworkDeployments.replace(pos, deployment, deployment.observe());
            }
         } else if (deployment.observed() || now - deployment.submittedAt() >= 1500L) {
            this.fireworkDeployments.remove(pos, deployment);
         }
      }
   }

   private void attackThreateningCrystal(InventorySnapshot inventory, Box playerBounds, long now) {
      if (this.attack.getValue() && now - this.lastAttack >= 50L) {
         EndCrystalEntity best = null;
         double bestDistance = Double.POSITIVE_INFINITY;
         Vec3d eye = inventory.eyePosition();

         for (EndCrystalEntity crystal : mc.world.getNonSpectatingEntities(EndCrystalEntity.class, playerBounds.expand(6.0))) {
            if (threatens(crystal.getBlockPos(), this.wanted)) {
               double distance = RotationUtil.distanceTo(eye, crystal.getBoundingBox());
               if (!(distance > 6.0) && !(distance >= bestDistance)) {
                  bestDistance = distance;
                  best = crystal;
               }
            }
         }

         if (best != null) {
            int id = best.getId();
            Long attacked = this.attackedCrystals.get(id);
            if (attacked == null || now - attacked >= 250L) {
               List<BlockPos> repairCells = this.crystalRepairCells(best.getBoundingBox(), this.wanted);
               this.attackedCrystals.put(id, now);
               this.lastAttack = now;
               RotationManager.Angles angles = RotationUtil.anglesTo(eye, RotationUtil.closestAimPoint(eye, best.getBoundingBox()));
               NetworkThreadExecutor.<Boolean>submit(context -> this.attackAndRepairNow(context, inventory, angles, id, repairCells, now)).thenAccept(sent -> {
                  if (!sent) {
                     this.attackedCrystals.remove(id);
                  }
               });
            }
         }
      }
   }

   private boolean attackAndRepairNow(
      NetworkActionContext context, InventorySnapshot inventory, RotationManager.Angles angles, int entityId, List<BlockPos> repairCells, long now
   ) {
      try (RotationManager.SilentRotation rotation = Homovore2Client.rotations().beginSilentNow(context, inventory, angles)) {
         if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
            return false;
         }

         if (!context.attack(entityId)) {
            return false;
         }

         context.swing(Hand.MAIN_HAND);
         PlacementManager.Options options = new PlacementManager.Options(true, true, false, false);

         for (BlockPos pos : Homovore2Client.placements().forcePlaceAllNow(context, repairCells, null, Items.OBSIDIAN, options)) {
            this.renderMap.put(pos, now);
         }

         return true;
      }
   }

   private List<BlockPos> crystalRepairCells(Box crystalBox, Set<BlockPos> protectedCells) {
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      return protectedCells.stream()
         .filter(world::replaceable)
         .filter(pos -> crystalBox.intersects(new Box(pos)))
         .sorted(Comparator.comparingDouble(pos -> world.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
         .toList();
   }

   private boolean canRocket(BlockPos pos, Box playerBounds, long now) {
      if (this.fireworkDeployments.containsKey(pos)) {
         return true;
      }

      boolean threatened = this.isHot(pos, now) || this.crystalProtect.getValue() && this.hasCrystalAt(pos);
      return threatened && this.isFullBlock(pos.up()) && this.isCrystalBase(pos.down()) && this.hasCornerSupport(pos, playerBounds);
   }

   private BlockHitResult fireworkHit(BlockPos pos) {
      Vec3d player = mc.player.getEntityPos();
      double x = pos.getX() + (player.x >= pos.getX() + 0.5 ? 0.15 : 0.85);
      double z = pos.getZ() + (player.z >= pos.getZ() + 0.5 ? 0.15 : 0.85);
      return new BlockHitResult(new Vec3d(x, pos.getY(), z), Direction.UP, pos.down().toImmutable(), false);
   }

   private boolean hasCornerSupport(BlockPos pos, Box bounds) {
      int minX = minCell(bounds.minX);
      int maxX = maxCell(bounds.maxX);
      int minZ = minCell(bounds.minZ);
      int maxZ = maxCell(bounds.maxZ);
      int x = pos.getX();
      int z = pos.getZ();
      if ((z < minZ || z > maxZ) && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.west()) && this.isFullBlock(pos.east());
      } else {
         return (x < minX || x > maxX) && z >= minZ && z <= maxZ ? this.isFullBlock(pos.north()) && this.isFullBlock(pos.south()) : false;
      }
   }

   private EndCrystalEntity findExtendCrystal() {
      Box area = mc.player.getBoundingBox().expand(1.5, 0.5, 1.5).offset(0.0, 1.0, 0.0);
      Vec3d eye = mc.player.getEyePos();
      EndCrystalEntity best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (EndCrystalEntity crystal : mc.world.getNonSpectatingEntities(EndCrystalEntity.class, area)) {
         double distance = crystal.squaredDistanceTo(eye);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = crystal;
         }
      }

      return best;
   }

   private boolean hasCrystalAt(BlockPos pos) {
      return !mc.world.getNonSpectatingEntities(EndCrystalEntity.class, new Box(pos).expand(0.5, 1.0, 0.5)).isEmpty();
   }

   private boolean hasBlockingCrystal(BlockPos pos) {
      for (EndCrystalEntity crystal : mc.world.getNonSpectatingEntities(EndCrystalEntity.class, new Box(pos))) {
         if (crystal.isAlive()) {
            return true;
         }
      }

      return false;
   }

   private boolean hasLiveFireworkAt(BlockPos pos) {
      Box box = new Box(pos).expand(0.05);

      for (FireworkRocketEntity rocket : mc.world.getNonSpectatingEntities(FireworkRocketEntity.class, box)) {
         if (rocket.isAlive()) {
            return true;
         }
      }

      return false;
   }

   private boolean isFullBlock(BlockPos pos) {
      return Block.isShapeFullCube(mc.world.getBlockState(pos).getCollisionShape(mc.world, pos));
   }

   private boolean isCrystalBase(BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
   }

   private boolean isPhased(PlayerEntity player) {
      BlockState state = mc.world.getBlockState(player.getBlockPos());
      return !state.isAir() && !state.isReplaceable();
   }

   private boolean isHot(BlockPos pos, long now) {
      synchronized (this.breakTimes) {
         ArrayDeque<Long> times = this.breakTimes.get(pos);
         if (times == null) {
            return false;
         }

         while (!times.isEmpty() && now - times.peekFirst() > 1000L) {
            times.pollFirst();
         }

         return times.size() >= 3;
      }
   }

   private void recordBreakNotice(BlockPos pos, long now) {
      synchronized (this.breakTimes) {
         ArrayDeque<Long> times = this.breakTimes.computeIfAbsent(pos.toImmutable(), ignored -> new ArrayDeque<>());

         while (!times.isEmpty() && now - times.peekFirst() > 1000L) {
            times.pollFirst();
         }

         times.addLast(now);
      }
   }

   private void clearBreakHistory(BlockPos pos) {
      synchronized (this.breakTimes) {
         this.breakTimes.remove(pos);
      }
   }

   private void collectExpired(long now) {
      this.breakThreats.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);
      this.attackedCrystals.entrySet().removeIf(entry -> now - entry.getValue() > 2000L);
      this.explosionRepairs.entrySet().removeIf(entry -> now - entry.getValue() > 100L);
      this.renderMap.entrySet().removeIf(entry -> now - entry.getValue() > this.fadeTime.getValue() * 1000.0);
      synchronized (this.breakTimes) {
         this.breakTimes.entrySet().removeIf(entry -> {
            ArrayDeque<Long> times = entry.getValue();

            while (!times.isEmpty() && now - times.peekFirst() > 1000L) {
               times.pollFirst();
            }

            return times.isEmpty();
         });
      }
   }

   private void releaseAllPlacements() {
      for (BlockPos pos : Set.copyOf(this.managed)) {
         Homovore2Client.placements().unmaintain(pos);
      }

      for (BlockPos pos : Set.copyOf(this.queued)) {
         Homovore2Client.placements().unmaintain(pos);
      }

      this.managed.clear();
      this.queued.clear();
   }

   private void resetLocalState() {
      this.managed.clear();
      this.queued.clear();
      synchronized (this.breakTimes) {
         this.breakTimes.clear();
      }

      this.breakThreats.clear();
      this.fireworkDeployments.clear();
      this.renderMap.clear();
      this.attackedCrystals.clear();
      this.explosionRepairs.clear();
      this.placedPistons.clear();
      this.pistonUsers.clear();
      this.wanted = Set.of();
      this.lastExtendOffset = null;
      this.lastExtendThreat = 0L;
      this.lastCrystalNearHead = 0L;
      this.lastAttack = 0L;
      this.antiPistonEpoch = 0L;
      this.lastOptions = null;
   }

   private boolean isEatingOrStarting(InventorySnapshot inventory) {
      if (inventory.usingItem()) {
         return (inventory.usingMainHand() ? inventory.item(inventory.selectedSlot()) : inventory.offhand()).contains(DataComponentTypes.FOOD);
      } else {
         return !mc.options.useKey.isPressed()
            ? false
            : inventory.item(inventory.selectedSlot()).contains(DataComponentTypes.FOOD) || inventory.offhand().contains(DataComponentTypes.FOOD);
      }
   }

   private static List<BlockPos> footCells(int minX, int maxX, int minZ, int maxZ, int y) {
      List<BlockPos> cells = new ArrayList<>();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            cells.add(new BlockPos(x, y, z));
         }
      }

      return cells;
   }

   private static boolean nearOwnPerimeter(BlockPos pos, Set<BlockPos> feet) {
      for (BlockPos foot : feet) {
         if (pos.equals(foot)
            || pos.equals(foot.up())
            || pos.equals(foot.down())
            || pos.equals(foot.north())
            || pos.equals(foot.south())
            || pos.equals(foot.east())
            || pos.equals(foot.west())) {
            return true;
         }
      }

      return false;
   }

   private static boolean isSideOfFootprint(BlockPos pos, List<BlockPos> feet) {
      for (BlockPos foot : feet) {
         if (pos.getY() == foot.getY() && Math.abs(pos.getX() - foot.getX()) + Math.abs(pos.getZ() - foot.getZ()) == 1) {
            return true;
         }
      }

      return false;
   }

   private static boolean threatens(BlockPos crystal, Set<BlockPos> protectedCells) {
      BlockPos below = crystal.down();
      if (!protectedCells.contains(crystal) && !protectedCells.contains(below)) {
         for (Direction direction : HORIZONTALS) {
            if (protectedCells.contains(crystal.offset(direction)) || protectedCells.contains(below.offset(direction))) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static int minCell(double coordinate) {
      return MathHelper.floor(coordinate);
   }

   private static int maxCell(double coordinate) {
      return MathHelper.ceil(coordinate) - 1;
   }

   public enum ExtendMode {
      NONE,
      SMART,
      ALWAYS;
   }

   private record FireworkDeployment(long submittedAt, boolean observed) {
      private SelfTrapModule.FireworkDeployment observe() {
         return this.observed ? this : new SelfTrapModule.FireworkDeployment(this.submittedAt, true);
      }
   }

   private record FireworkLaunch(BlockPos pos, BlockHitResult hit, SelfTrapModule.FireworkDeployment deployment) {
   }

   private record ReactiveState(
      boolean enabled,
      boolean attack,
      boolean fireworks,
      boolean prePlace,
      int playerId,
      long generation,
      Set<BlockPos> protectedCells,
      Map<BlockPos, BlockHitResult> fireworkHits
   ) {
      private static final SelfTrapModule.ReactiveState DISABLED = new SelfTrapModule.ReactiveState(false, false, false, false, -1, 0L, Set.of(), Map.of());
   }

   public enum SelfTrapMode {
      NONE,
      SMART,
      ALWAYS;
   }
}
