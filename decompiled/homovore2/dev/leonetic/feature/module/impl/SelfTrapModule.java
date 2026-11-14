package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.render.BlockRender;
import dev.leonetic.util.RotationUtil;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkActionContext;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.world.PlacementWorldSnapshot;
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
import net.minecraft.class_1268;
import net.minecraft.class_1299;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1671;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2604;
import net.minecraft.class_2664;
import net.minecraft.class_2680;
import net.minecraft.class_2716;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_4184;
import net.minecraft.class_9334;

public final class SelfTrapModule extends Module {
   private static final double ANTI_PISTON_TRIGGER_RANGE_SQ = 25.0;
   private static final long FIREWORK_APPEAR_TIMEOUT_MS = 1500L;
   private static final long REBREAK_WINDOW_MS = 1000L;
   private static final int REBREAK_THRESHOLD = 3;
   private static final long BREAK_THREAT_MS = 1000L;
   private static final long EXTEND_HOLD_MS = 1000L;
   private static final long ATTACK_RETRY_MS = 250L;
   private static final long EXPLOSION_REPAIR_MS = 100L;
   private static final class_2350[] HORIZONTALS = new class_2350[]{
      class_2350.field_11043, class_2350.field_11035, class_2350.field_11034, class_2350.field_11039
   };
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
   private final Set<class_2338> managed = new HashSet<>();
   private final Set<class_2338> queued = ConcurrentHashMap.newKeySet();
   private final Map<class_2338, ArrayDeque<Long>> breakTimes = new HashMap<>();
   private final Map<class_2338, Long> breakThreats = new ConcurrentHashMap<>();
   private final Map<class_2338, SelfTrapModule.FireworkDeployment> fireworkDeployments = new ConcurrentHashMap<>();
   private final Map<class_2338, Long> renderMap = new ConcurrentHashMap<>();
   private final Map<Integer, Long> attackedCrystals = new ConcurrentHashMap<>();
   private final Map<class_2338, Long> explosionRepairs = new ConcurrentHashMap<>();
   private final ConcurrentLinkedQueue<class_2338> placedPistons = new ConcurrentLinkedQueue<>();
   private final AtomicReference<SelfTrapModule.ReactiveState> reactiveState = new AtomicReference<>(SelfTrapModule.ReactiveState.DISABLED);
   private final AtomicLong generation = new AtomicLong();
   private Set<class_2338> wanted = Set.of();
   private class_2338 lastExtendOffset;
   private long lastExtendThreat;
   private long lastCrystalNearHead;
   private long lastAttack;
   private long antiPistonEpoch;
   private final Set<UUID> pistonUsers = new HashSet<>();
   private PlacementManager.Options lastOptions;
   private final PlacementManager.PlacementListener placementListener = new PlacementManager.PlacementListener() {
      @Override
      public void onBlockUpdate(class_2338 pos, class_2680 blockState, boolean wasReplaceable) {
         if (wasReplaceable && SelfTrapModule.isPiston(blockState)) {
            SelfTrapModule.this.placedPistons.add(pos);
         }

         SelfTrapModule.this.queued.remove(pos);
         boolean nowAir = blockState.method_26215();
         if (nowAir) {
            SelfTrapModule.ReactiveState state = SelfTrapModule.this.reactiveState.get();
            if (state.enabled && state.protectedCells.contains(pos)) {
               long now = System.currentTimeMillis();
               SelfTrapModule.this.renderMap.put(pos, now);
               if (!SelfTrapModule.this.fireworkDeployments.containsKey(pos)) {
                  SelfTrapModule.this.recordBreakNotice(pos, now);
                  class_3965 fireworkHit = state.fireworkHits.get(pos);
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
      public void onBreakProgress(class_2338 pos, int breakerId, int progress) {
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

   public boolean isSurroundPos(class_2338 pos) {
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
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1755 == null && NetworkThreadExecutor.isConnected()) {
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
            boolean hasObsidian = InventoryUtil.find(class_1802.field_8281, InventoryUtil.HOTBAR_SCOPE).found();
            long now = System.currentTimeMillis();
            this.collectExpired(now);
            this.refreshFireworkDeployments(now);
            boolean antiPistonActive = this.updateAntiPistonThreats();
            class_238 bounds = mc.field_1724.method_5829();
            int feetY = mc.field_1724.method_24515().method_10264();
            int minX = minCell(bounds.field_1323);
            int maxX = maxCell(bounds.field_1320);
            int minZ = minCell(bounds.field_1321);
            int maxZ = maxCell(bounds.field_1324);
            List<class_2338> feet = footCells(minX, maxX, minZ, maxZ, feetY);
            LinkedHashSet<class_2338> desired = new LinkedHashSet<>();
            Set<class_2338> extensionCells = new HashSet<>();
            this.baseSurround(feet, minX, maxX, minZ, maxZ, desired, now, antiPistonActive);
            this.computeExtend(feet, desired, extensionCells, now);
            this.applyMineProtect(feet, desired, extensionCells, now);
            this.addRebreakCover(feet, desired, now);
            this.addHeadTrap(feet, minX, maxX, minZ, maxZ, desired, antiPistonActive);
            Set<class_2338> helpBlocked = this.computeHelpBlocked(minX, maxX, minZ, maxZ, feetY, desired);
            desired.removeAll(helpBlocked);
            DoubleMineModule mine = Homovore2Client.modules().get(DoubleMineModule.class);
            if (mine != null && mine.isEnabled()) {
               desired.removeIf(mine::isMining);
            }

            Set<class_2338> protectedCells = new HashSet<>(desired);
            Set<class_2338> packetReactiveCells = new HashSet<>(protectedCells);
            this.addPacketReactiveSelfTrapCells(feet, minX, maxX, minZ, maxZ, packetReactiveCells);
            if (mine != null && mine.isEnabled()) {
               packetReactiveCells.removeIf(mine::isMining);
            }

            PlacementWorldSnapshot.Snapshot placementWorld = PlacementWorldSnapshot.snapshot();
            if (placementWorld.available()) {
               Set<class_2338> crystalOccupied = new HashSet<>();

               for (class_2338 pos : desired) {
                  if (placementWorld.entityCollision(pos) && this.hasBlockingCrystal(pos)) {
                     crystalOccupied.add(pos);
                  }
               }

               desired.removeIf(pos -> placementWorld.selfCollision(pos) || placementWorld.entityCollision(pos) && !crystalOccupied.contains(pos));
            }

            class_243 predicted = mc.field_1724.method_73189().method_1019(mc.field_1724.method_18798().method_1021(0.5));
            List<class_2338> ordered = desired.stream().sorted(Comparator.comparingDouble(pos -> class_243.method_24953(pos).method_1025(predicted))).toList();
            Result firework = InventoryUtil.find(class_1802.field_8639, InventoryUtil.FULL_SCOPE);
            boolean canLaunchFireworks = this.fireworks.getValue() && firework.found();
            List<class_2338> fireworkCandidates = new ArrayList<>(ordered);
            if (this.fireworks.getValue()) {
               for (class_2338 foot : feet) {
                  for (class_2350 direction : HORIZONTALS) {
                     fireworkCandidates.add(foot.method_10079(direction, 2).method_10062());
                  }
               }
            }

            Map<class_2338, class_3965> fireworkHits = this.fireworks.getValue() ? this.fireworkTargets(fireworkCandidates, bounds, now) : Map.of();
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
      if (event.getPacket() instanceof class_2716 removed) {
         IntListIterator var14 = removed.method_36548().iterator();

         while (var14.hasNext()) {
            int id = var14.next();
            this.attackedCrystals.remove(id);
         }
      } else if (event.getPacket() instanceof class_2664 explosion) {
         this.refillVacatedCells(explosion.comp_2883());
      } else if (event.getPacket() instanceof class_2604 packet && packet.method_11169() == class_1299.field_6110) {
         SelfTrapModule.ReactiveState state = this.reactiveState.get();
         if (state.enabled && state.attack) {
            class_2338 cell = new class_2338(
               class_3532.method_15357(packet.method_11175()), class_3532.method_15357(packet.method_11174()), class_3532.method_15357(packet.method_11176())
            );
            if (threatens(cell, state.protectedCells)) {
               class_238 crystalBox = class_1299.field_6110.method_18386().method_30231(packet.method_11175(), packet.method_11174(), packet.method_11176());
               List<class_2338> repairCells = this.crystalRepairCells(crystalBox, state.protectedCells);
               long now = System.currentTimeMillis();
               Long previous = this.attackedCrystals.putIfAbsent(packet.method_11167(), now);
               if (previous == null || now - previous >= 250L) {
                  this.attackedCrystals.put(packet.method_11167(), now);
                  InventorySnapshot inventory = InventoryUtil.snapshot();
                  if (inventory.available() && inventory.playerId() == state.playerId && !inventory.deadOrDying()) {
                     RotationManager.Angles angles = RotationUtil.anglesTo(
                        inventory.eyePosition(), new class_243(packet.method_11175(), packet.method_11174() + 1.0, packet.method_11176())
                     );
                     NetworkThreadExecutor.executeNow(context -> {
                        if (!this.attackAndRepairNow(context, inventory, angles, packet.method_11167(), repairCells, now)) {
                           this.attackedCrystals.remove(packet.method_11167(), now);
                        }
                     });
                  } else {
                     this.attackedCrystals.remove(packet.method_11167(), now);
                  }
               }
            }
         }
      }
   }

   private void refillVacatedCells(class_243 center) {
      SelfTrapModule.ReactiveState state = this.reactiveState.get();
      if (state.enabled && state.prePlace) {
         class_238 blast = class_1299.field_6110.method_18386().method_30231(center.field_1352, center.field_1351, center.field_1350);
         List<class_2338> repairCells = this.crystalRepairCells(blast, state.protectedCells);
         if (!repairCells.isEmpty()) {
            InventorySnapshot inventory = InventoryUtil.snapshot();
            if (inventory.available() && inventory.playerId() == state.playerId && !inventory.deadOrDying()) {
               long now = System.currentTimeMillis();
               PlacementManager.Options options = new PlacementManager.Options(true, true, false, false);
               NetworkThreadExecutor.executeNow(context -> {
                  List<class_2338> due = new ArrayList<>(repairCells.size());

                  for (class_2338 pos : repairCells) {
                     Long previous = this.explosionRepairs.get(pos);
                     if (previous == null || now - previous >= 100L) {
                        due.add(pos);
                     }
                  }

                  if (!due.isEmpty()) {
                     for (class_2338 pos : Homovore2Client.placements().placeAllNow(context, due, null, class_1802.field_8281, options)) {
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
      }
   }

   private void baseSurround(List<class_2338> feet, int minX, int maxX, int minZ, int maxZ, Set<class_2338> desired, long now, boolean antiPistonActive) {
      for (class_2338 foot : feet) {
         for (class_2350 direction : HORIZONTALS) {
            class_2338 adjacent = foot.method_10093(direction);
            if (adjacent.method_10263() < minX || adjacent.method_10263() > maxX || adjacent.method_10260() < minZ || adjacent.method_10260() > maxZ) {
               desired.add(adjacent.method_10062());
               if (this.selfTrap.getValue()) {
                  if (antiPistonActive) {
                     desired.add(adjacent.method_10084().method_10062());
                  } else if (this.selfTrapMode.getValue() != SelfTrapModule.SelfTrapMode.NONE) {
                     this.addUpperTrap(adjacent, desired, now);
                  }
               }
            }
         }

         desired.add(foot.method_10074().method_10062());
      }
   }

   private void addUpperTrap(class_2338 adjacent, Set<class_2338> desired, long now) {
      boolean should = this.selfTrapMode.getValue() == SelfTrapModule.SelfTrapMode.ALWAYS;
      if (this.selfTrapMode.getValue() == SelfTrapModule.SelfTrapMode.SMART) {
         if (this.hasCrystalAt(adjacent.method_10084())) {
            this.lastCrystalNearHead = now;
         }

         should = now - this.lastCrystalNearHead < 1000L;
      }

      if (should) {
         desired.add(adjacent.method_10084().method_10062());
      }
   }

   private void addHeadTrap(List<class_2338> feet, int minX, int maxX, int minZ, int maxZ, Set<class_2338> desired, boolean antiPistonActive) {
      if (this.selfTrap.getValue() && (this.selfTrapHead.getValue() || antiPistonActive)) {
         boolean prone = this.crawlTrap.getValue() && (mc.field_1724.method_20448() || mc.field_1724.method_6128());
         int height = prone ? 1 : 2;
         if (this.selfTrapHead.getValue()) {
            desired.add(mc.field_1724.method_24515().method_10086(height).method_10062());
         }

         if (antiPistonActive) {
            for (class_2338 foot : feet) {
               class_2338 head = foot.method_10086(height);

               for (class_2350 direction : HORIZONTALS) {
                  class_2338 cardinal = head.method_10093(direction);
                  if (cardinal.method_10263() < minX || cardinal.method_10263() > maxX || cardinal.method_10260() < minZ || cardinal.method_10260() > maxZ) {
                     desired.add(cardinal.method_10062());
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

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724 && player.method_5805()) {
               alive.add(player.method_5667());
            }
         }

         this.pistonUsers.retainAll(alive);

         class_2338 piston;
         while ((piston = this.placedPistons.poll()) != null) {
            class_243 center = class_243.method_24953(piston);
            if (!(mc.field_1724.method_73189().method_1025(center) > 25.0)) {
               class_1657 user = null;
               double closest = Double.POSITIVE_INFINITY;

               for (class_1657 player : mc.field_1687.method_18456()) {
                  if (player != mc.field_1724 && player.method_5805() && !Homovore2Client.friends().isFriend(player)) {
                     double distance = player.method_33571().method_1025(center);
                     if (!(distance > 25.0) && !(distance >= closest)) {
                        closest = distance;
                        user = player;
                     }
                  }
               }

               if (user != null) {
                  this.pistonUsers.add(user.method_5667());
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

   private static boolean isPiston(class_2680 state) {
      return state.method_27852(class_2246.field_10560) || state.method_27852(class_2246.field_10615);
   }

   private void addPacketReactiveSelfTrapCells(List<class_2338> feet, int minX, int maxX, int minZ, int maxZ, Set<class_2338> reactiveCells) {
      if (this.selfTrap.getValue() && this.selfTrapMode.getValue() != SelfTrapModule.SelfTrapMode.NONE) {
         for (class_2338 foot : feet) {
            for (class_2350 direction : HORIZONTALS) {
               class_2338 adjacent = foot.method_10093(direction);
               if (adjacent.method_10263() < minX || adjacent.method_10263() > maxX || adjacent.method_10260() < minZ || adjacent.method_10260() > maxZ) {
                  reactiveCells.add(adjacent.method_10084().method_10062());
               }
            }
         }
      }
   }

   private void computeExtend(List<class_2338> feet, Set<class_2338> desired, Set<class_2338> extensionCells, long now) {
      if (this.extend.getValue() && this.extendMode.getValue() != SelfTrapModule.ExtendMode.NONE) {
         if (this.extendMode.getValue() == SelfTrapModule.ExtendMode.ALWAYS) {
            for (class_2338 foot : feet) {
               for (class_2350 direction : HORIZONTALS) {
                  this.addExtendBlocks(foot, direction.method_10148(), direction.method_10165(), desired, extensionCells);
               }
            }
         } else {
            class_1511 crystal = this.findExtendCrystal();
            if (crystal != null) {
               this.lastExtendOffset = crystal.method_24515().method_10059(mc.field_1724.method_24515());
               this.lastExtendThreat = now;
            }

            if (this.lastExtendOffset != null && now - this.lastExtendThreat < 1000L) {
               int dx = Integer.signum(this.lastExtendOffset.method_10263());
               int dz = Integer.signum(this.lastExtendOffset.method_10260());

               for (class_2338 foot : feet) {
                  this.addExtendBlocks(foot, dx, dz, desired, extensionCells);
               }
            }
         }
      }
   }

   private void addExtendBlocks(class_2338 foot, int dx, int dz, Set<class_2338> desired, Set<class_2338> extensionCells) {
      if (dx != 0 && dz != 0) {
         this.addSupported(foot.method_10069(dx, 0, dz), desired, extensionCells);
         this.addSupported(foot.method_10069(dx * 2, 0, 0), desired, extensionCells);
         this.addSupported(foot.method_10069(0, 0, dz * 2), desired, extensionCells);
      } else if (dx != 0 ^ dz != 0) {
         if (dx != 0) {
            this.addSupported(foot.method_10069(dx, 0, 1), desired, extensionCells);
            this.addSupported(foot.method_10069(dx, 0, -1), desired, extensionCells);
            this.addSupported(foot.method_10069(dx * 2, 0, 0), desired, extensionCells);
         } else {
            this.addSupported(foot.method_10069(1, 0, dz), desired, extensionCells);
            this.addSupported(foot.method_10069(-1, 0, dz), desired, extensionCells);
            this.addSupported(foot.method_10069(0, 0, dz * 2), desired, extensionCells);
         }
      }
   }

   private void addSupported(class_2338 pos, Set<class_2338> desired, Set<class_2338> extensionCells) {
      class_2680 below = mc.field_1687.method_8320(pos.method_10074());
      if (below.method_27852(class_2246.field_10540) || below.method_27852(class_2246.field_9987)) {
         desired.add(pos.method_10062());
         extensionCells.add(pos.method_10062());
      }
   }

   private void applyMineProtect(List<class_2338> feet, Set<class_2338> desired, Set<class_2338> extensionCells, long now) {
      if (this.mineProtect.getValue()) {
         for (Entry<class_2338, Long> entry : this.breakThreats.entrySet()) {
            if (now - entry.getValue() <= 1000L) {
               class_2338 mined = entry.getKey();

               for (class_2338 foot : feet) {
                  if (mined.method_10264() == foot.method_10264()) {
                     int dx = mined.method_10263() - foot.method_10263();
                     int dz = mined.method_10260() - foot.method_10260();
                     if (Math.abs(dx) + Math.abs(dz) == 1) {
                        addDefense(mined.method_10084(), desired, extensionCells);
                        addDefense(mined.method_10074(), desired, extensionCells);
                        if (dx != 0) {
                           addDefense(foot.method_10069(dx, 0, 1), desired, extensionCells);
                           addDefense(foot.method_10069(dx, 0, -1), desired, extensionCells);
                           addDefense(foot.method_10069(dx * 2, 0, 0), desired, extensionCells);
                        } else {
                           addDefense(foot.method_10069(1, 0, dz), desired, extensionCells);
                           addDefense(foot.method_10069(-1, 0, dz), desired, extensionCells);
                           addDefense(foot.method_10069(0, 0, dz * 2), desired, extensionCells);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void addDefense(class_2338 pos, Set<class_2338> desired, Set<class_2338> extensionCells) {
      desired.add(pos.method_10062());
      extensionCells.add(pos.method_10062());
   }

   private void addRebreakCover(List<class_2338> feet, Set<class_2338> desired, long now) {
      if (this.fireworks.getValue()) {
         Set<class_2338> footSet = Set.copyOf(feet);

         for (class_2338 hot : desired.stream().filter(pos -> isSideOfFootprint(pos, feet)).filter(pos -> this.isHot(pos, now)).toList()) {
            for (class_2350 direction : HORIZONTALS) {
               class_2338 neighbor = hot.method_10093(direction).method_10062();
               if (!footSet.contains(neighbor)) {
                  desired.add(neighbor);
               }
            }
         }
      }
   }

   private Set<class_2338> computeHelpBlocked(int minX, int maxX, int minZ, int maxZ, int feetY, Set<class_2338> desired) {
      if (this.avoidHelping.getValue() && (minX != maxX || minZ != maxZ) && !(mc.field_1724.method_6032() < 10.0F)) {
         Set<class_2338> opponents = new HashSet<>();
         class_2338 self = mc.field_1724.method_24515();

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724
               && !(player.method_24515().method_10262(self) > 25.0)
               && !this.isPhased(player)
               && !Homovore2Client.friends().isFriend(player)) {
               class_238 box = player.method_5829();
               int pMinX = minCell(box.field_1323);
               int pMaxX = maxCell(box.field_1320);
               int pMinZ = minCell(box.field_1321);
               int pMaxZ = maxCell(box.field_1324);
               int y = player.method_24515().method_10264();

               for (int x = pMinX; x <= pMaxX; x++) {
                  for (int z = pMinZ; z <= pMaxZ; z++) {
                     class_2338 foot = new class_2338(x, y, z);

                     for (class_2350 direction : HORIZONTALS) {
                        class_2338 adjacent = foot.method_10093(direction);
                        if (adjacent.method_10263() < pMinX
                           || adjacent.method_10263() > pMaxX
                           || adjacent.method_10260() < pMinZ
                           || adjacent.method_10260() > pMaxZ) {
                           opponents.add(adjacent.method_10062());
                        }
                     }

                     opponents.add(foot.method_10074().method_10062());
                     opponents.add(foot.method_10062());
                  }
               }
            }
         }

         Set<class_2338> myFootCells = new HashSet<>(footCells(minX, maxX, minZ, maxZ, feetY));
         Set<class_2338> blocked = new HashSet<>();

         for (class_2338 position : opponents) {
            if (desired.contains(position) && !nearOwnPerimeter(position, myFootCells)) {
               blocked.add(position);
            }
         }

         return blocked;
      } else {
         return Set.of();
      }
   }

   private Map<class_2338, class_3965> fireworkTargets(List<class_2338> positions, class_238 playerBounds, long now) {
      Map<class_2338, class_3965> hits = new LinkedHashMap<>();

      for (class_2338 pos : positions) {
         if (this.canRocket(pos, playerBounds, now)) {
            hits.put(pos, this.fireworkHit(pos));
         }
      }

      return hits;
   }

   private void reconcilePlacements(List<class_2338> ordered, Set<class_2338> fireworkTargets, PlacementManager.Options options, long now) {
      Set<class_2338> desiredObsidian = new LinkedHashSet<>(ordered);
      desiredObsidian.removeAll(fireworkTargets);

      for (class_2338 old : Set.copyOf(this.managed)) {
         if (!this.reactive.getValue() || !desiredObsidian.contains(old)) {
            Homovore2Client.placements().unmaintain(old);
            this.managed.remove(old);
         }
      }

      for (class_2338 old : Set.copyOf(this.queued)) {
         if (this.reactive.getValue() || !desiredObsidian.contains(old)) {
            Homovore2Client.placements().unmaintain(old);
            this.queued.remove(old);
         }
      }

      boolean optionsChanged = !options.equals(this.lastOptions);
      Set<class_2338> inFlight = Homovore2Client.placements().pending();

      for (class_2338 pos : desiredObsidian) {
         class_2680 state = mc.field_1687.method_8320(pos);
         if (this.reactive.getValue()) {
            if (this.managed.add(pos) || optionsChanged) {
               Homovore2Client.placements().maintain(pos, null, class_1802.field_8281, options);
            }
         } else if (state.method_45474() && !inFlight.contains(pos) && this.queued.add(pos)) {
            Homovore2Client.placements().enqueue(pos, null, class_1802.field_8281, options).thenAccept(accepted -> {
               if (!accepted) {
                  this.queued.remove(pos);
               }
            });
         }

         if (state.method_45474()) {
            this.renderMap.put(pos, now);
         }
      }

      this.lastOptions = options;
   }

   private void deployReadyFireworks(Map<class_2338, class_3965> targets, Result firework, long now, long activeGeneration) {
      List<SelfTrapModule.FireworkLaunch> ready = new ArrayList<>();

      for (Entry<class_2338, class_3965> entry : targets.entrySet()) {
         class_2338 pos = entry.getKey();
         if (!this.fireworkDeployments.containsKey(pos) && !Homovore2Client.placements().isPending(pos) && mc.field_1687.method_8320(pos).method_45474()) {
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
         List<class_3965> hits = ready.stream().map(SelfTrapModule.FireworkLaunch::hit).toList();
         Homovore2Client.placements().useItemOn(hits, firework, false).whenComplete((sent, error) -> {
            if (this.generation.get() == activeGeneration && error == null) {
               long stamp = System.currentTimeMillis();

               for (int i = 0; i < Math.min(sent, ready.size()); i++) {
                  SelfTrapModule.FireworkLaunch launch = ready.get(i);
                  class_2338 posx = launch.pos();
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

   private void deployFirework(List<class_3965> hits, class_2338 pos, SelfTrapModule.FireworkDeployment deployment, long activeGeneration) {
      Result firework = InventoryUtil.find(class_1802.field_8639, InventoryUtil.FULL_SCOPE);
      Homovore2Client.placements().useItemOn(hits, firework, false).whenComplete((sent, error) -> {
         if (error == null && sent > 0 && this.generation.get() == activeGeneration) {
            this.fireworkDeployments.replace(pos, deployment, new SelfTrapModule.FireworkDeployment(System.currentTimeMillis(), false));
            this.clearBreakHistory(pos);
         } else {
            this.fireworkDeployments.remove(pos, deployment);
         }
      });
   }

   private SelfTrapModule.FireworkDeployment reserveFirework(class_2338 pos, long now) {
      SelfTrapModule.FireworkDeployment deployment = new SelfTrapModule.FireworkDeployment(now, false);
      return this.fireworkDeployments.putIfAbsent(pos.method_10062(), deployment) == null ? deployment : null;
   }

   private void releaseFirework(SelfTrapModule.FireworkLaunch launch) {
      this.fireworkDeployments.remove(launch.pos(), launch.deployment());
   }

   private void refreshFireworkDeployments(long now) {
      for (Entry<class_2338, SelfTrapModule.FireworkDeployment> entry : this.fireworkDeployments.entrySet()) {
         class_2338 pos = entry.getKey();
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

   private void attackThreateningCrystal(InventorySnapshot inventory, class_238 playerBounds, long now) {
      if (this.attack.getValue() && now - this.lastAttack >= 50L) {
         class_1511 best = null;
         double bestDistance = Double.POSITIVE_INFINITY;
         class_243 eye = inventory.eyePosition();

         for (class_1511 crystal : mc.field_1687.method_18467(class_1511.class, playerBounds.method_1014(6.0))) {
            if (threatens(crystal.method_24515(), this.wanted)) {
               double distance = RotationUtil.distanceTo(eye, crystal.method_5829());
               if (!(distance > 6.0) && !(distance >= bestDistance)) {
                  bestDistance = distance;
                  best = crystal;
               }
            }
         }

         if (best != null) {
            int id = best.method_5628();
            Long attacked = this.attackedCrystals.get(id);
            if (attacked == null || now - attacked >= 250L) {
               List<class_2338> repairCells = this.crystalRepairCells(best.method_5829(), this.wanted);
               this.attackedCrystals.put(id, now);
               this.lastAttack = now;
               RotationManager.Angles angles = RotationUtil.anglesTo(eye, RotationUtil.closestAimPoint(eye, best.method_5829()));
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
      NetworkActionContext context, InventorySnapshot inventory, RotationManager.Angles angles, int entityId, List<class_2338> repairCells, long now
   ) {
      try (RotationManager.SilentRotation rotation = Homovore2Client.rotations().beginSilentNow(context, inventory, angles)) {
         if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
            return false;
         }

         if (!context.attack(entityId)) {
            return false;
         }

         context.swing(class_1268.field_5808);
         PlacementManager.Options options = new PlacementManager.Options(true, true, false, false);

         for (class_2338 pos : Homovore2Client.placements().forcePlaceAllNow(context, repairCells, null, class_1802.field_8281, options)) {
            this.renderMap.put(pos, now);
         }

         return true;
      }
   }

   private List<class_2338> crystalRepairCells(class_238 crystalBox, Set<class_2338> protectedCells) {
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      return protectedCells.stream()
         .filter(world::replaceable)
         .filter(pos -> crystalBox.method_994(new class_238(pos)))
         .sorted(Comparator.comparingDouble(pos -> world.distanceToSqr(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5)))
         .toList();
   }

   private boolean canRocket(class_2338 pos, class_238 playerBounds, long now) {
      if (this.fireworkDeployments.containsKey(pos)) {
         return true;
      }

      boolean threatened = this.isHot(pos, now) || this.crystalProtect.getValue() && this.hasCrystalAt(pos);
      return threatened && this.isFullBlock(pos.method_10084()) && this.isCrystalBase(pos.method_10074()) && this.hasCornerSupport(pos, playerBounds);
   }

   private class_3965 fireworkHit(class_2338 pos) {
      class_243 player = mc.field_1724.method_73189();
      double x = pos.method_10263() + (player.field_1352 >= pos.method_10263() + 0.5 ? 0.15 : 0.85);
      double z = pos.method_10260() + (player.field_1350 >= pos.method_10260() + 0.5 ? 0.15 : 0.85);
      return new class_3965(new class_243(x, pos.method_10264(), z), class_2350.field_11036, pos.method_10074().method_10062(), false);
   }

   private boolean hasCornerSupport(class_2338 pos, class_238 bounds) {
      int minX = minCell(bounds.field_1323);
      int maxX = maxCell(bounds.field_1320);
      int minZ = minCell(bounds.field_1321);
      int maxZ = maxCell(bounds.field_1324);
      int x = pos.method_10263();
      int z = pos.method_10260();
      if ((z < minZ || z > maxZ) && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.method_10067()) && this.isFullBlock(pos.method_10078());
      } else {
         return (x < minX || x > maxX) && z >= minZ && z <= maxZ ? this.isFullBlock(pos.method_10095()) && this.isFullBlock(pos.method_10072()) : false;
      }
   }

   private class_1511 findExtendCrystal() {
      class_238 area = mc.field_1724.method_5829().method_1009(1.5, 0.5, 1.5).method_989(0.0, 1.0, 0.0);
      class_243 eye = mc.field_1724.method_33571();
      class_1511 best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (class_1511 crystal : mc.field_1687.method_18467(class_1511.class, area)) {
         double distance = crystal.method_5707(eye);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = crystal;
         }
      }

      return best;
   }

   private boolean hasCrystalAt(class_2338 pos) {
      return !mc.field_1687.method_18467(class_1511.class, new class_238(pos).method_1009(0.5, 1.0, 0.5)).isEmpty();
   }

   private boolean hasBlockingCrystal(class_2338 pos) {
      for (class_1511 crystal : mc.field_1687.method_18467(class_1511.class, new class_238(pos))) {
         if (crystal.method_5805()) {
            return true;
         }
      }

      return false;
   }

   private boolean hasLiveFireworkAt(class_2338 pos) {
      class_238 box = new class_238(pos).method_1014(0.05);

      for (class_1671 rocket : mc.field_1687.method_18467(class_1671.class, box)) {
         if (rocket.method_5805()) {
            return true;
         }
      }

      return false;
   }

   private boolean isFullBlock(class_2338 pos) {
      return class_2248.method_9614(mc.field_1687.method_8320(pos).method_26220(mc.field_1687, pos));
   }

   private boolean isCrystalBase(class_2338 pos) {
      class_2680 state = mc.field_1687.method_8320(pos);
      return state.method_27852(class_2246.field_10540) || state.method_27852(class_2246.field_9987);
   }

   private boolean isPhased(class_1657 player) {
      class_2680 state = mc.field_1687.method_8320(player.method_24515());
      return !state.method_26215() && !state.method_45474();
   }

   private boolean isHot(class_2338 pos, long now) {
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

   private void recordBreakNotice(class_2338 pos, long now) {
      synchronized (this.breakTimes) {
         ArrayDeque<Long> times = this.breakTimes.computeIfAbsent(pos.method_10062(), ignored -> new ArrayDeque<>());

         while (!times.isEmpty() && now - times.peekFirst() > 1000L) {
            times.pollFirst();
         }

         times.addLast(now);
      }
   }

   private void clearBreakHistory(class_2338 pos) {
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
      for (class_2338 pos : Set.copyOf(this.managed)) {
         Homovore2Client.placements().unmaintain(pos);
      }

      for (class_2338 pos : Set.copyOf(this.queued)) {
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
         return (inventory.usingMainHand() ? inventory.item(inventory.selectedSlot()) : inventory.offhand()).method_57826(class_9334.field_50075);
      } else {
         return !mc.field_1690.field_1904.method_1434()
            ? false
            : inventory.item(inventory.selectedSlot()).method_57826(class_9334.field_50075) || inventory.offhand().method_57826(class_9334.field_50075);
      }
   }

   private static List<class_2338> footCells(int minX, int maxX, int minZ, int maxZ, int y) {
      List<class_2338> cells = new ArrayList<>();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            cells.add(new class_2338(x, y, z));
         }
      }

      return cells;
   }

   private static boolean nearOwnPerimeter(class_2338 pos, Set<class_2338> feet) {
      for (class_2338 foot : feet) {
         if (pos.equals(foot)
            || pos.equals(foot.method_10084())
            || pos.equals(foot.method_10074())
            || pos.equals(foot.method_10095())
            || pos.equals(foot.method_10072())
            || pos.equals(foot.method_10078())
            || pos.equals(foot.method_10067())) {
            return true;
         }
      }

      return false;
   }

   private static boolean isSideOfFootprint(class_2338 pos, List<class_2338> feet) {
      for (class_2338 foot : feet) {
         if (pos.method_10264() == foot.method_10264()
            && Math.abs(pos.method_10263() - foot.method_10263()) + Math.abs(pos.method_10260() - foot.method_10260()) == 1) {
            return true;
         }
      }

      return false;
   }

   private static boolean threatens(class_2338 crystal, Set<class_2338> protectedCells) {
      class_2338 below = crystal.method_10074();
      if (!protectedCells.contains(crystal) && !protectedCells.contains(below)) {
         for (class_2350 direction : HORIZONTALS) {
            if (protectedCells.contains(crystal.method_10093(direction)) || protectedCells.contains(below.method_10093(direction))) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static int minCell(double coordinate) {
      return class_3532.method_15357(coordinate);
   }

   private static int maxCell(double coordinate) {
      return class_3532.method_15384(coordinate) - 1;
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

   private record FireworkLaunch(class_2338 pos, class_3965 hit, SelfTrapModule.FireworkDeployment deployment) {
   }

   private record ReactiveState(
      boolean enabled,
      boolean attack,
      boolean fireworks,
      boolean prePlace,
      int playerId,
      long generation,
      Set<class_2338> protectedCells,
      Map<class_2338, class_3965> fireworkHits
   ) {
      private static final SelfTrapModule.ReactiveState DISABLED = new SelfTrapModule.ReactiveState(false, false, false, false, -1, 0L, Set.of(), Map.of());
   }

   public enum SelfTrapMode {
      NONE,
      SMART,
      ALWAYS;
   }
}
