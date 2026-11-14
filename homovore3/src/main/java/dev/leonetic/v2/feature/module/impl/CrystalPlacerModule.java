package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.EntityAddedEvent;
import dev.leonetic.v2.event.impl.network.PacketEvent;
import dev.leonetic.v2.event.system.Subscribe;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.module.ModuleManager;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.PlacementManager;
import dev.leonetic.v2.manager.RotationManager;
import dev.leonetic.v2.render.PlacementAnimation;
import dev.leonetic.v2.util.ChatUtil;
import dev.leonetic.v2.util.inventory.InventorySnapshot;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import dev.leonetic.v2.util.inventory.Result;
import dev.leonetic.v2.util.network.NetworkAction;
import dev.leonetic.v2.util.network.NetworkActionContext;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import dev.leonetic.v2.util.network.PacketLimiter;
import dev.leonetic.v2.util.world.CrystalWorldSnapshot;
import dev.leonetic.v2.util.world.ExplosionMath;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class CrystalPlacerModule extends Module {
   private static final int TRACK_CAPACITY = 64;
   private static final long ATTACK_TTL_TICKS = 10L;
   private static final long PLACE_TTL_NANOS = 150000000L;
   private static final int PLACE_LEAD_TICKS = 1;
   private static final int NO_TARGET = Integer.MIN_VALUE;
   private static final long INTENDED_PLACE_TTL_NANOS = 5000000000L;
   private static final long CPS_WINDOW_NANOS = 1000000000L;
   private static final double PISTON_PRIORITY = 100.0;
   public static final double PLACE_REACH = 4.5;
   public static final double BREAK_REACH = 3.0;
   private static final double BLOCK_REACH_SQ = 20.25;
   private static final double ENTITY_REACH_SQ = 9.0;
   public final Setting<Boolean> place = this.bool("Place", true);
   public final Setting<Boolean> doBreak = this.bool("Break", true);
   public final Setting<Boolean> pistons = this.bool("Pistons", true)
      .describe(
         "Also blows up the normal (non-sticky) pistons an opponent is pushing at you. Only fires while a player target is in range, and only from a base whose blast is certain to break the piston."
      );
   public final Setting<Boolean> pauseWhileUsing = this.bool("PauseWhileUsing", true);
   public final Setting<Boolean> debug = this.bool("Debug", false).describe("Shows the current planner gate once per second.").page("Extra");
   public final Setting<Double> targetRange = this.number("TargetRange", (double)18.0, (double)2.0, (double)18.0).page("Ranges");
   public final Setting<Double> pistonRange = this.number("PistonRange", (double)6.0, (double)1.0, (double)6.0)
      .describe("How far a piston may be from you to be worth a crystal.")
      .visibleWhen(() -> this.pistons.getValue())
      .page("Ranges");
   public final Setting<Double> minDamage = this.number("MinDamage", (double)6.0, (double)0.0, (double)36.0).page("Damage");
   public final Setting<Double> maxSelfDamage = this.number("MaxSelfDamage", (double)3.0, (double)0.0, (double)36.0).page("Damage");
   public final Setting<Boolean> damageSync = this.bool("DamageSync", true)
      .describe(
         "Holds crystals back while a turtle-master target is still in its hurt cooldown, where a blast would be swallowed. Compensates for your own ping. Anyone not on turtle is crystalled regardless of cooldown, and piston breaks always fire."
      )
      .page("Damage");
   public final Setting<Boolean> autoMineTarget = this.bool("AutoMineTarget", true)
      .describe("Keeps crystal damage focused on Auto Mine's selected player.")
      .page("Damage");
   public final Setting<Boolean> pane = this.bool("Pane", false)
      .describe("A square that slides along the ground from one placement to the next.")
      .page("Render");
   public final Setting<Color> paneLineColor = this.color("PaneLine", 175, 100, 255, 220).visibleWhen(() -> this.pane.getValue()).page("Render");
   public final Setting<Color> paneSideColor = this.color("PaneSide", 175, 100, 255, 60).visibleWhen(() -> this.pane.getValue()).page("Render");
   public final Setting<Double> paneWidth = this.number("PaneWidth", (double)3.0, (double)0.1, (double)16.0)
      .visibleWhen(() -> this.pane.getValue())
      .page("Render");
   public final Setting<Double> paneSize = this.number("PaneSize", (double)0.9, (double)0.1, (double)2.0)
      .describe("Side length in blocks.")
      .visibleWhen(() -> this.pane.getValue())
      .page("Render");
   public final Setting<Double> paneSpeed = this.number("PaneSpeed", (double)0.06, (double)0.0, (double)0.5)
      .describe("Seconds to cover half the distance to a new placement; 0 snaps.")
      .visibleWhen(() -> this.pane.getValue())
      .page("Render");
   public final Setting<Double> paneLinger = this.number("PaneLinger", (double)0.6, (double)0.0, (double)5.0)
      .describe("Seconds the pane stays after the last placement before fading out.")
      .visibleWhen(() -> this.pane.getValue())
      .page("Render");
   public final Setting<Double> paneFade = this.number("PaneFade", (double)0.5, (double)0.05, (double)2.0)
      .describe("Seconds the pane takes to fade after it stops lingering.")
      .visibleWhen(() -> this.pane.getValue())
      .page("Render");
   public final Setting<Boolean> spinner = this.bool("Spinner", false)
      .describe("Every placement spawns a triangle turned a step further than the last, which expands as it fades, so a stream of placements spins.")
      .page("Render");
   public final Setting<Color> spinnerLineColor = this.color("SpinnerLine", 255, 255, 255, 230).visibleWhen(() -> this.spinner.getValue()).page("Render");
   public final Setting<Color> spinnerSideColor = this.color("SpinnerSide", 175, 100, 255, 0).visibleWhen(() -> this.spinner.getValue()).page("Render");
   public final Setting<Double> spinnerWidth = this.number("SpinnerWidth", (double)3.0, (double)0.1, (double)16.0)
      .visibleWhen(() -> this.spinner.getValue())
      .page("Render");
   public final Setting<Double> spinnerSize = this.number("SpinnerSize", (double)1.5, (double)0.1, (double)2.0)
      .describe("Diameter in blocks of the circle its corners sit on, before it expands.")
      .visibleWhen(() -> this.spinner.getValue())
      .page("Render");
   public final Setting<Double> spinnerStep = this.number("SpinnerStep", (double)20.0, (double)0.0, (double)180.0)
      .describe("Degrees each triangle is turned past the one before it.")
      .visibleWhen(() -> this.spinner.getValue())
      .page("Render");
   public final Setting<Double> spinnerFade = this.number("SpinnerFade", (double)0.5, (double)0.05, (double)2.0)
      .describe("Seconds a triangle takes to fade out as it expands.")
      .visibleWhen(() -> this.spinner.getValue())
      .page("Render");
   public final Setting<Boolean> box = this.bool("Box", true).describe("Fades a box out on each block a crystal is placed on.").page("Render");
   public final Setting<Color> boxLineColor = this.color("BoxLine", 123, 0, 255, 255).visibleWhen(() -> this.box.getValue()).page("Render");
   public final Setting<Color> boxSideColor = this.color("BoxSide", 123, 0, 255, 44).visibleWhen(() -> this.box.getValue()).page("Render");
   public final Setting<Double> boxWidth = this.number("BoxWidth", (double)3.0, (double)0.1, (double)16.0)
      .visibleWhen(() -> this.box.getValue())
      .page("Render");
   public final Setting<Double> boxFade = this.number("BoxFade", (double)2.0, (double)0.05, (double)2.0)
      .describe("Seconds a box takes to fade out.")
      .visibleWhen(() -> this.box.getValue())
      .page("Render");
   public final Setting<Double> renderHeight = this.number("RenderHeight", (double)0.02, (double)-1.0, (double)1.0)
      .describe("How far above the block the marks sit; a little clears z-fighting.")
      .visibleWhen(() -> this.pane.getValue() || this.spinner.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true)
      .visibleWhen(() -> this.pane.getValue() || this.spinner.getValue() || this.box.getValue())
      .page("Render");
   private final AtomicLong nextSubmission = new AtomicLong();
   private final AtomicLong pendingSubmission = new AtomicLong();
   private final AtomicLong generation = new AtomicLong();
   private final AtomicReference<CrystalPlacerModule.RuntimeConfig> runtimeConfig = new AtomicReference<>(CrystalPlacerModule.RuntimeConfig.DISABLED);
   private final AtomicReference<CrystalPlacerModule.PlannerDebug> plannerDebug = new AtomicReference<>(CrystalPlacerModule.PlannerDebug.STARTING);
   private final PlacementAnimation animation = new PlacementAnimation();
   private final Deque<Long> confirmedPlacementTimes = new ArrayDeque<>();
   private int debugTicks;
   private final CrystalPlacerModule.IntExpirySet attackedCrystals = new CrystalPlacerModule.IntExpirySet(64);
   private final CrystalPlacerModule.LongExpirySet pendingPlaces = new CrystalPlacerModule.LongExpirySet(64);
   private final ConcurrentHashMap<Long, CrystalPlacerModule.PlaceIntents> intendedPlaces = new ConcurrentHashMap<>();
   private long snapshotEpoch;
   private long networkTick;
   private long lastPlacementClientTick = Long.MIN_VALUE;
   private double bestBaseScore;

   public CrystalPlacerModule() {
      super("CrystalPlacer", Category.COMBAT, "Places and breaks one damage-screened end crystal each network tick.");
   }

   @Override
   public void onEnable() {
      CrystalWorldSnapshot.setActive(true);
      CrystalWorldSnapshot.setPistonTargeting(this.pistons.getValue());
      CrystalWorldSnapshot.setPreferredTargetId(-1);
      this.animation.clear();
      this.resetPlacementCounter();
      long activeGeneration = this.generation.incrementAndGet();
      CrystalPlacerModule.RuntimeConfig initialConfig = this.captureConfig();
      this.runtimeConfig.set(initialConfig);
      publishSnapshotPlanning(initialConfig);
      this.plannerDebug.set(CrystalPlacerModule.PlannerDebug.STARTING);
      this.debugTicks = 0;
      if (NetworkThreadExecutor.isConnected()) {
         NetworkThreadExecutor.execute(context -> {
            if (this.generation.get() == activeGeneration) {
               this.resetNetworkState();
            }
         });
      }
   }

   @Override
   public void onDisable() {
      CrystalWorldSnapshot.setActive(false);
      CrystalWorldSnapshot.setPistonTargeting(false);
      CrystalWorldSnapshot.setPreferredTargetId(-1);
      this.animation.clear();
      this.resetPlacementCounter();
      this.runtimeConfig.set(CrystalPlacerModule.RuntimeConfig.DISABLED);
      long disabledGeneration = this.generation.incrementAndGet();
      this.pendingSubmission.set(0L);
      if (NetworkThreadExecutor.isConnected()) {
         NetworkThreadExecutor.execute(context -> {
            if (this.generation.get() == disabledGeneration) {
               this.resetNetworkState();
            }
         });
      }
   }

   @Override
   public void onTick() {
      CrystalPlacerModule.RuntimeConfig config = this.captureConfig();
      this.runtimeConfig.set(config);
      publishSnapshotPlanning(config);
      CrystalWorldSnapshot.setPistonTargeting(config.pistons());
      CrystalWorldSnapshot.setPreferredTargetId(config.preferredTargetId());
      InventorySnapshot inventory = InventoryUtil.snapshot();
      CrystalWorldSnapshot.Snapshot world = CrystalWorldSnapshot.snapshot();
      if (inventory.available() && world.available()) {
         if (inventory.playerId() != world.playerId()) {
            if (config.debug()) {
               this.plannerDebug
                  .set(new CrystalPlacerModule.PlannerDebug("snapshot player mismatch", world.baseCount(), world.entityCount(), Integer.MIN_VALUE, -1));
            }

            this.showDebug(config);
         } else if (inventory.deadOrDying()) {
            if (config.debug()) {
               this.plannerDebug
                  .set(new CrystalPlacerModule.PlannerDebug("local player is dead", world.baseCount(), world.entityCount(), Integer.MIN_VALUE, -1));
            }

            this.showDebug(config);
         } else if (config.pauseWhileUsing() && inventory.usingItem()) {
            if (config.debug()) {
               this.plannerDebug
                  .set(new CrystalPlacerModule.PlannerDebug("paused while using item", world.baseCount(), world.entityCount(), Integer.MIN_VALUE, -1));
            }

            this.showDebug(config);
         } else {
            BasePlaceModule basePlace = Homovore2Client.modules().get(BasePlaceModule.class);
            BasePlaceModule.Candidate baseCandidate = basePlace == null ? null : basePlace.candidate();
            long submission = this.nextSubmission.incrementAndGet();
            if (this.pendingSubmission.compareAndSet(0L, submission)) {
               long activeGeneration = this.generation.get();
               CompletableFuture<Void> submitted = NetworkThreadExecutor.execute(context -> {
                  if (this.generation.get() == activeGeneration) {
                     this.runNetworkTick(context, inventory, world, config, baseCandidate);
                  }
               });
               submitted.whenComplete((ignored, failure) -> this.pendingSubmission.compareAndSet(submission, 0L));
               this.showDebug(config);
            }
         }
      } else if (config.debug()) {
         this.plannerDebug.set(new CrystalPlacerModule.PlannerDebug("waiting for snapshots", -1, -1, Integer.MIN_VALUE, -1));
      }
   }

   private void showDebug(CrystalPlacerModule.RuntimeConfig config) {
      if (config.debug() && ++this.debugTicks >= 20) {
         this.debugTicks = 0;
         CrystalPlacerModule.PlannerDebug state = this.plannerDebug.get();
         String detail = state.targetId() == Integer.MIN_VALUE ? "" : " target=" + state.targetId();
         ChatUtil.sendPersistent(
            "crystalplacer:debug",
            Text.literal(
               "Crystal Placer: "
                  + state.status()
                  + " [bases="
                  + state.bases()
                  + ", entities="
                  + state.entities()
                  + detail
                  + ", crystals="
                  + state.crystals()
                  + "]"
            )
         );
      }
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (this.pane.getValue() || this.spinner.getValue() || this.box.getValue()) {
         this.animation
            .render(
               new PlacementAnimation.Style(
                  this.pane.getValue(),
                  this.paneLineColor.getValue().getRGB(),
                  this.paneSideColor.getValue().getRGB(),
                  this.paneWidth.getValue().floatValue(),
                  this.paneSize.getValue(),
                  this.paneSpeed.getValue().floatValue(),
                  this.paneLinger.getValue().floatValue(),
                  this.paneFade.getValue().floatValue(),
                  this.spinner.getValue(),
                  this.spinnerLineColor.getValue().getRGB(),
                  this.spinnerSideColor.getValue().getRGB(),
                  this.spinnerWidth.getValue().floatValue(),
                  this.spinnerSize.getValue(),
                  this.spinnerStep.getValue().floatValue(),
                  this.spinnerFade.getValue().floatValue(),
                  this.box.getValue(),
                  this.boxLineColor.getValue().getRGB(),
                  this.boxSideColor.getValue().getRGB(),
                  this.boxWidth.getValue().floatValue(),
                  this.boxFade.getValue().floatValue(),
                  this.renderHeight.getValue(),
                  this.throughWalls.getValue()
               )
            );
      }
   }

   public int crystalsPerSecond() {
      this.trimConfirmedPlacements(System.nanoTime());
      return this.confirmedPlacementTimes.size();
   }

   @Subscribe
   public void onEntityAdded(EntityAddedEvent event) {
      EntitySpawnS2CPacket packet = event.getPacket();
      if (packet.getEntityType() == EntityType.END_CRYSTAL) {
         long crystalPos = BlockPos.asLong(MathHelper.floor(packet.getX()), MathHelper.floor(packet.getY()), MathHelper.floor(packet.getZ()));
         long now = System.nanoTime();
         boolean[] matched = new boolean[]{false};
         this.intendedPlaces.computeIfPresent(crystalPos, (ignored, intents) -> {
            if (intents.expired(now)) {
               return null;
            }

            CrystalPlacerModule.PlaceIntents remaining = intents.consume(now);
            matched[0] = true;
            return remaining.isEmpty() ? null : remaining;
         });
         if (matched[0]) {
            this.trimConfirmedPlacements(now);
            this.confirmedPlacementTimes.addLast(now);
         }
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
      } else if (event.getPacket() instanceof EntitySpawnS2CPacket packet && packet.getEntityType() == EntityType.END_CRYSTAL) {
         CrystalPlacerModule.RuntimeConfig config = this.runtimeConfig.get();
         long activeGeneration = this.generation.get();
         if (config.enabled() && config.doBreak()) {
            int id = packet.getEntityId();
            double x = packet.getX();
            double y = packet.getY();
            double z = packet.getZ();
            if (!protectedByPistonCrystal(id, x, y, z)) {
               NetworkThreadExecutor.executeNow(context -> {
                  if (this.generation.get() == activeGeneration) {
                     this.breakSpawnedCrystal(context, id, x, y, z, config);
                  }
               });
            }
         }
      }
   }

   private void runNetworkTick(
      NetworkActionContext context,
      InventorySnapshot inventory,
      CrystalWorldSnapshot.Snapshot world,
      CrystalPlacerModule.RuntimeConfig config,
      BasePlaceModule.Candidate baseCandidate
   ) {
      context.requireNetworkThread();
      this.adoptSnapshotEpoch(world);
      this.networkTick++;
      this.attackedCrystals.collect(this.networkTick);
      this.pendingPlaces.collect(System.nanoTime());
      int target = this.nearestTarget(world, config.targetRangeSquared());
      if (target < 0) {
         this.debug(config, "no target in range", world, Integer.MIN_VALUE);
      } else {
         int targetId = world.entityId(target);
         int pistonMask = pistonsInRange(world, inventory, config);
         boolean breakDamageable = damageable(world, target, config, 0);
         boolean placeDamageable = damageable(world, target, config, 1);
         if (!breakDamageable && !placeDamageable && pistonMask == 0) {
            this.debug(config, "turtle target in hurt cooldown", world, targetId);
         } else {
            int crystal = -1;
            int brokenEntityId = -1;
            long preferredBase = Long.MIN_VALUE;
            if (config.doBreak()) {
               crystal = this.bestCrystal(world, target, inventory, config, pistonMask, breakDamageable);
               if (crystal >= 0) {
                  brokenEntityId = world.entityId(crystal);
                  preferredBase = crystalBase(world, crystal);
                  this.pendingPlaces.remove(preferredBase);
               }
            }

            Result crystals = null;
            long base = Long.MIN_VALUE;
            if (config.place()) {
               crystals = InventoryUtil.find(Items.END_CRYSTAL, InventoryUtil.HOTBAR_SCOPE);
               if (crystals.found()) {
                  base = this.bestBase(world, target, brokenEntityId, preferredBase, inventory, config, pistonMask, placeDamageable);
               }
            }

            BasePlaceModule.Candidate newBase = null;
            if (baseCandidate != null
               && crystals != null
               && crystals.found()
               && placeDamageable
               && baseCandidate.targetId() == targetId
               && (base == Long.MIN_VALUE || baseCandidate.damage() > this.bestBaseScore)
               && !this.pendingPlaces.contains(baseCandidate.base().asLong(), System.nanoTime())) {
               newBase = baseCandidate;
            }

            if (crystal < 0 && base == Long.MIN_VALUE && newBase == null) {
               String status;
               if (!config.place() || crystals != null && crystals.found()) {
                  if (world.baseCount() == 0) {
                     status = "no obsidian/bedrock base nearby";
                  } else if (!breakDamageable && !placeDamageable) {
                     status = "turtle target in hurt cooldown";
                  } else {
                     status = "no safe damaging action";
                  }
               } else {
                  status = "no crystals in hotbar/offhand";
               }

               this.debug(config, status, world, targetId);
            } else {
               boolean rotated = false;
               if (crystal >= 0) {
                  try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
                        .beginSilentNow(
                           context,
                           inventory,
                           anglesTo(
                              inventory,
                              (world.minX(crystal) + world.maxX(crystal)) * 0.5,
                              (world.minY(crystal) + world.maxY(crystal)) * 0.5,
                              (world.minZ(crystal) + world.maxZ(crystal)) * 0.5
                           )
                        )) {
                     rotated = rotation.applied();
                     this.attack(context, brokenEntityId);
                  }
               }

               if (newBase != null && this.placeOnNewBase(context, inventory, world, newBase, crystals, config, !rotated)) {
                  this.debug(config, crystal >= 0 ? "broke and base placed" : "base placed", world, targetId);
               } else if (base != Long.MIN_VALUE && crystals != null) {
                  CrystalPlacerModule.PlaceOutcome outcome = this.placeCrystal(context, inventory, base, crystals, config, !rotated);
                  if (outcome == CrystalPlacerModule.PlaceOutcome.LIMITED) {
                     this.debug(config, "place over packet limit", world, targetId);
                  } else if (outcome == CrystalPlacerModule.PlaceOutcome.ALREADY_PLACED) {
                     this.debug(config, "already placed this protocol tick", world, targetId);
                  } else {
                     boolean placed = outcome == CrystalPlacerModule.PlaceOutcome.PLACED;
                     this.debug(config, placed ? (crystal >= 0 ? "broke and placed" : "placed crystal") : "crystal swap rejected", world, targetId);
                  }
               } else {
                  this.debug(config, crystal >= 0 ? "broke crystal" : "base place rejected", world, targetId);
               }
            }
         }
      }
   }

   private CrystalPlacerModule.PlaceOutcome placeCrystal(
      NetworkActionContext context, InventorySnapshot inventory, long base, Result crystals, CrystalPlacerModule.RuntimeConfig config, boolean rotate
   ) {
      long clientTick = context.limits().clientTick();
      if (this.lastPlacementClientTick == clientTick) {
         return CrystalPlacerModule.PlaceOutcome.ALREADY_PLACED;
      }

      try (RotationManager.SilentRotation rotation = rotate
            ? Homovore2Client.rotations()
               .beginSilentNow(
                  context, inventory, anglesTo(inventory, BlockPos.unpackLongX(base) + 0.5, BlockPos.unpackLongY(base) + 1.0, BlockPos.unpackLongZ(base) + 0.5)
               )
            : null) {
         BlockPos basePos = BlockPos.fromLong(base);
         Vec3d hitLocation = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1.0, basePos.getZ() + 0.5);
         boolean inside = inventory.x() > basePos.getX()
            && inventory.x() < basePos.getX() + 1
            && inventory.eyeY() > basePos.getY()
            && inventory.eyeY() < basePos.getY() + 1
            && inventory.z() > basePos.getZ()
            && inventory.z() < basePos.getZ() + 1;
         BlockHitResult hit = new BlockHitResult(hitLocation, Direction.UP, basePos, inside);
         if (!context.limits().canSend(PacketLimiter.Bucket.USE_ITEM)) {
            return CrystalPlacerModule.PlaceOutcome.LIMITED;
         } else {
            boolean[] sent = new boolean[]{false};
            boolean swapped = InventoryUtil.withSwapNow(context, crystals, action -> sent[0] = action.place(crystals.hand(), hit, true));
            if (swapped && sent[0]) {
               this.lastPlacementClientTick = clientTick;
               long nowx = System.nanoTime();
               this.pendingPlaces.add(base, nowx + placeHoldNanos(context));
               this.intendedPlaces.entrySet().removeIf(entry -> entry.getValue().expired(nowx));
               long expiresAt = nowx + 5000000000L;
               this.intendedPlaces.compute(crystalPosition(base), (ignored, intents) -> CrystalPlacerModule.PlaceIntents.append(intents, nowx, expiresAt));
               this.animation.record(base);
               return CrystalPlacerModule.PlaceOutcome.PLACED;
            } else {
               return CrystalPlacerModule.PlaceOutcome.REJECTED;
            }
         }
      }
   }

   private boolean placeOnNewBase(
      NetworkActionContext context,
      InventorySnapshot inventory,
      CrystalWorldSnapshot.Snapshot world,
      BasePlaceModule.Candidate candidate,
      Result crystals,
      CrystalPlacerModule.RuntimeConfig config,
      boolean rotate
   ) {
      if (this.lastPlacementClientTick == context.limits().clientTick()) {
         return false;
      }

      if (context.limits().remaining(PacketLimiter.Bucket.USE_ITEM) < 2) {
         return false;
      }

      BlockPos newBase = candidate.base();
      if (world.selfCollides(newBase.getX(), newBase.getY() + 1.0, newBase.getZ(), newBase.getX() + 1.0, newBase.getY() + 3.0, newBase.getZ() + 1.0)) {
         return false;
      }

      PlacementManager.Options options = new PlacementManager.Options(true, false, false, true);
      if (!Homovore2Client.placements().placeNow(context, newBase, null, Items.OBSIDIAN, options)) {
         return false;
      }

      this.placeCrystal(context, inventory, newBase.asLong(), crystals, config, rotate);
      return true;
   }

   public boolean preplace(BlockPos airPos) {
      NetworkAction action = this.preparePreplaceAction(airPos);
      if (action == null) {
         return false;
      }

      NetworkThreadExecutor.execute(action);
      return true;
   }

   public NetworkAction preparePreplaceAction(BlockPos airPos) {
      if (this.isEnabled() && this.place.getValue() && mc.world != null && NetworkThreadExecutor.isConnected()) {
         BlockPos basePos = airPos.down().toImmutable();
         BlockState baseState = mc.world.getBlockState(basePos);
         if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) {
            return null;
         }

         Box space = new Box(airPos).stretch(0.0, 1.0, 0.0);
         if (!mc.world.getOtherEntities((Entity)null, space, entity -> !entity.isSpectator()).isEmpty()) {
            return null;
         }

         CrystalPlacerModule.RuntimeConfig config = this.runtimeConfig.get();
         InventorySnapshot inventory = InventoryUtil.snapshot();
         CrystalWorldSnapshot.Snapshot world = CrystalWorldSnapshot.snapshot();
         if (config.enabled()
            && inventory.available()
            && world.available()
            && inventory.playerId() == world.playerId()
            && !inventory.deadOrDying()
            && (!config.pauseWhileUsing() || !inventory.usingItem())) {
            int bx = basePos.getX();
            int by = basePos.getY();
            int bz = basePos.getZ();
            if (distanceSquaredToBox(inventory.x(), inventory.eyeY(), inventory.z(), bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0) > 20.25) {
               return null;
            }

            if (!safeForSelf(world, bx + 0.5, by + 1.0, bz + 0.5, config.maxSelfDamage(), 1.0)) {
               return null;
            }

            long base = basePos.asLong();
            long activeGeneration = this.generation.get();
            return context -> {
               if (this.generation.get() == activeGeneration) {
                  this.adoptSnapshotEpoch(world);
                  if (!this.pendingPlaces.contains(base, System.nanoTime())) {
                     Result crystals = InventoryUtil.find(Items.END_CRYSTAL, InventoryUtil.HOTBAR_SCOPE);
                     if (crystals.found()) {
                        this.placeCrystal(context, inventory, base, crystals, config, true);
                     }
                  }
               }
            };
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static long placeHoldNanos(NetworkActionContext context) {
      return Math.max(150000000L, context.limits().acknowledgementTimeoutNanos());
   }

   private static int countCrystals(CrystalWorldSnapshot.Snapshot world) {
      int count = 0;

      for (int i = 0; i < world.entityCount(); i++) {
         if (world.hasFlag(i, (byte)2)) {
            count++;
         }
      }

      return count;
   }

   private void debug(CrystalPlacerModule.RuntimeConfig config, String status, CrystalWorldSnapshot.Snapshot world, int targetId) {
      if (config.debug()) {
         this.plannerDebug.set(new CrystalPlacerModule.PlannerDebug(status, world.baseCount(), world.entityCount(), targetId, countCrystals(world)));
      }
   }

   private void breakSpawnedCrystal(NetworkActionContext context, int id, double x, double y, double z, CrystalPlacerModule.RuntimeConfig config) {
      if (!protectedByPistonCrystal(id, x, y, z) && !this.attackedCrystals.contains(id, this.networkTick)) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         CrystalWorldSnapshot.Snapshot world = CrystalWorldSnapshot.snapshot();
         if (inventory.available()
            && world.available()
            && inventory.playerId() == world.playerId()
            && !inventory.deadOrDying()
            && (!config.pauseWhileUsing() || !inventory.usingItem())) {
            this.adoptSnapshotEpoch(world);
            long base = BlockPos.asLong(MathHelper.floor(x), MathHelper.floor(y) - 1, MathHelper.floor(z));
            int baseIndex = world.baseIndex(base);
            boolean ours = this.pendingPlaces.contains(base, System.nanoTime());
            double reachSquared = distanceSquaredToBox(inventory.x(), inventory.eyeY(), inventory.z(), x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0);
            if (!(reachSquared > 9.0)) {
               int target = this.nearestTarget(world, config.targetRangeSquared());
               if (target >= 0) {
                  double targetDistance = squaredDistanceToTarget(world, target, x, y, z);
                  double targetDamage = targetDamage(world, target, targetDistance, baseIndex < 0 ? 1.0 : world.baseTargetExposure(baseIndex, target));
                  boolean breaksPiston = baseIndex >= 0 && (world.basePistonMask(baseIndex) & pistonsInRange(world, inventory, config)) != 0;
                  if ((ours || damageable(world, target, config, 0) && !(targetDamage < config.minDamage()) || breaksPiston)
                     && safeForSelf(world, x, y, z, config.maxSelfDamage(), baseIndex < 0 ? 1.0 : world.baseSelfExposure(baseIndex))) {
                     try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
                           .beginSilentNow(context, inventory, anglesTo(inventory, x, y + 1.0, z))) {
                        this.pendingPlaces.remove(base);
                        if (this.attack(context, id) && ours && config.place()) {
                           if (!world.selfCollides(
                              BlockPos.unpackLongX(base),
                              BlockPos.unpackLongY(base) + 1.0,
                              BlockPos.unpackLongZ(base),
                              BlockPos.unpackLongX(base) + 1.0,
                              BlockPos.unpackLongY(base) + 3.0,
                              BlockPos.unpackLongZ(base) + 1.0
                           )) {
                              Result crystals = InventoryUtil.find(Items.END_CRYSTAL, InventoryUtil.HOTBAR_SCOPE);
                              if (crystals.found()) {
                                 this.placeCrystal(context, inventory, base, crystals, config, false);
                              }

                              return;
                           }

                           return;
                        }
                     }

                     return;
                  }
               }
            }
         }
      }
   }

   private boolean attack(NetworkActionContext context, int entityId) {
      if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
         return false;
      }

      if (!context.attack(entityId)) {
         return false;
      }

      context.swing(Hand.MAIN_HAND);
      this.attackedCrystals.add(entityId, this.networkTick + 10L);
      return true;
   }

   private static PistonCrystalModule pistonCrystal() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(PistonCrystalModule.class);
   }

   private static boolean protectedByPistonCrystal(int entityId, double x, double y, double z) {
      PistonCrystalModule pistonCrystal = pistonCrystal();
      return pistonCrystal != null && pistonCrystal.isProtectedCrystal(entityId, x, y, z);
   }

   private static boolean ownPistonBlock(int x, int y, int z) {
      PistonCrystalModule pistonCrystal = pistonCrystal();
      return pistonCrystal != null && pistonCrystal.isOwnPistonBlock(x, y, z);
   }

   private int nearestTarget(CrystalWorldSnapshot.Snapshot world, double targetRangeSquared) {
      int best = -1;
      double bestDistance = targetRangeSquared;

      for (int i = 0; i < world.entityCount(); i++) {
         if (world.hasFlag(i, (byte)1)) {
            double distance = distanceSquaredToBox(
               world.playerX(), world.playerY(), world.playerZ(), world.minX(i), world.minY(i), world.minZ(i), world.maxX(i), world.maxY(i), world.maxZ(i)
            );
            if (!(distance > targetRangeSquared)) {
               if (world.entityId(i) == world.exposureTargetId()) {
                  return i;
               }

               if (distance <= bestDistance) {
                  bestDistance = distance;
                  best = i;
               }
            }
         }
      }

      return best;
   }

   private static int pistonsInRange(CrystalWorldSnapshot.Snapshot world, InventorySnapshot inventory, CrystalPlacerModule.RuntimeConfig config) {
      if (!config.pistons()) {
         return 0;
      }

      int mask = 0;

      for (int i = 0; i < world.pistonCount(); i++) {
         long packed = world.piston(i);
         int px = BlockPos.unpackLongX(packed);
         int py = BlockPos.unpackLongY(packed);
         int pz = BlockPos.unpackLongZ(packed);
         if (!ownPistonBlock(px, py, pz)
            && distanceSquaredToBox(inventory.x(), inventory.eyeY(), inventory.z(), px, py, pz, px + 1.0, py + 1.0, pz + 1.0) <= config.pistonRangeSquared()) {
            mask |= 1 << i;
         }
      }

      return mask;
   }

   private int bestCrystal(
      CrystalWorldSnapshot.Snapshot world,
      int target,
      InventorySnapshot inventory,
      CrystalPlacerModule.RuntimeConfig config,
      int pistonMask,
      boolean damageable
   ) {
      int best = -1;
      double bestScore = Double.NEGATIVE_INFINITY;

      for (int i = 0; i < world.entityCount(); i++) {
         if (world.hasFlag(i, (byte)2) && !this.attackedCrystals.contains(world.entityId(i), this.networkTick)) {
            double x = (world.minX(i) + world.maxX(i)) * 0.5;
            double y = world.minY(i);
            double z = (world.minZ(i) + world.maxZ(i)) * 0.5;
            if (!protectedByPistonCrystal(world.entityId(i), x, y, z)) {
               double reachSquared = distanceSquaredToBox(
                  inventory.x(), inventory.eyeY(), inventory.z(), world.minX(i), world.minY(i), world.minZ(i), world.maxX(i), world.maxY(i), world.maxZ(i)
               );
               if (!(reachSquared > 9.0) && safeForSelf(world, x, y, z, config.maxSelfDamage(), world.entitySelfExposure(i))) {
                  double targetDamage = targetDamage(world, target, squaredDistanceToTarget(world, target, x, y, z), world.entityTargetExposure(i, target));
                  boolean breaksPiston = (world.entityPistonMask(i) & pistonMask) != 0;
                  if (damageable && !(targetDamage < config.minDamage()) || breaksPiston) {
                     double score = targetDamage + (breaksPiston ? 100.0 : 0.0);
                     if (score >= bestScore) {
                        bestScore = score;
                        best = i;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private long bestBase(
      CrystalWorldSnapshot.Snapshot world,
      int target,
      int ignoredEntityId,
      long preferredBase,
      InventorySnapshot inventory,
      CrystalPlacerModule.RuntimeConfig config,
      int pistonMask,
      boolean damageable
   ) {
      long best = Long.MIN_VALUE;
      double bestScore = Double.NEGATIVE_INFINITY;
      long now = System.nanoTime();

      for (int i = 0; i < world.baseCount(); i++) {
         long packed = world.base(i);
         if (!this.pendingPlaces.contains(packed, now)) {
            int bx = BlockPos.unpackLongX(packed);
            int by = BlockPos.unpackLongY(packed);
            int bz = BlockPos.unpackLongZ(packed);
            double x = bx + 0.5;
            double y = by + 1.0;
            double z = bz + 0.5;
            if (!(distanceSquaredToBox(inventory.x(), inventory.eyeY(), inventory.z(), bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0) > 20.25)
               && !(distanceSquaredToBox(inventory.x(), inventory.eyeY(), inventory.z(), x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0) > 9.0)
               && !this.blocked(world, bx, by, bz, ignoredEntityId)
               && safeForSelf(world, x, y, z, config.maxSelfDamage(), world.baseSelfExposure(i))) {
               double targetDamage = targetDamage(world, target, squaredDistanceToTarget(world, target, x, y, z), world.baseTargetExposure(i, target));
               boolean breaksPiston = (world.basePistonMask(i) & pistonMask) != 0;
               if (damageable && !(targetDamage < config.minDamage()) || breaksPiston) {
                  double priorityDamage = targetDamage + (breaksPiston ? 100.0 : 0.0);
                  if (packed == preferredBase) {
                     this.bestBaseScore = priorityDamage;
                     return packed;
                  }

                  double selfDistance = squaredDistance(world.playerX(), world.playerY(), world.playerZ(), x, y, z);
                  double score = priorityDamage + Math.min(selfDistance, 144.0) * 0.001;
                  if (score > bestScore) {
                     bestScore = score;
                     this.bestBaseScore = priorityDamage;
                     best = packed;
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean blocked(CrystalWorldSnapshot.Snapshot world, int bx, int by, int bz, int ignoredEntityId) {
      double minX = bx;
      double minY = by + 1.0;
      double minZ = bz;
      double maxX = bx + 1.0;
      double maxY = by + 3.0;
      double maxZ = bz + 1.0;
      if (world.selfCollides(minX, minY, minZ, maxX, maxY, maxZ)) {
         return true;
      }

      for (int i = 0; i < world.entityCount(); i++) {
         if (world.hasFlag(i, (byte)4)) {
            int id = world.entityId(i);
            if (id != ignoredEntityId
               && (!world.hasFlag(i, (byte)2) || !this.attackedCrystals.contains(id, this.networkTick))
               && world.maxX(i) > minX
               && world.minX(i) < maxX
               && world.maxY(i) > minY
               && world.minY(i) < maxY
               && world.maxZ(i) > minZ
               && world.minZ(i) < maxZ) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean safeForSelf(CrystalWorldSnapshot.Snapshot world, double x, double y, double z, double maxSelfDamage, double exposure) {
      double selfDamage = ExplosionMath.damage(
         squaredDistance(world.playerX(), world.playerY(), world.playerZ(), x, y, z),
         exposure,
         world.playerArmor(),
         world.playerToughness(),
         world.playerResistance(),
         world.playerProtection(),
         world.difficulty()
      );
      return ExplosionMath.safeForSelf(selfDamage, maxSelfDamage, world.playerHealth(), world.playerInvulnerable());
   }

   private static boolean damageable(CrystalWorldSnapshot.Snapshot world, int target, CrystalPlacerModule.RuntimeConfig config, int leadTicks) {
      return config.damageSync() && world.hasFlag(target, (byte)8) ? world.targetHurtTime(target) - world.latencyTicks() - leadTicks <= 0 : true;
   }

   private static double targetDamage(CrystalWorldSnapshot.Snapshot world, int target, double distanceSquared, double exposure) {
      return ExplosionMath.damage(
         distanceSquared,
         exposure,
         world.targetArmor(target),
         world.targetToughness(target),
         world.targetResistance(target),
         world.targetProtection(target),
         world.difficulty()
      );
   }

   private static double squaredDistanceToTarget(CrystalWorldSnapshot.Snapshot world, int target, double x, double y, double z) {
      double targetX = (world.minX(target) + world.maxX(target)) * 0.5;
      double targetY = world.minY(target);
      double targetZ = (world.minZ(target) + world.maxZ(target)) * 0.5;
      return squaredDistance(x, y, z, targetX, targetY, targetZ);
   }

   private static long crystalBase(CrystalWorldSnapshot.Snapshot world, int crystal) {
      return BlockPos.asLong(
         MathHelper.floor((world.minX(crystal) + world.maxX(crystal)) * 0.5),
         MathHelper.floor(world.minY(crystal)) - 1,
         MathHelper.floor((world.minZ(crystal) + world.maxZ(crystal)) * 0.5)
      );
   }

   private static RotationManager.Angles anglesTo(InventorySnapshot inventory, double x, double y, double z) {
      double dx = x - inventory.x();
      double dy = y - inventory.eyeY();
      double dz = z - inventory.z();
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      return new RotationManager.Angles((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float)(-Math.toDegrees(Math.atan2(dy, horizontal))));
   }

   private static double distanceSquaredToBox(double x, double y, double z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = x < minX ? minX - x : (x > maxX ? x - maxX : 0.0);
      double dy = y < minY ? minY - y : (y > maxY ? y - maxY : 0.0);
      double dz = z < minZ ? minZ - z : (z > maxZ ? z - maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private static double squaredDistance(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = ax - bx;
      double dy = ay - by;
      double dz = az - bz;
      return dx * dx + dy * dy + dz * dz;
   }

   private CrystalPlacerModule.RuntimeConfig captureConfig() {
      double targetRangeValue = this.targetRange.getValue();
      double pistonRangeValue = this.pistonRange.getValue();
      int preferredTargetId = -1;
      AutoMineModule autoMine = Homovore2Client.modules().get(AutoMineModule.class);
      if (this.autoMineTarget.getValue() && autoMine != null && autoMine.isEnabled()) {
         preferredTargetId = autoMine.targetId();
      }

      return new CrystalPlacerModule.RuntimeConfig(
         true,
         this.place.getValue(),
         this.doBreak.getValue(),
         this.pauseWhileUsing.getValue(),
         this.debug.getValue(),
         this.pistons.getValue(),
         this.damageSync.getValue(),
         this.minDamage.getValue(),
         this.maxSelfDamage.getValue(),
         targetRangeValue * targetRangeValue,
         pistonRangeValue * pistonRangeValue,
         preferredTargetId
      );
   }

   private static void publishSnapshotPlanning(CrystalPlacerModule.RuntimeConfig config) {
      CrystalWorldSnapshot.setPlanningLimits(config.minDamage(), config.maxSelfDamage(), 4.5, 3.0);
   }

   private void resetNetworkState() {
      this.snapshotEpoch = 0L;
      this.networkTick = 0L;
      this.lastPlacementClientTick = Long.MIN_VALUE;
      this.attackedCrystals.clear();
      this.pendingPlaces.clear();
      this.intendedPlaces.clear();
   }

   private void resetPlacementCounter() {
      this.intendedPlaces.clear();
      this.confirmedPlacementTimes.clear();
   }

   private void trimConfirmedPlacements(long now) {
      while (!this.confirmedPlacementTimes.isEmpty() && now - this.confirmedPlacementTimes.peekFirst() > 1000000000L) {
         this.confirmedPlacementTimes.removeFirst();
      }
   }

   private static long crystalPosition(long base) {
      return BlockPos.asLong(BlockPos.unpackLongX(base), BlockPos.unpackLongY(base) + 1, BlockPos.unpackLongZ(base));
   }

   private void adoptSnapshotEpoch(CrystalWorldSnapshot.Snapshot world) {
      if (this.snapshotEpoch != world.epoch()) {
         this.resetNetworkState();
         this.snapshotEpoch = world.epoch();
      }
   }

   private static final class IntExpirySet {
      private final int[] keys;
      private final long[] expires;
      private int size;

      private IntExpirySet(int capacity) {
         this.keys = new int[capacity];
         this.expires = new long[capacity];
      }

      private boolean contains(int key, long now) {
         for (int i = 0; i < this.size; i++) {
            if (this.keys[i] == key && this.expires[i] > now) {
               return true;
            }
         }

         return false;
      }

      private void add(int key, long expiresAt) {
         for (int i = 0; i < this.size; i++) {
            if (this.keys[i] == key) {
               this.expires[i] = expiresAt;
               return;
            }
         }

         if (this.size < this.keys.length) {
            this.keys[this.size] = key;
            this.expires[this.size++] = expiresAt;
         } else {
            int oldest = 0;

            for (int i = 1; i < this.size; i++) {
               if (this.expires[i] < this.expires[oldest]) {
                  oldest = i;
               }
            }

            this.keys[oldest] = key;
            this.expires[oldest] = expiresAt;
         }
      }

      private void remove(int key) {
         for (int i = 0; i < this.size; i++) {
            if (this.keys[i] == key) {
               int last = --this.size;
               this.keys[i] = this.keys[last];
               this.expires[i] = this.expires[last];
               return;
            }
         }
      }

      private void collect(long now) {
         int i = 0;

         while (i < this.size) {
            if (this.expires[i] > now) {
               i++;
            } else {
               int last = --this.size;
               this.keys[i] = this.keys[last];
               this.expires[i] = this.expires[last];
            }
         }
      }

      private void clear() {
         this.size = 0;
      }
   }

   private static final class LongExpirySet {
      private final long[] keys;
      private final long[] expires;
      private int size;

      private LongExpirySet(int capacity) {
         this.keys = new long[capacity];
         this.expires = new long[capacity];
      }

      private boolean contains(long key, long now) {
         for (int i = 0; i < this.size; i++) {
            if (this.keys[i] == key && this.expires[i] > now) {
               return true;
            }
         }

         return false;
      }

      private void add(long key, long expiresAt) {
         for (int i = 0; i < this.size; i++) {
            if (this.keys[i] == key) {
               this.expires[i] = expiresAt;
               return;
            }
         }

         if (this.size < this.keys.length) {
            this.keys[this.size] = key;
            this.expires[this.size++] = expiresAt;
         } else {
            int oldest = 0;

            for (int i = 1; i < this.size; i++) {
               if (this.expires[i] < this.expires[oldest]) {
                  oldest = i;
               }
            }

            this.keys[oldest] = key;
            this.expires[oldest] = expiresAt;
         }
      }

      private boolean remove(long key) {
         for (int i = 0; i < this.size; i++) {
            if (this.keys[i] == key) {
               int last = --this.size;
               this.keys[i] = this.keys[last];
               this.expires[i] = this.expires[last];
               return true;
            }
         }

         return false;
      }

      private void collect(long now) {
         int i = 0;

         while (i < this.size) {
            if (this.expires[i] > now) {
               i++;
            } else {
               int last = --this.size;
               this.keys[i] = this.keys[last];
               this.expires[i] = this.expires[last];
            }
         }
      }

      private void clear() {
         this.size = 0;
      }
   }

   private record PlaceIntents(long[] expirations) {
      private static final CrystalPlacerModule.PlaceIntents EMPTY = new CrystalPlacerModule.PlaceIntents(new long[0]);

      private static CrystalPlacerModule.PlaceIntents append(CrystalPlacerModule.PlaceIntents current, long now, long expiresAt) {
         if (current == null) {
            return new CrystalPlacerModule.PlaceIntents(new long[]{expiresAt});
         }

         int first = current.firstLive(now);
         int live = current.expirations.length - first;
         long[] next = new long[live + 1];
         System.arraycopy(current.expirations, first, next, 0, live);
         next[live] = expiresAt;
         return new CrystalPlacerModule.PlaceIntents(next);
      }

      private CrystalPlacerModule.PlaceIntents consume(long now) {
         int first = this.firstLive(now);
         if (first == this.expirations.length) {
            return EMPTY;
         }

         int remaining = this.expirations.length - first - 1;
         if (remaining == 0) {
            return EMPTY;
         }

         long[] next = new long[remaining];
         System.arraycopy(this.expirations, first + 1, next, 0, remaining);
         return new CrystalPlacerModule.PlaceIntents(next);
      }

      private int firstLive(long now) {
         int first = 0;

         while (first < this.expirations.length && this.expirations[first] < now) {
            first++;
         }

         return first;
      }

      private boolean expired(long now) {
         return this.expirations.length == 0 || this.expirations[this.expirations.length - 1] < now;
      }

      private boolean isEmpty() {
         return this.expirations.length == 0;
      }
   }

   private enum PlaceOutcome {
      PLACED,
      LIMITED,
      ALREADY_PLACED,
      REJECTED;
   }

   private record PlannerDebug(String status, int bases, int entities, int targetId, int crystals) {
      private static final CrystalPlacerModule.PlannerDebug STARTING = new CrystalPlacerModule.PlannerDebug("starting", 0, 0, Integer.MIN_VALUE, 0);
   }

   private record RuntimeConfig(
      boolean enabled,
      boolean place,
      boolean doBreak,
      boolean pauseWhileUsing,
      boolean debug,
      boolean pistons,
      boolean damageSync,
      double minDamage,
      double maxSelfDamage,
      double targetRangeSquared,
      double pistonRangeSquared,
      int preferredTargetId
   ) {
      private static final CrystalPlacerModule.RuntimeConfig DISABLED = new CrystalPlacerModule.RuntimeConfig(
         false, false, false, false, false, false, false, 0.0, 0.0, 0.0, 0.0, -1
      );
   }
}
