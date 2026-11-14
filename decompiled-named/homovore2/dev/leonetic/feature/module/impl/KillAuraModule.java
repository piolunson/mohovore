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
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

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
      if (mc.world != null && mc.player != null && snapshot.available() && !snapshot.deadOrDying() && snapshot.playerId() == mc.player.getId()) {
         float tps = Homovore2Client.tps().latest();
         this.attackCooldownTicks = Math.max(0.0F, this.attackCooldownTicks - tps / 20.0F);
         if (snapshot.usingItem() && snapshot.usingMainHand()) {
            this.currentTargetId = -1;
         } else {
            Entity target = this.findTarget(snapshot);
            if (target == null) {
               this.currentTargetId = -1;
            } else {
               this.currentTargetId = target.getId();
               Result weapon = this.findBestWeapon(snapshot);
               if (weapon == null) {
                  this.currentTargetId = -1;
               } else {
                  boolean skipCooldown = target instanceof ShulkerBulletEntity
                     || target instanceof LivingEntity living && living.getMaxHealth() <= snapshot.estimatedAttackDamage();
                  Box targetBox = target.getBoundingBox();
                  Vec3d eye = RotationUtil.closestOrigin(targetBox, snapshot.possibleEyePositions());
                  if (RotationUtil.distanceTo(eye, targetBox) > 3.0) {
                     this.currentTargetId = -1;
                  } else {
                     Vec3d aim = RotationUtil.closestAimPoint(eye, targetBox);
                     RotationManager.Angles angles = RotationUtil.anglesTo(eye, aim);
                     if (this.rotate.getValue() == KillAuraModule.Rotate.MOTION) {
                        if (skipCooldown || this.attackCooldownTicks <= 0.1 * tps) {
                           Homovore2Client.rotations().motion(angles, 6, null);
                        }

                        RotationManager.Angles server = Homovore2Client.rotations().serverRotation();
                        if (!targetBox.contains(eye) && !RotationUtil.rayIntersects(eye, targetBox, 3.0, server)) {
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
                        this.submitAttack(snapshot, target.getId(), weapon, weapon.stack(), angles, tps, skipCooldown, critical);
                     }
                  }
               }
            }
         }
      } else {
         this.currentTargetId = -1;
      }
   }

   private boolean shouldCritical(Entity target, InventorySnapshot snapshot) {
      if (this.criticals.getValue() && target instanceof LivingEntity && snapshot.onGround() && System.currentTimeMillis() - this.lastCritAt >= 250L) {
         LivingEntity player = mc.player;
         return !player.hasVehicle()
               && !player.isGliding()
               && !player.isTouchingWater()
               && !player.isInLava()
               && !player.isClimbing()
               && !player.isCrawling()
               && !player.hasStatusEffect(StatusEffects.BLINDNESS)
            ? this.isPhased()
            : false;
      } else {
         return false;
      }
   }

   private boolean isPhased() {
      Box box = mc.player.getBoundingBox().contract(1.0E-4);
      return mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (this.render.getValue() && this.currentTargetId != -1 && mc.world != null) {
         Entity target = mc.world.getEntityById(this.currentTargetId);
         if (target != null) {
            Vec3d interpolated = target.getLerpedPos(partialTick);
            Box box = target.getBoundingBox().offset(interpolated.subtract(target.getEntityPos()));
            GizmoDrawing.box(box, DrawStyle.stroked(this.renderColor.getValue().getRGB(), 1.5F), false).ignoreOcclusion();
         }
      }
   }

   private void submitAttack(
      InventorySnapshot snapshot,
      int targetId,
      Result weapon,
      ItemStack cooldownStack,
      RotationManager.Angles angles,
      float tps,
      boolean skipCooldown,
      boolean critical
   ) {
      if (this.attackPending.compareAndSet(false, true)) {
         long generation = this.attackGeneration.get();
         ClientCommandC2SPacket stop = null;
         ClientCommandC2SPacket start = null;
         if ((this.stopSprinting.getValue() == KillAuraModule.Sprint.PACKET || critical) && snapshot.sprinting() && !snapshot.shiftKeyDown()) {
            stop = new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING);
            start = new ClientCommandC2SPacket(mc.player, Mode.START_SPRINTING);
         }

         ClientCommandC2SPacket stopPacket = stop;
         ClientCommandC2SPacket startPacket = start;
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
                        context.send(new Full(x, y + offset, z, look.yaw(), look.pitch(), false, horizontalCollision));
                     }
                  }

                  if (context.attack(targetId)) {
                     context.swing(Hand.MAIN_HAND);
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

         if (mc.player != null) {
            mc.player.swingHand(Hand.MAIN_HAND, false);
            if (completion.critical() && mc.world != null) {
               Entity target = mc.world.getEntityById(completion.targetId());
               if (target != null) {
                  mc.player.addCritParticles(target);
               }
            }

            this.pulseTargetId = completion.targetId();
            this.pulsedAt = System.currentTimeMillis();
         }
      }
   }

   public float pulseAmount(Entity entity) {
      if (this.pulse.getValue() && entity != null && entity.getId() == this.pulseTargetId) {
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

   private Entity findTarget(InventorySnapshot snapshot) {
      List<Entity> candidates = new ArrayList<>();

      for (Entity entity : mc.world.getEntities()) {
         if (entity.getId() != snapshot.playerId()
            && !(targetDistance(entity, snapshot) > 3.0)
            && this.isTarget(entity)
            && !(entity instanceof LivingEntity living && !living.isAlive())) {
            candidates.add(entity);
         }
      }

      Comparator<Entity> distance = Comparator.comparingDouble(entityx -> targetDistance(entityx, snapshot));

      return switch ((KillAuraModule.Priority)this.priority.getValue()) {
         case DISTANCE -> (Entity)candidates.stream().min(distance).orElse(null);
         case HEALTH -> (Entity)candidates.stream()
            .filter(LivingEntity.class::isInstance)
            .min(Comparator.comparingDouble(entityx -> ((LivingEntity)entityx).getHealth()))
            .orElse(null);
         case SMART -> this.smartTarget(candidates, distance, snapshot);
      };
   }

   private Entity smartTarget(List<Entity> candidates, Comparator<Entity> distance, InventorySnapshot snapshot) {
      Entity player = candidates.stream().filter(PlayerEntity.class::isInstance).min(distance).orElse(null);
      if (player != null) {
         return player;
      }

      Entity creeper = candidates.stream().filter(CreeperEntity.class::isInstance).min(distance).orElse(null);
      if (creeper != null) {
         return creeper;
      }

      Entity projectile = candidates.stream().filter(this::isProjectile).min(distance).orElse(null);
      return projectile != null ? projectile : candidates.stream().min(distance).orElse(null);
   }

   private static double targetDistance(Entity entity, InventorySnapshot snapshot) {
      Box box = entity.getBoundingBox();
      Vec3d eye = RotationUtil.closestOrigin(box, snapshot.possibleEyePositions());
      return RotationUtil.distanceTo(eye, box);
   }

   private boolean isTarget(Entity entity) {
      if (entity instanceof PlayerEntity player) {
         return this.players.getValue() && !Homovore2Client.friends().isFriend(player);
      } else if (this.isProjectile(entity)) {
         return this.projectiles.getValue();
      } else if (!(entity instanceof LivingEntity)) {
         return false;
      } else {
         return !(entity instanceof MobEntity mob && (mob.isAttacking() || mob instanceof CreeperEntity creeper && creeper.getFuseSpeed() > 0))
            ? entity instanceof PassiveEntity ? this.passives.getValue() : this.neutrals.getValue()
            : this.hostiles.getValue();
      }
   }

   private boolean isProjectile(Entity entity) {
      return entity instanceof ShulkerBulletEntity || entity instanceof FireballEntity;
   }

   private Result findBestWeapon(InventorySnapshot snapshot) {
      int bestSlot = -1;
      double bestDamage = Double.NEGATIVE_INFINITY;
      boolean prioritizeMace = !snapshot.flying() && !snapshot.onGround();

      for (int slot = 0; slot < 9 && slot < snapshot.size(); slot++) {
         ItemStack stack = snapshot.item(slot);
         if (isWeapon(stack)) {
            if (stack.getItem() instanceof MaceItem && prioritizeMace) {
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

   private static boolean isWeapon(ItemStack stack) {
      return !stack.isEmpty()
         && (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.getItem() instanceof TridentItem || stack.getItem() instanceof MaceItem);
   }

   private static double weaponDamage(ItemStack stack) {
      AttributeModifiersComponent modifiers = (AttributeModifiersComponent)stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
      double damage = modifiers == null ? 0.0 : modifiers.applyOperations(EntityAttributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
      if (stack.isIn(ItemTags.SWORDS)) {
         damage += 5.0;
      }

      for (Entry<RegistryEntry<Enchantment>> enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         int level = enchantment.getIntValue();
         if (((RegistryEntry)enchantment.getKey()).matchesKey(Enchantments.SHARPNESS)) {
            damage += level * 1.25;
         } else if (((RegistryEntry)enchantment.getKey()).matchesKey(Enchantments.SMITE)
            || ((RegistryEntry)enchantment.getKey()).matchesKey(Enchantments.BANE_OF_ARTHROPODS)) {
            damage += level * 2.5;
         }
      }

      return damage;
   }

   private float baseCooldownTicks(ItemStack stack, float tps) {
      float base;
      if (stack.isIn(ItemTags.SWORDS)) {
         base = 13.0F;
      } else if (stack.isIn(ItemTags.AXES)) {
         base = 21.0F;
      } else if (stack.getItem() instanceof TridentItem) {
         base = 19.0F;
      } else if (stack.getItem() instanceof MaceItem) {
         base = 34.0F;
      } else {
         base = 3.3333333F;
      }

      return base * (20.0F / Math.max(0.1F, tps)) * this.delay.getValue().floatValue();
   }

   private record AttackCompletion(long generation, int targetId, ItemStack weapon, float tps, boolean skipCooldown, boolean critical) {
      private AttackCompletion {
         weapon = weapon.copy();
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
