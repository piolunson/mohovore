package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.network.PacketEvent;
import dev.leonetic.v2.event.system.Subscribe;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.PlacementManager;
import dev.leonetic.v2.manager.RotationManager;
import dev.leonetic.v2.render.BlockRender;
import dev.leonetic.v2.util.inventory.InventorySnapshot;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import dev.leonetic.v2.util.inventory.Result;
import dev.leonetic.v2.util.inventory.SwapManager;
import dev.leonetic.v2.util.network.NetworkAction;
import dev.leonetic.v2.util.network.PacketLimiter;
import dev.leonetic.v2.util.world.AnchorDamageUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.World;

public final class AnchorPlacerModule extends Module {
   private static final double PLACE_RANGE = 6.0;
   private static final double TARGET_RANGE = 16.0;
   private static final double NO_POP_MARGIN = 1.5;
   private static final long CONFIRMATION_TIMEOUT_NANOS = 1000000000L;
   public final Setting<Double> minDamage = this.number("MinDamage", (double)10.0, (double)0.0, (double)36.0).page("Damage");
   public final Setting<Double> maxSelfDamage = this.number("MaxSelfDamage", (double)3.0, (double)0.0, (double)36.0).page("Damage");
   public final Setting<Integer> delay = this.integer("Delay", 2, 0, 20).describe("Client ticks between confirmed bursts.");
   public final Setting<Boolean> pauseWhileUsing = this.bool("PauseWhileUsing", true).describe("Does not interrupt an item being used in either hand.");
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.5, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> fillColor = this.color("FillColor", 123, 0, 255, 44).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> outlineColor = this.color("OutlineColor", 123, 0, 255, 255).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)3.0, (double)0.1, (double)8.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(() -> this.render.getValue()).page("Render");
   private final AtomicBoolean submissionPending = new AtomicBoolean();
   private final AtomicLong generation = new AtomicLong();
   private final AtomicLong nextBurstTick = new AtomicLong();
   private final AtomicReference<AnchorPlacerModule.PendingBurst> awaitingConfirmation = new AtomicReference<>();
   private final AtomicReference<AnchorPlacerModule.RenderMark> renderMark = new AtomicReference<>();
   private long clientTick;

   public AnchorPlacerModule() {
      super("AnchorPlacer", Category.COMBAT, "Places, charges and detonates the safest damaging respawn anchor in one burst.");
   }

   @Override
   public void onEnable() {
      this.generation.incrementAndGet();
      this.submissionPending.set(false);
      this.awaitingConfirmation.set(null);
      this.renderMark.set(null);
      this.nextBurstTick.set(0L);
      this.clientTick = 0L;
   }

   @Override
   public void onDisable() {
      this.generation.incrementAndGet();
      this.submissionPending.set(false);
      this.awaitingConfirmation.set(null);
      this.renderMark.set(null);
      Homovore2Client.rotations().releaseSilent(this);
   }

   @Override
   public void onTick() {
      this.clientTick++;
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null && !player.isDead() && !level.getRegistryKey().equals(World.NETHER) && mc.currentScreen == null) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available()
            && inventory.playerId() == player.getId()
            && inventory.menuId() == 0
            && !inventory.deadOrDying()
            && (!this.pauseWhileUsing.getValue() || !inventory.usingItem())
            && this.clientTick >= this.nextBurstTick.get()) {
            AnchorPlacerModule.PendingBurst pending = this.awaitingConfirmation.get();
            if (pending != null) {
               if (System.nanoTime() - pending.startedAt() < 1000000000L) {
                  return;
               }

               this.awaitingConfirmation.compareAndSet(pending, null);
            }

            if (!this.submissionPending.get() && !Homovore2Client.rotations().silentReservedByOther(this)) {
               Result anchor = InventoryUtil.find(Items.RESPAWN_ANCHOR, InventoryUtil.MAIN_INVENTORY_SCOPE);
               Result glowstone = InventoryUtil.find(Items.GLOWSTONE, InventoryUtil.MAIN_INVENTORY_SCOPE);
               if (anchor.found() && glowstone.found()) {
                  AnchorPlacerModule.Candidate candidate = this.findBest(level, player);
                  if (candidate != null && this.submissionPending.compareAndSet(false, true)) {
                     AnchorPlacerModule.BurstConfig config = new AnchorPlacerModule.BurstConfig(this.delay.getValue(), this.render.getValue());
                     this.submitBurst(inventory, candidate, anchor, glowstone, config);
                  }
               }
            }
         }
      }
   }

   private void submitBurst(
      InventorySnapshot inventory, AnchorPlacerModule.Candidate candidate, Result anchor, Result glowstone, AnchorPlacerModule.BurstConfig config
   ) {
      long activeGeneration = this.generation.get();
      long plannedTick = this.clientTick;
      AnchorPlacerModule.PendingBurst pending = new AnchorPlacerModule.PendingBurst(candidate.pos(), System.nanoTime());
      this.awaitingConfirmation.set(pending);
      Homovore2Client.rotations().reserveSilent(this);
      AtomicInteger phase = new AtomicInteger();
      int usePackets = candidate.needPlace() ? 3 : 2;
      BlockHitResult anchorHit = anchorHit(candidate.pos(), inventory.eyePosition());
      RotationManager.Angles angles = anglesTo(inventory, anchorHit.getPos());
      List<SwapManager.Step> steps = new ArrayList<>(usePackets);
      if (candidate.needPlace()) {
         steps.add(new SwapManager.Step(anchor, this.firstAction(inventory, angles, activeGeneration, usePackets, phase, config, context -> {
            if (context.placeBurst(Hand.MAIN_HAND, candidate.placeHit(), false, true)) {
               phase.compareAndSet(0, 1);
            }
         })));
      }

      int chargePhase = candidate.needPlace() ? 1 : 0;
      NetworkAction charge = context -> {
         if (this.generation.get() == activeGeneration && phase.get() == chargePhase && context.useItemOn(Hand.MAIN_HAND, anchorHit)) {
            phase.compareAndSet(chargePhase, chargePhase + 1);
         }
      };
      steps.add(
         new SwapManager.Step(
            glowstone, candidate.needPlace() ? charge : this.firstAction(inventory, angles, activeGeneration, usePackets, phase, config, charge)
         )
      );
      int detonatePhase = chargePhase + 1;
      steps.add(new SwapManager.Step(anchor, context -> {
         if (this.generation.get() == activeGeneration && phase.get() == detonatePhase && context.useItemOn(Hand.MAIN_HAND, anchorHit)) {
            phase.compareAndSet(detonatePhase, detonatePhase + 1);
            context.swing(Hand.MAIN_HAND);
         }
      }));

      CompletableFuture<Boolean> submitted;
      try {
         submitted = InventoryUtil.withSwapSequence(steps);
      } catch (RuntimeException exception) {
         this.finishSubmission(activeGeneration, plannedTick, candidate, pending, phase, usePackets, config, false);
         return;
      }

      submitted.whenComplete(
         (swapped, failure) -> this.finishSubmission(
            activeGeneration, plannedTick, candidate, pending, phase, usePackets, config, failure == null && Boolean.TRUE.equals(swapped)
         )
      );
   }

   private NetworkAction firstAction(
      InventorySnapshot inventory,
      RotationManager.Angles angles,
      long activeGeneration,
      int usePackets,
      AtomicInteger phase,
      AnchorPlacerModule.BurstConfig config,
      NetworkAction action
   ) {
      return context -> {
         if (this.generation.get() == activeGeneration && phase.get() == 0) {
            int totalPackets = usePackets + 2;
            if (context.limits().canSend(PacketLimiter.Bucket.USE_ITEM, usePackets) && context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= totalPackets) {
               try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
                     .beginSilentNow(context, inventory, angles, inventory.horizontalCollision(), this)) {
                  if (rotation.applied()) {
                     action.run(context);
                     return;
                  }

                  return;
               }
            }
         }
      };
   }

   private void finishSubmission(
      long activeGeneration,
      long plannedTick,
      AnchorPlacerModule.Candidate candidate,
      AnchorPlacerModule.PendingBurst pending,
      AtomicInteger phase,
      int usePackets,
      AnchorPlacerModule.BurstConfig config,
      boolean transactionCompleted
   ) {
      Homovore2Client.rotations().releaseSilent(this);
      this.submissionPending.set(false);
      if (this.generation.get() == activeGeneration) {
         if (transactionCompleted && phase.get() == usePackets) {
            this.nextBurstTick.set(plannedTick + config.delay());
            if (config.render()) {
               this.renderMark.set(new AnchorPlacerModule.RenderMark(candidate.pos(), System.currentTimeMillis()));
            }
         } else {
            this.awaitingConfirmation.compareAndSet(pending, null);
         }
      }
   }

   private AnchorPlacerModule.Candidate findBest(ClientWorld level, ClientPlayerEntity player) {
      List<PlayerEntity> targets = collectTargets(level, player);
      if (targets.isEmpty()) {
         return null;
      }

      Vec3d eye = player.getCameraPosVec(1.0F);
      BlockPos centerPos = player.getBlockPos();
      double rangeSquared = 36.0;
      double min = this.minDamage.getValue();
      double maxSelf = this.maxSelfDamage.getValue();
      double health = player.getHealth() + player.getAbsorptionAmount();
      int radius = (int)Math.ceil(6.0);
      int radiusSquared = radius * radius;
      AnchorPlacerModule.Candidate best = null;
      Mutable cursor = new Mutable();

      for (int dx = -radius; dx <= radius; dx++) {
         int xx = dx * dx;

         for (int dy = -radius; dy <= radius; dy++) {
            int xxyy = xx + dy * dy;

            for (int dz = -radius; dz <= radius; dz++) {
               if (xxyy + dz * dz <= radiusSquared) {
                  cursor.set(centerPos.getX() + dx, centerPos.getY() + dy, centerPos.getZ() + dz);
                  BlockPos pos = cursor.toImmutable();
                  Vec3d explosion = Vec3d.ofCenter(pos);
                  if (!(eye.squaredDistanceTo(explosion) > rangeSquared)) {
                     BlockState state = level.getBlockState(pos);
                     boolean existing = state.isOf(Blocks.RESPAWN_ANCHOR);
                     BlockHitResult placeHit = null;
                     if (!existing) {
                        if (!state.isReplaceable()) {
                           continue;
                        }

                        placeHit = Homovore2Client.placements().prepareHit(pos, null, Items.RESPAWN_ANCHOR, PlacementManager.Options.EXTENDED_REACH);
                        if (placeHit == null) {
                           continue;
                        }
                     }

                     boolean canDamage = false;

                     for (PlayerEntity target : targets) {
                        if (target.squaredDistanceTo(explosion) < 100.0 && AnchorDamageUtil.maxDamage(level, target, explosion) >= min) {
                           canDamage = true;
                           break;
                        }
                     }

                     if (canDamage) {
                        double totalDamage = 0.0;

                        for (PlayerEntity target : targets) {
                           totalDamage += AnchorDamageUtil.damage(level, target, explosion, pos);
                        }

                        if (!(totalDamage < min) && (best == null || !(totalDamage <= best.damage()))) {
                           double selfDamage = AnchorDamageUtil.damage(level, player, explosion, pos);
                           if (!(selfDamage > maxSelf) && (!(selfDamage > 0.0) || !(selfDamage + 1.5 >= health))) {
                              best = new AnchorPlacerModule.Candidate(pos, totalDamage, !existing, placeHit);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private static List<PlayerEntity> collectTargets(ClientWorld level, ClientPlayerEntity player) {
      double rangeSquared = 256.0;
      List<PlayerEntity> targets = new ArrayList<>();

      for (PlayerEntity target : level.getPlayers()) {
         if (target != player
            && !target.isDead()
            && !target.isRemoved()
            && !target.isSpectator()
            && !(player.squaredDistanceTo(target) > rangeSquared)
            && !Homovore2Client.friends().isFriend(target)) {
            targets.add(target);
         }
      }

      return targets;
   }

   private static BlockHitResult anchorHit(BlockPos pos, Vec3d eye) {
      Vec3d center = Vec3d.ofCenter(pos);
      double dx = eye.x - center.x;
      double dy = eye.y - center.y;
      double dz = eye.z - center.z;
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      Direction face = ay >= ax && ay >= az
         ? (dy > 0.0 ? Direction.UP : Direction.DOWN)
         : (ax >= az ? (dx > 0.0 ? Direction.EAST : Direction.WEST) : (dz > 0.0 ? Direction.SOUTH : Direction.NORTH));
      return new BlockHitResult(center.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5), face, pos, false);
   }

   private static RotationManager.Angles anglesTo(InventorySnapshot inventory, Vec3d point) {
      double dx = point.x - inventory.x();
      double dy = point.y - inventory.eyeY();
      double dz = point.z - inventory.z();
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      return new RotationManager.Angles((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float)(-Math.toDegrees(Math.atan2(dy, horizontal))));
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      this.inspectPacket(event.getPacket());
   }

   private void inspectPacket(Packet<?> packet) {
      if (packet instanceof BlockUpdateS2CPacket update) {
         this.observeBlock(update.getPos(), update.getState());
      } else if (packet instanceof ChunkDeltaUpdateS2CPacket section) {
         section.visitUpdates(this::observeBlock);
      } else if (packet instanceof BundleS2CPacket bundle) {
         for (Packet<?> nested : bundle.getPackets()) {
            this.inspectPacket(nested);
         }
      }
   }

   private void observeBlock(BlockPos pos, BlockState state) {
      AnchorPlacerModule.PendingBurst pending = this.awaitingConfirmation.get();
      if (pending != null && pending.pos().equals(pos) && !state.isOf(Blocks.RESPAWN_ANCHOR)) {
         this.awaitingConfirmation.compareAndSet(pending, null);
      }
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (this.render.getValue()) {
         AnchorPlacerModule.RenderMark mark = this.renderMark.get();
         if (mark != null) {
            long now = System.currentTimeMillis();
            if (BlockRender.expired(mark.startedAt(), now, this.fadeTime.getValue())) {
               this.renderMark.compareAndSet(mark, null);
            } else {
               float alpha = BlockRender.alpha(mark.startedAt(), now, this.fadeTime.getValue());
               BlockRender.cuboid(
                  mark.pos(),
                  this.outlineColor.getValue(),
                  this.fillColor.getValue(),
                  this.lineWidth.getValue().floatValue(),
                  alpha,
                  this.throughWalls.getValue()
               );
            }
         }
      }
   }

   private record BurstConfig(int delay, boolean render) {
   }

   private record Candidate(BlockPos pos, double damage, boolean needPlace, BlockHitResult placeHit) {
   }

   private record PendingBurst(BlockPos pos, long startedAt) {
   }

   private record RenderMark(BlockPos pos, long startedAt) {
   }
}
