package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.trace.ActionTrace;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;

public final class AutoXpModule extends Module {
   private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final float THROW_PITCH = 90.0F;
   public final Setting<Integer> minDurability = this.integer("MinDurability", 60, 1, 100)
      .describe("Starts throwing once a mending piece falls to this percent.");
   public final Setting<Integer> stopDurability = this.integer("StopDurability", 90, 0, 100)
      .describe("Stops once every mending piece is back above this percent.");
   public final Setting<Integer> burst = this.integer("Burst", 3, 1, 36)
      .describe("Bottles to attempt per tick. The packet limiter caps what actually goes out.");
   public final Setting<Boolean> rotate = this.bool("Rotate", true)
      .describe("Aims the burst with a silent downward movement packet. Off throws along whatever rotation the server last saw.");
   public final Setting<Boolean> pauseWhileUsing = this.bool("PauseWhileUsing", true)
      .describe("Holds off while an item is being used, so a swap cannot interrupt an eat.");
   public final Setting<Bind> throwBind = this.register(new Setting<>("Throw", Bind.none())).describe("Throws while held, whatever the armour durability is.");
   private final AtomicBoolean pending = new AtomicBoolean();
   private final AtomicLong generation = new AtomicLong();
   private boolean throwing;
   private boolean manualDown;
   private volatile ActionTrace trace;
   private final AtomicInteger heldThrown = new AtomicInteger();

   public AutoXpModule() {
      super("AutoXP", Category.SAFETY, "Throws experience bottles to mend armour whose durability has run low.");
   }

   @Override
   public void onTick() {
      Bind bind = this.throwBind.getValue();
      boolean held = !bind.isEmpty() && bind.isDown();
      if (held && !this.manualDown && mc.currentScreen == null) {
         this.endHold("replaced by a new press");
         this.heldThrown.set(0);
         this.trace = ActionTrace.press(this, "throw", bind);
      } else if (!held && this.manualDown) {
         this.endHold(null);
      }

      this.manualDown = held;
      if (mc.player != null && mc.currentScreen == null) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (!snapshot.available()) {
            this.waiting("inventory snapshot unavailable");
         } else if (snapshot.menuId() != 0 || snapshot.deadOrDying()) {
            this.waiting(snapshot.deadOrDying() ? "dead" : "container open");
         } else if (!snapshot.onGround()) {
            this.waiting("not on ground");
         } else if (this.pauseWhileUsing.getValue() && snapshot.usingItem()) {
            this.waiting("using an item");
         } else {
            if (this.anyMendingArmorAtOrBelow(this.minDurability.getValue())) {
               this.throwing = true;
            } else if (this.throwing && !this.anyMendingArmorAtOrBelow(this.stopDurability.getValue())) {
               this.throwing = false;
            }

            if (held || this.throwing) {
               if (this.rotate.getValue() && Homovore2Client.rotations().silentReservedByOther(this)) {
                  this.waiting("silent rotation held by another module");
               } else {
                  Result bottle = InventoryUtil.find(Items.EXPERIENCE_BOTTLE, InventoryUtil.FULL_SCOPE);
                  if (!bottle.found()) {
                     this.waiting("no bottles");
                  } else {
                     this.throwBurst(snapshot, bottle);
                  }
               }
            }
         }
      } else {
         this.waiting("screen open or not in game");
      }
   }

   @Override
   public void onDisable() {
      this.endHold("module disabled");
      this.manualDown = false;
      this.throwing = false;
      this.generation.incrementAndGet();
      this.pending.set(false);
   }

   private void waiting(String reason) {
      ActionTrace current = this.trace;
      if (current != null && !current.finished()) {
         current.waiting(reason);
      }
   }

   private void endHold(String reason) {
      ActionTrace current = this.trace;
      this.trace = null;
      if (current != null) {
         int bottles = this.heldThrown.getAndSet(0);
         if (bottles == 0) {
            current.dropped(reason == null ? "released before any bottle went out" : reason);
         } else {
            current.released(bottles + " bottles" + (reason == null ? "" : ", " + reason));
         }
      }
   }

   private void throwBurst(InventorySnapshot snapshot, Result bottle) {
      if (this.pending.compareAndSet(false, true)) {
         ActionTrace press = this.trace;
         if (press != null && !press.finished()) {
            press.attempt("burst of " + this.burst.getValue());
         }

         long submission = this.generation.get();
         int requested = this.burst.getValue();
         RotationManager.Angles angles = new RotationManager.Angles(snapshot.yaw(), 90.0F);
         NetworkAction action = context -> {
            if (this.generation.get() == submission) {
               boolean shouldRotate = this.rotate.getValue();

               try (RotationManager.SilentRotation rotation = shouldRotate
                     ? Homovore2Client.rotations().beginSilentNow(context, snapshot, angles, snapshot.horizontalCollision(), this)
                     : null) {
                  if (!shouldRotate || rotation.applied()) {
                     int allowed = Math.min(requested, context.limits().remaining(PacketLimiter.Bucket.USE_ITEM));
                     if (allowed <= 0 && press != null && !press.finished()) {
                        press.refused("use-item budget full");
                     }

                     for (int i = 0; i < allowed && context.useItem(bottle.hand(), angles.yaw(), angles.pitch()); i++) {
                        if (press != null && this.trace == press) {
                           this.heldThrown.incrementAndGet();
                           press.sent(context.lastUseSequence());
                        }
                     }
                  } else {
                     if (press != null && !press.finished()) {
                        press.refused("silent rotation not applied");
                     }
                  }
               }
            }
         };

         CompletableFuture<Boolean> submitted;
         try {
            submitted = InventoryUtil.withSwap(bottle, action);
         } catch (RuntimeException exception) {
            this.pending.set(false);
            return;
         }

         submitted.whenComplete((swapped, failure) -> this.pending.set(false));
      }
   }

   private boolean anyMendingArmorAtOrBelow(int percent) {
      for (EquipmentSlot slot : ARMOR) {
         ItemStack armor = mc.player.getEquippedStack(slot);
         if (!armor.isEmpty() && armor.getMaxDamage() > 0 && hasMending(armor)) {
            float durability = (float)(armor.getMaxDamage() - armor.getDamage()) / armor.getMaxDamage() * 100.0F;
            if (durability <= percent) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean hasMending(ItemStack stack) {
      for (Entry<RegistryEntry<Enchantment>> enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         if (((RegistryEntry)enchantment.getKey()).matchesKey(Enchantments.MENDING)) {
            return true;
         }
      }

      return false;
   }
}
