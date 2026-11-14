package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.RotationUtil;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.PacketLimiter;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1296;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1308;
import net.minecraft.class_1309;
import net.minecraft.class_1548;
import net.minecraft.class_1657;
import net.minecraft.class_1674;
import net.minecraft.class_1678;
import net.minecraft.class_1799;
import net.minecraft.class_1835;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2848;
import net.minecraft.class_3489;
import net.minecraft.class_4184;
import net.minecraft.class_5134;
import net.minecraft.class_6880;
import net.minecraft.class_9285;
import net.minecraft.class_9334;
import net.minecraft.class_9362;
import net.minecraft.class_2828.class_2830;
import net.minecraft.class_2848.class_2849;

public final class KillAuraModule extends Module {
   private static final int ROTATION_PRIORITY = 6;
   private static final int NO_TARGET = -1;
   private static final double RANGE = 3.0;
   private static final double[] CRIT_OFFSETS = new double[]{0.0625, 0.062501356F, 1.3579E-6};
   private static final long CRIT_INTERVAL_MS = 250L;
   private static final double PHASE_EPSILON = 1.0E-4;
   public final Setting<Double> delay = this.number("Delay", (double)0.92, (double)0.0, (double)1.0)
      .describe("Multiplier applied to each weapon's normal cooldown.");
   public final Setting<KillAuraModule.Sprint> stopSprinting = this.mode("Sprinting", KillAuraModule.Sprint.NONE);
   public final Setting<KillAuraModule.Rotate> rotate = this.mode("Rotate", KillAuraModule.Rotate.SILENT);
   public final Setting<Boolean> criticals = this.bool("Criticals", false)
      .describe("While phased and on the ground, spoofs a tiny hop with movement packets before each hit on a living target, so it lands as a critical.");
   public final Setting<Boolean> players = this.bool("Players", true).page("Targets");
   public final Setting<Boolean> hostiles = this.bool("Hostiles", true).page("Targets");
   public final Setting<Boolean> neutrals = this.bool("Neutrals", false).page("Targets");
   public final Setting<Boolean> passives = this.bool("Passives", false).page("Targets");
   public final Setting<Boolean> projectiles = this.bool("Projectiles", true).page("Targets");
   public final Setting<KillAuraModule.Priority> priority = this.mode("Priority", KillAuraModule.Priority.SMART).page("Targets");
   public final Setting<Boolean> render = this.bool("Render", false).page("Render");
   public final Setting<Color> renderColor = this.color("Color", 255, 0, 0, 200).page("Render");
   public final Setting<Boolean> pulse = this.bool("Pulse", true)
      .page("Render")
      .describe("Flashes the target's Chams colour towards the colour above on every swing. Needs Chams to be drawing the target.");
   public final Setting<Double> pulseTime = this.number("PulseTime", (double)0.25, (double)0.05, (double)3.0)
      .page("Render")
      .describe("Seconds a pulse takes to fade back to the target's own colour.")
      .visibleWhen(() -> this.pulse.getValue());
   public final Setting<Float> pulseStrength = this.number("PulseStrength", 1.0F, 0.0F, 1.0F)
      .page("Render")
      .describe("How far towards the colour above the pulse pushes at its peak.")
      .visibleWhen(() -> this.pulse.getValue());
   private final AtomicBoolean attackPending = new AtomicBoolean();
   private final AtomicReference<KillAuraModule.AttackCompletion> attackCompletion = new AtomicReference<>();
   private final AtomicLong attackGeneration = new AtomicLong();
   private int currentTargetId = -1;
   private float attackCooldownTicks;
   private long lastCritAt;
   private volatile int pulseTargetId = -1;
   private volatile long pulsedAt;

   public KillAuraModule() {
      super("KillAura", Category.COMBAT, "Automatically attacks Nami-style prioritized targets.");
   }

   @Override
   public void onDisable() {
      this.currentTargetId = -1;
      this.attackCooldownTicks = 0.0F;
      this.attackGeneration.incrementAndGet();
      this.attackCompletion.set(null);
      this.pulseTargetId = -1;
   }

   @Override
   public void onTick() {
      this.applyAttackCompletion();
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (mc.field_1687 != null
         && mc.field_1724 != null
         && snapshot.available()
         && !snapshot.deadOrDying()
         && snapshot.playerId() == mc.field_1724.method_5628()) {
         float tps = Homovore2Client.tps().latest();
         this.attackCooldownTicks = Math.max(0.0F, this.attackCooldownTicks - tps / 20.0F);
         if (snapshot.usingItem() && snapshot.usingMainHand()) {
            this.currentTargetId = -1;
         } else {
            class_1297 target = this.findTarget(snapshot);
            if (target == null) {
               this.currentTargetId = -1;
            } else {
               this.currentTargetId = target.method_5628();
               Result weapon = this.findBestWeapon(snapshot);
               if (weapon == null) {
                  this.currentTargetId = -1;
               } else {
                  boolean skipCooldown = target instanceof class_1678
                     || target instanceof class_1309 living && living.method_6063() <= snapshot.estimatedAttackDamage();
                  class_238 targetBox = target.method_5829();
                  class_243 eye = RotationUtil.closestOrigin(targetBox, snapshot.possibleEyePositions());
                  if (RotationUtil.distanceTo(eye, targetBox) > 3.0) {
                     this.currentTargetId = -1;
                  } else {
                     class_243 aim = RotationUtil.closestAimPoint(eye, targetBox);
                     RotationManager.Angles angles = RotationUtil.anglesTo(eye, aim);
                     if (this.rotate.getValue() == KillAuraModule.Rotate.MOTION) {
                        if (skipCooldown || this.attackCooldownTicks <= 0.1 * tps) {
                           Homovore2Client.rotations().motion(angles, 6, null);
                        }

                        RotationManager.Angles server = Homovore2Client.rotations().serverRotation();
                        if (!targetBox.method_1006(eye) && !RotationUtil.rayIntersects(eye, targetBox, 3.0, server)) {
                           return;
                        }
                     }

                     if (skipCooldown || !(this.attackCooldownTicks > 0.0F)) {
                        if (this.stopSprinting.getValue() == KillAuraModule.Sprint.MOTION) {
                           SprintModule sprint = Homovore2Client.modules().get(SprintModule.class);
                           if (sprint != null && sprint.isEnabled()) {
                              sprint.stopSprinting(3);
                           }
                        }

                        boolean critical = this.shouldCritical(target, snapshot);
                        this.submitAttack(snapshot, target.method_5628(), weapon, weapon.stack(), angles, tps, skipCooldown, critical);
                     }
                  }
               }
            }
         }
      } else {
         this.currentTargetId = -1;
      }
   }

   private boolean shouldCritical(class_1297 target, InventorySnapshot snapshot) {
      if (this.criticals.getValue() && target instanceof class_1309 && snapshot.onGround() && System.currentTimeMillis() - this.lastCritAt >= 250L) {
         class_1309 player = mc.field_1724;
         return !player.method_5765()
               && !player.method_6128()
               && !player.method_5799()
               && !player.method_5771()
               && !player.method_6101()
               && !player.method_20448()
               && !player.method_6059(class_1294.field_5919)
            ? this.isPhased()
            : false;
      } else {
         return false;
      }
   }

   private boolean isPhased() {
      class_238 box = mc.field_1724.method_5829().method_1011(1.0E-4);
      return mc.field_1687.method_20812(mc.field_1724, box).iterator().hasNext();
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (this.render.getValue() && this.currentTargetId != -1 && mc.field_1687 != null) {
         class_1297 target = mc.field_1687.method_8469(this.currentTargetId);
         if (target != null) {
            class_243 interpolated = target.method_30950(partialTick);
            class_238 box = target.method_5829().method_997(interpolated.method_1020(target.method_73189()));
            class_12180.method_75542(box, class_12179.method_75536(this.renderColor.getValue().getRGB(), 1.5F), false).method_75533();
         }
      }
   }

   private void submitAttack(
      InventorySnapshot snapshot,
      int targetId,
      Result weapon,
      class_1799 cooldownStack,
      RotationManager.Angles angles,
      float tps,
      boolean skipCooldown,
      boolean critical
   ) {
      if (this.attackPending.compareAndSet(false, true)) {
         long generation = this.attackGeneration.get();
         class_2848 stop = null;
         class_2848 start = null;
         if ((this.stopSprinting.getValue() == KillAuraModule.Sprint.PACKET || critical) && snapshot.sprinting() && !snapshot.shiftKeyDown()) {
            stop = new class_2848(mc.field_1724, class_2849.field_12985);
            start = new class_2848(mc.field_1724, class_2849.field_12981);
         }

         class_2848 stopPacket = stop;
         class_2848 startPacket = start;
         double x = snapshot.x();
         double y = snapshot.y();
         double z = snapshot.z();
         boolean horizontalCollision = snapshot.horizontalCollision();
         RotationManager.Angles fallback = new RotationManager.Angles(snapshot.yaw(), snapshot.pitch());
         boolean[] sent = new boolean[]{false};
         boolean[] spoofed = new boolean[]{false};
         NetworkAction action = context -> {
            if (this.attackGeneration.get() == generation) {
               boolean spoof = critical && !context.awaitingPositionConfirmation();
               int cost = 2 + (stopPacket != null ? 2 : 0) + (spoof ? CRIT_OFFSETS.length : 0);
               if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= cost && context.limits().canAttack(targetId)) {
                  if (stopPacket != null) {
                     context.send(stopPacket);
                  }

                  if (spoof) {
                     RotationManager.Angles server = Homovore2Client.rotations().serverRotation();
                     RotationManager.Angles look = server != null ? server : fallback;

                     for (double offset : CRIT_OFFSETS) {
                        context.send(new class_2830(x, y + offset, z, look.yaw(), look.pitch(), false, horizontalCollision));
                     }
                  }

                  if (context.attack(targetId)) {
                     context.swing(class_1268.field_5808);
                     if (startPacket != null) {
                        context.send(startPacket);
                     }

                     spoofed[0] = spoof;
                     sent[0] = true;
                  }
               }
            }
         };
         CompletableFuture<Boolean> submitted = this.rotate.getValue() == KillAuraModule.Rotate.SILENT
            ? Homovore2Client.rotations().silentWithSwap(angles, weapon, action)
            : InventoryUtil.withSwap(weapon, action);
         submitted.whenComplete((attacked, failure) -> {
            if (failure == null && Boolean.TRUE.equals(attacked) && sent[0] && this.attackGeneration.get() == generation) {
               this.attackCompletion.set(new KillAuraModule.AttackCompletion(generation, targetId, cooldownStack, tps, skipCooldown, spoofed[0]));
            }

            this.attackPending.set(false);
         });
      }
   }

   private void applyAttackCompletion() {
      KillAuraModule.AttackCompletion completion = this.attackCompletion.getAndSet(null);
      if (completion != null && completion.generation() == this.attackGeneration.get()) {
         if (!completion.skipCooldown()) {
            this.attackCooldownTicks = this.baseCooldownTicks(completion.weapon(), completion.tps());
         }

         if (completion.critical()) {
            this.lastCritAt = System.currentTimeMillis();
         }

         if (mc.field_1724 != null) {
            mc.field_1724.method_23667(class_1268.field_5808, false);
            if (completion.critical() && mc.field_1687 != null) {
               class_1297 target = mc.field_1687.method_8469(completion.targetId());
               if (target != null) {
                  mc.field_1724.method_7277(target);
               }
            }

            this.pulseTargetId = completion.targetId();
            this.pulsedAt = System.currentTimeMillis();
         }
      }
   }

   public float pulseAmount(class_1297 entity) {
      if (this.pulse.getValue() && entity != null && entity.method_5628() == this.pulseTargetId) {
         float life = (float)(this.pulseTime.getValue() * 1000.0);
         long elapsed = System.currentTimeMillis() - this.pulsedAt;
         return elapsed >= 0L && !((float)elapsed >= life) ? (1.0F - (float)elapsed / life) * this.pulseStrength.getValue() : 0.0F;
      } else {
         return 0.0F;
      }
   }

   public int pulseColor() {
      return this.renderColor.getValue().getRGB() & 16777215;
   }

   public static KillAuraModule active() {
      if (Homovore2Client.modules() == null) {
         return null;
      }

      KillAuraModule module = Homovore2Client.modules().get(KillAuraModule.class);
      return module != null && module.isEnabled() ? module : null;
   }

   private class_1297 findTarget(InventorySnapshot snapshot) {
      List<class_1297> candidates = new ArrayList<>();

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity.method_5628() != snapshot.playerId()
            && !(targetDistance(entity, snapshot) > 3.0)
            && this.isTarget(entity)
            && !(entity instanceof class_1309 living && !living.method_5805())) {
            candidates.add(entity);
         }
      }

      Comparator<class_1297> distance = Comparator.comparingDouble(entityx -> targetDistance(entityx, snapshot));

      return switch ((KillAuraModule.Priority)this.priority.getValue()) {
         case DISTANCE -> (class_1297)candidates.stream().min(distance).orElse(null);
         case HEALTH -> (class_1297)candidates.stream()
            .filter(class_1309.class::isInstance)
            .min(Comparator.comparingDouble(entityx -> ((class_1309)entityx).method_6032()))
            .orElse(null);
         case SMART -> this.smartTarget(candidates, distance, snapshot);
      };
   }

   private class_1297 smartTarget(List<class_1297> candidates, Comparator<class_1297> distance, InventorySnapshot snapshot) {
      class_1297 player = candidates.stream().filter(class_1657.class::isInstance).min(distance).orElse(null);
      if (player != null) {
         return player;
      }

      class_1297 creeper = candidates.stream().filter(class_1548.class::isInstance).min(distance).orElse(null);
      if (creeper != null) {
         return creeper;
      }

      class_1297 projectile = candidates.stream().filter(this::isProjectile).min(distance).orElse(null);
      return projectile != null ? projectile : candidates.stream().min(distance).orElse(null);
   }

   private static double targetDistance(class_1297 entity, InventorySnapshot snapshot) {
      class_238 box = entity.method_5829();
      class_243 eye = RotationUtil.closestOrigin(box, snapshot.possibleEyePositions());
      return RotationUtil.distanceTo(eye, box);
   }

   private boolean isTarget(class_1297 entity) {
      if (entity instanceof class_1657 player) {
         return this.players.getValue() && !Homovore2Client.friends().isFriend(player);
      } else if (this.isProjectile(entity)) {
         return this.projectiles.getValue();
      } else if (!(entity instanceof class_1309)) {
         return false;
      } else {
         return !(entity instanceof class_1308 mob && (mob.method_6510() || mob instanceof class_1548 creeper && creeper.method_7007() > 0))
            ? entity instanceof class_1296 ? this.passives.getValue() : this.neutrals.getValue()
            : this.hostiles.getValue();
      }
   }

   private boolean isProjectile(class_1297 entity) {
      return entity instanceof class_1678 || entity instanceof class_1674;
   }

   private Result findBestWeapon(InventorySnapshot snapshot) {
      int bestSlot = -1;
      double bestDamage = Double.NEGATIVE_INFINITY;
      boolean prioritizeMace = !snapshot.flying() && !snapshot.onGround();

      for (int slot = 0; slot < 9 && slot < snapshot.size(); slot++) {
         class_1799 stack = snapshot.item(slot);
         if (isWeapon(stack)) {
            if (stack.method_7909() instanceof class_9362 && prioritizeMace) {
               return InventoryUtil.resultAt(snapshot, slot);
            }

            double damage = weaponDamage(stack);
            if (damage > bestDamage) {
               bestDamage = damage;
               bestSlot = slot;
            }
         }
      }

      return bestSlot < 0 ? null : InventoryUtil.resultAt(snapshot, bestSlot);
   }

   private static boolean isWeapon(class_1799 stack) {
      return !stack.method_7960()
         && (
            stack.method_31573(class_3489.field_42611)
               || stack.method_31573(class_3489.field_42612)
               || stack.method_7909() instanceof class_1835
               || stack.method_7909() instanceof class_9362
         );
   }

   private static double weaponDamage(class_1799 stack) {
      class_9285 modifiers = (class_9285)stack.method_58694(class_9334.field_49636);
      double damage = modifiers == null ? 0.0 : modifiers.method_57481(class_5134.field_23721, 0.0, class_1304.field_6173);
      if (stack.method_31573(class_3489.field_42611)) {
         damage += 5.0;
      }

      for (Entry<class_6880<class_1887>> enchantment : stack.method_58657().method_57539()) {
         int level = enchantment.getIntValue();
         if (((class_6880)enchantment.getKey()).method_40225(class_1893.field_9118)) {
            damage += level * 1.25;
         } else if (((class_6880)enchantment.getKey()).method_40225(class_1893.field_9123)
            || ((class_6880)enchantment.getKey()).method_40225(class_1893.field_9112)) {
            damage += level * 2.5;
         }
      }

      return damage;
   }

   private float baseCooldownTicks(class_1799 stack, float tps) {
      float base;
      if (stack.method_31573(class_3489.field_42611)) {
         base = 13.0F;
      } else if (stack.method_31573(class_3489.field_42612)) {
         base = 21.0F;
      } else if (stack.method_7909() instanceof class_1835) {
         base = 19.0F;
      } else if (stack.method_7909() instanceof class_9362) {
         base = 34.0F;
      } else {
         base = 3.3333333F;
      }

      return base * (20.0F / Math.max(0.1F, tps)) * this.delay.getValue().floatValue();
   }

   private record AttackCompletion(long generation, int targetId, class_1799 weapon, float tps, boolean skipCooldown, boolean critical) {
      private AttackCompletion {
         weapon = weapon.method_7972();
      }
   }

   public enum Priority {
      DISTANCE,
      HEALTH,
      SMART;
   }

   public enum Rotate {
      MOTION,
      SILENT;
   }

   public enum Sprint {
      NONE,
      MOTION,
      PACKET;
   }
}
