package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.BindPressEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.util.RotationUtil;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.trace.ActionTrace;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_3532;

public final class PhaseModule extends Module {
   private static final double AIM_DROP = 0.5;
   private static final int STEEP_ABOVE_BLOCK_Y = 4;
   private static final float STEEP_PITCH = 85.0F;
   private static final float SHALLOW_PITCH = 75.0F;
   private static final int RETRY_TICKS = 8;
   private static final int WAIT_TICKS = 40;
   private static final long NO_PRESS = -1L;
   public final Setting<Bind> throwBind = this.register(new Setting<>("Throw", Bind.none())).describe("Throws one ender pearl on each press.");
   private final AtomicBoolean pending = new AtomicBoolean();
   private final AtomicLong thrown = new AtomicLong(-1L);
   private final AtomicLong swapRefused = new AtomicLong(-1L);
   private long pressId;
   private int attemptsLeft;
   private int waitLeft;
   private volatile ActionTrace trace;
   private String blockedBy = "";

   public PhaseModule() {
      super("Phase", Category.COMBAT, "Throws a pearl into the block corner underfoot to phase into walls.");
   }

   @Override
   public int getTickPriority() {
      return Integer.MAX_VALUE;
   }

   @Override
   public void onDisable() {
      this.dropTrace("module disabled");
      this.attemptsLeft = 0;
      this.waitLeft = 0;
      this.swapRefused.set(-1L);
      Homovore2Client.rotations().releaseSilent(this);
      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      if (limiter != null) {
         limiter.releaseUseItemPriority(this);
      }
   }

   @Override
   public void onTick() {
      this.settle();
      this.holdUseSlot(!this.throwBind.getValue().isEmpty() && this.pearl() != null);
      this.advance();
   }

   @Subscribe
   public void onBindPress(BindPressEvent event) {
      Bind bind = this.throwBind.getValue();
      if (event.matches(bind) && mc.field_1755 == null) {
         this.settle();
         this.dropTrace("replaced by a new press");
         this.pressId++;
         this.trace = ActionTrace.press(this, "throw", bind);
         this.attemptsLeft = 8;
         this.waitLeft = 40;
         this.advance();
      }
   }

   private void settle() {
      if (this.thrown.getAndSet(-1L) == this.pressId) {
         this.endPress("thrown");
      }

      if (this.swapRefused.getAndSet(-1L) == this.pressId && this.attemptsLeft > 0) {
         this.attemptsLeft = Math.min(this.attemptsLeft + 1, 8);
         if (--this.waitLeft <= 0) {
            this.endPress("wait ran out after swap refusals");
         }
      }
   }

   private void advance() {
      if (!this.pending.get()) {
         if (this.attemptsLeft <= 0) {
            this.endPress("all 8 attempts refused");
         } else {
            switch (this.tryThrow()) {
               case SUBMITTED:
                  this.attemptsLeft--;
                  break;
               case WAIT:
                  ActionTrace current = this.trace;
                  if (current != null) {
                     current.waiting(this.blockedBy);
                  }

                  if (--this.waitLeft <= 0) {
                     this.endPress("waited 40 ticks: " + this.blockedBy);
                  }
                  break;
               case ABANDON:
                  this.endPress(this.blockedBy);
            }
         }
      }
   }

   private void endPress(String reason) {
      this.dropTrace(reason);
      this.attemptsLeft = 0;
      this.waitLeft = 0;
      Homovore2Client.rotations().releaseSilent(this);
   }

   private void dropTrace(String reason) {
      ActionTrace current = this.trace;
      this.trace = null;
      if (current != null) {
         current.dropped(reason);
      }
   }

   private PhaseModule.Attempt tryThrow() {
      if (mc.field_1755 == null && mc.field_1724 != null && mc.field_1687 != null) {
         Result pearl = this.pearl();
         if (pearl == null) {
            this.blockedBy = "no pearl";
            return PhaseModule.Attempt.ABANDON;
         } else {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (!snapshot.available()) {
               this.blockedBy = "inventory snapshot unavailable";
               return PhaseModule.Attempt.WAIT;
            } else if (mc.field_1724.method_18276()) {
               this.blockedBy = "crouching";
               return PhaseModule.Attempt.WAIT;
            } else if (mc.field_1724.method_7357().method_7904(pearl.stack())) {
               this.blockedBy = "pearl cooldown";
               return PhaseModule.Attempt.WAIT;
            } else {
               RotationManager.Angles angles = new RotationManager.Angles(
                  RotationUtil.anglesTo(snapshot.eyePosition(), aimPoint(snapshot)).yaw(), pitchFor(snapshot)
               );
               this.submit(snapshot, pearl, angles);
               return PhaseModule.Attempt.SUBMITTED;
            }
         }
      } else {
         this.blockedBy = "screen open or not in game";
         return PhaseModule.Attempt.ABANDON;
      }
   }

   private Result pearl() {
      Result pearl = InventoryUtil.find(class_1802.field_8634, InventoryUtil.FULL_SCOPE);
      return pearl.found() ? pearl : null;
   }

   private void holdUseSlot(boolean hold) {
      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      if (limiter != null) {
         if (hold) {
            limiter.holdUseItemPriority(this);
         } else {
            limiter.releaseUseItemPriority(this);
         }
      }
   }

   private void submit(InventorySnapshot snapshot, Result pearl, RotationManager.Angles angles) {
      if (this.pending.compareAndSet(false, true)) {
         long id = this.pressId;
         ActionTrace press = this.trace;
         if (press != null) {
            press.attempt(String.format("slot=%s rot=%.1f/%.1f", pearl.hand(), angles.yaw(), angles.pitch()));
         }

         Homovore2Client.rotations().preemptSilent(this);
         NetworkAction action = context -> {
            if (!context.limits().canSend(PacketLimiter.Bucket.USE_ITEM, 1, this)) {
               refused(press, "use-item budget " + useBudget(context.limits()));
            } else if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
               refused(press, "global budget");
            } else {
               try (RotationManager.SilentRotation rotation = Homovore2Client.rotations().beginSilentNow(context, snapshot, angles, true, this)) {
                  if (!rotation.applied()) {
                     refused(press, "silent rotation not applied");
                     return;
                  }

                  if (context.useItem(pearl.hand(), angles.yaw(), angles.pitch(), this)) {
                     if (press != null) {
                        press.sent(context.lastUseSequence());
                     }

                     this.thrown.set(id);
                  } else {
                     refused(press, "use-item budget at send " + useBudget(context.limits()));
                  }
               }

               return;
            }
         };
         InventoryUtil.withSwap(pearl, action).whenComplete((swapped, failure) -> {
            if (failure != null) {
               refused(press, "swap failed: " + failure);
            } else if (!Boolean.TRUE.equals(swapped)) {
               refused(press, "swap refused");
               this.swapRefused.set(id);
            }

            this.pending.set(false);
         });
      }
   }

   private static void refused(ActionTrace press, String reason) {
      if (press != null) {
         press.refused(reason);
      }
   }

   private static String useBudget(PacketLimiter limiter) {
      PacketLimiter.Snapshot budget = limiter.snapshot();
      return budget.useItemUsed() + "/" + budget.useItemLimit();
   }

   private static class_243 aimPoint(InventorySnapshot snapshot) {
      return new class_243(Math.round(snapshot.x()), snapshot.y() - 0.5, Math.round(snapshot.z()));
   }

   private static float pitchFor(InventorySnapshot snapshot) {
      return class_3532.method_15357(snapshot.y()) > 4 ? 85.0F : 75.0F;
   }

   private enum Attempt {
      SUBMITTED,
      WAIT,
      ABANDON;
   }
}
