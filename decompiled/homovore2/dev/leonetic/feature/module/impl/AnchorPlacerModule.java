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
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapManager;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.world.AnchorDamageUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_4184;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_8042;
import net.minecraft.class_2338.class_2339;

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
      class_746 player = mc.field_1724;
      class_638 level = mc.field_1687;
      if (player != null && level != null && !player.method_29504() && !level.method_27983().equals(class_1937.field_25180) && mc.field_1755 == null) {
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available()
            && inventory.playerId() == player.method_5628()
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
               Result anchor = InventoryUtil.find(class_1802.field_23141, InventoryUtil.MAIN_INVENTORY_SCOPE);
               Result glowstone = InventoryUtil.find(class_1802.field_8801, InventoryUtil.MAIN_INVENTORY_SCOPE);
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
      class_3965 anchorHit = anchorHit(candidate.pos(), inventory.eyePosition());
      RotationManager.Angles angles = anglesTo(inventory, anchorHit.method_17784());
      List<SwapManager.Step> steps = new ArrayList<>(usePackets);
      if (candidate.needPlace()) {
         steps.add(new SwapManager.Step(anchor, this.firstAction(inventory, angles, activeGeneration, usePackets, phase, config, context -> {
            if (context.placeBurst(class_1268.field_5808, candidate.placeHit(), false, true)) {
               phase.compareAndSet(0, 1);
            }
         })));
      }

      int chargePhase = candidate.needPlace() ? 1 : 0;
      NetworkAction charge = context -> {
         if (this.generation.get() == activeGeneration && phase.get() == chargePhase && context.useItemOn(class_1268.field_5808, anchorHit)) {
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
         if (this.generation.get() == activeGeneration && phase.get() == detonatePhase && context.useItemOn(class_1268.field_5808, anchorHit)) {
            phase.compareAndSet(detonatePhase, detonatePhase + 1);
            context.swing(class_1268.field_5808);
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

   private AnchorPlacerModule.Candidate findBest(class_638 level, class_746 player) {
      List<class_1657> targets = collectTargets(level, player);
      if (targets.isEmpty()) {
         return null;
      }

      class_243 eye = player.method_5836(1.0F);
      class_2338 centerPos = player.method_24515();
      double rangeSquared = 36.0;
      double min = this.minDamage.getValue();
      double maxSelf = this.maxSelfDamage.getValue();
      double health = player.method_6032() + player.method_6067();
      int radius = (int)Math.ceil(6.0);
      int radiusSquared = radius * radius;
      AnchorPlacerModule.Candidate best = null;
      class_2339 cursor = new class_2339();

      for (int dx = -radius; dx <= radius; dx++) {
         int xx = dx * dx;

         for (int dy = -radius; dy <= radius; dy++) {
            int xxyy = xx + dy * dy;

            for (int dz = -radius; dz <= radius; dz++) {
               if (xxyy + dz * dz <= radiusSquared) {
                  cursor.method_10103(centerPos.method_10263() + dx, centerPos.method_10264() + dy, centerPos.method_10260() + dz);
                  class_2338 pos = cursor.method_10062();
                  class_243 explosion = class_243.method_24953(pos);
                  if (!(eye.method_1025(explosion) > rangeSquared)) {
                     class_2680 state = level.method_8320(pos);
                     boolean existing = state.method_27852(class_2246.field_23152);
                     class_3965 placeHit = null;
                     if (!existing) {
                        if (!state.method_45474()) {
                           continue;
                        }

                        placeHit = Homovore2Client.placements().prepareHit(pos, null, class_1802.field_23141, PlacementManager.Options.EXTENDED_REACH);
                        if (placeHit == null) {
                           continue;
                        }
                     }

                     boolean canDamage = false;

                     for (class_1657 target : targets) {
                        if (target.method_5707(explosion) < 100.0 && AnchorDamageUtil.maxDamage(level, target, explosion) >= min) {
                           canDamage = true;
                           break;
                        }
                     }

                     if (canDamage) {
                        double totalDamage = 0.0;

                        for (class_1657 target : targets) {
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

   private static List<class_1657> collectTargets(class_638 level, class_746 player) {
      double rangeSquared = 256.0;
      List<class_1657> targets = new ArrayList<>();

      for (class_1657 target : level.method_18456()) {
         if (target != player
            && !target.method_29504()
            && !target.method_31481()
            && !target.method_7325()
            && !(player.method_5858(target) > rangeSquared)
            && !Homovore2Client.friends().isFriend(target)) {
            targets.add(target);
         }
      }

      return targets;
   }

   private static class_3965 anchorHit(class_2338 pos, class_243 eye) {
      class_243 center = class_243.method_24953(pos);
      double dx = eye.field_1352 - center.field_1352;
      double dy = eye.field_1351 - center.field_1351;
      double dz = eye.field_1350 - center.field_1350;
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      class_2350 face = ay >= ax && ay >= az
         ? (dy > 0.0 ? class_2350.field_11036 : class_2350.field_11033)
         : (ax >= az ? (dx > 0.0 ? class_2350.field_11034 : class_2350.field_11039) : (dz > 0.0 ? class_2350.field_11035 : class_2350.field_11043));
      return new class_3965(center.method_1031(face.method_10148() * 0.5, face.method_10164() * 0.5, face.method_10165() * 0.5), face, pos, false);
   }

   private static RotationManager.Angles anglesTo(InventorySnapshot inventory, class_243 point) {
      double dx = point.field_1352 - inventory.x();
      double dy = point.field_1351 - inventory.eyeY();
      double dz = point.field_1350 - inventory.z();
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      return new RotationManager.Angles((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float)(-Math.toDegrees(Math.atan2(dy, horizontal))));
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      this.inspectPacket(event.getPacket());
   }

   private void inspectPacket(class_2596<?> packet) {
      if (packet instanceof class_2626 update) {
         this.observeBlock(update.method_11309(), update.method_11308());
      } else if (packet instanceof class_2637 section) {
         section.method_30621(this::observeBlock);
      } else if (packet instanceof class_8042 bundle) {
         for (class_2596<?> nested : bundle.method_48324()) {
            this.inspectPacket(nested);
         }
      }
   }

   private void observeBlock(class_2338 pos, class_2680 state) {
      AnchorPlacerModule.PendingBurst pending = this.awaitingConfirmation.get();
      if (pending != null && pending.pos().equals(pos) && !state.method_27852(class_2246.field_23152)) {
         this.awaitingConfirmation.compareAndSet(pending, null);
      }
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
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

   private record Candidate(class_2338 pos, double damage, boolean needPlace, class_3965 placeHit) {
   }

   private record PendingBurst(class_2338 pos, long startedAt) {
   }

   private record RenderMark(class_2338 pos, long startedAt) {
   }
}
