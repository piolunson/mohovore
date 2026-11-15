package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Bind;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.manager.RotationManager;
import dev.piolunson.v2.util.inventory.InventorySnapshot;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.inventory.Result;
import dev.piolunson.v2.util.network.NetworkAction;
import dev.piolunson.v2.util.network.NetworkActionContext;
import dev.piolunson.v2.util.network.NetworkThreadExecutor;
import dev.piolunson.v2.util.network.PacketLimiter;
import dev.piolunson.v2.util.trace.ActionTrace;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.item.Items;

public final class MiddleClickModule extends Module {
   private static final int RETRY_TICKS = 8;
   private static final int PEARL_COOLDOWN_TIMEOUT = 20;
   private final Setting<Bind> click = this.register(new Setting<>("Click", Bind.mouse(2))).describe("The button that throws the pearl or firework.");
   private final Setting<Boolean> fireworkInAir = this.register(new Setting<>("FireworkInAir", true))
      .describe("Fires a rocket instead of a pearl while gliding with an elytra.");
   private final AtomicBoolean pending = new AtomicBoolean();
   private final AtomicBoolean done = new AtomicBoolean();
   private final AtomicLong generation = new AtomicLong();
   private boolean down;
   private int attemptsLeft;
   private boolean threwPearl;
   private int awaitingPearlCooldown;
   private volatile ActionTrace trace;
   private String blockedBy = "";

   public MiddleClickModule() {
      super("MiddleClick", Category.EXTRA, "Throws a pearl or firework on middle click.");
   }

   @Override
   public int getTickPriority() {
      return 2147483645;
   }

   @Override
   public void onDisable() {
      this.generation.incrementAndGet();
      this.dropTrace("module disabled");
      this.attemptsLeft = 0;
      this.awaitingPearlCooldown = 0;
      this.down = false;
      this.pending.set(false);
      this.done.set(false);
      Homovore2Client.rotations().releaseSilent(this);
   }

   @Override
   public void onTick() {
      Bind bind = this.click.getValue();
      boolean isDown = !bind.isEmpty() && bind.isDown();
      boolean pressed = isDown && !this.down;
      this.down = isDown;
      if (pressed && mc.currentScreen == null) {
         ActionTrace press = ActionTrace.press(this, "click", bind);
         if (this.attemptsLeft > 0) {
            press.dropped("previous press still outstanding");
         } else {
            this.trace = press;
            this.attemptsLeft = 8;
         }
      }

      if (this.done.getAndSet(false)) {
         if (this.threwPearl) {
            this.awaitingPearlCooldown = 20;
         }

         this.attemptsLeft = 0;
      }

      this.tickPearlCooldown();
      if (!this.pending.get()) {
         if (this.attemptsLeft > 0 && this.tryAct()) {
            this.attemptsLeft--;
         } else {
            this.dropTrace(this.attemptsLeft > 0 ? this.blockedBy : "all 8 attempts used, last: " + this.blockedBy);
            this.attemptsLeft = 0;
            Homovore2Client.rotations().releaseSilent(this);
         }
      }
   }

   private void dropTrace(String reason) {
      ActionTrace current = this.trace;
      this.trace = null;
      if (current != null) {
         current.dropped(reason);
      }
   }

   private boolean abandon(String reason) {
      this.blockedBy = reason;
      return false;
   }

   private boolean waitFor(String reason) {
      this.blockedBy = reason;
      ActionTrace current = this.trace;
      if (current != null) {
         current.waiting(reason);
      }

      return true;
   }

   private boolean firework() {
      return this.fireworkInAir.getValue() && mc.player != null && mc.player.isGliding();
   }

   private void tickPearlCooldown() {
      if (this.awaitingPearlCooldown > 0 && mc.player != null) {
         if (mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
            this.awaitingPearlCooldown = 0;
         } else {
            this.awaitingPearlCooldown--;
         }
      }
   }

   private boolean tryAct() {
      if (mc.currentScreen == null && mc.player != null && mc.world != null) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (!snapshot.available()) {
            return this.abandon("inventory snapshot unavailable");
         }

         if (snapshot.menuId() != 0) {
            return this.abandon("container open");
         }

         if (snapshot.deadOrDying()) {
            return this.abandon("dead");
         }

         boolean rocket = this.firework();
         Result item = InventoryUtil.find(rocket ? Items.FIREWORK_ROCKET : Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
         if (!item.found()) {
            return this.abandon(rocket ? "no firework" : "no pearl");
         }

         if (!rocket && this.awaitingPearlCooldown > 0) {
            return this.abandon("awaiting the last pearl's cooldown packet");
         }

         if (!rocket && mc.player.getItemCooldownManager().isCoolingDown(item.stack())) {
            return this.abandon("pearl cooldown");
         }

         if (!useItemFits()) {
            return this.waitFor("use-item budget full " + useBudget());
         }

         if (rocket) {
            Homovore2Client.rotations().releaseSilent(this);
         } else {
            Homovore2Client.rotations().reserveSilent(this);
            if (Homovore2Client.rotations().silentReservedByOther(this)) {
               return this.waitFor("silent rotation held by another module");
            }
         }

         this.submit(rocket, snapshot, item);
         return true;
      } else {
         return this.abandon("screen open or not in game");
      }
   }

   private void submit(boolean rocket, InventorySnapshot snapshot, Result item) {
      if (this.pending.compareAndSet(false, true)) {
         this.threwPearl = !rocket;
         ActionTrace press = this.trace;
         if (press != null) {
            press.attempt((rocket ? "firework" : "pearl") + " slot=" + item.hand());
         }

         long submission = this.generation.get();
         RotationManager.Angles angles = new RotationManager.Angles(snapshot.yaw(), snapshot.pitch());
         NetworkAction task = context -> {
            if (this.generation.get() == submission) {
               if (!context.limits().canSend(PacketLimiter.Bucket.USE_ITEM, 1, this)) {
                  refused(press, "use-item budget " + useBudget());
               } else if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < (rocket ? 1 : 2)) {
                  refused(press, "global budget");
               } else if (rocket) {
                  this.use(context, press, item, snapshot.yaw(), snapshot.pitch());
               } else {
                  try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
                        .beginSilentNow(context, snapshot, angles, snapshot.horizontalCollision(), this)) {
                     if (!rotation.applied()) {
                        refused(press, "silent rotation not applied");
                        return;
                     }

                     this.use(context, press, item, angles.yaw(), angles.pitch());
                  }

                  return;
               }
            }
         };

         try {
            InventoryUtil.withSwap(item, task).whenComplete((swapped, failure) -> {
               if (failure != null) {
                  refused(press, "swap failed: " + failure);
               } else if (!Boolean.TRUE.equals(swapped)) {
                  refused(press, "swap refused");
               }

               this.pending.set(false);
            });
         } catch (RuntimeException exception) {
            refused(press, "swap threw: " + exception);
            this.pending.set(false);
         }
      }
   }

   private void use(NetworkActionContext context, ActionTrace press, Result item, float yaw, float pitch) {
      if (context.useItem(item.hand(), yaw, pitch, this)) {
         if (press != null) {
            press.sent(context.lastUseSequence());
         }

         this.done.set(true);
      } else {
         refused(press, "use-item budget at send " + useBudget());
      }
   }

   private static void refused(ActionTrace press, String reason) {
      if (press != null) {
         press.refused(reason);
      }
   }

   private static String useBudget() {
      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      if (limiter == null) {
         return "(no connection)";
      }

      PacketLimiter.Snapshot budget = limiter.snapshot();
      return budget.useItemUsed() + "/" + budget.useItemLimit();
   }

   private static boolean useItemFits() {
      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      if (limiter == null) {
         return false;
      }

      PacketLimiter.Snapshot budget = limiter.snapshot();
      return budget.useItemLimit() <= 0 || budget.useItemUsed() < budget.useItemLimit();
   }
}
