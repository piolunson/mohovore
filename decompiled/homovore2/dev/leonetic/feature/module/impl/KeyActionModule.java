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
import dev.leonetic.util.network.NetworkActionContext;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.trace.ActionTrace;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.class_1291;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1844;
import net.minecraft.class_6880;
import net.minecraft.class_9334;

public final class KeyActionModule extends Module {
   private static final float SPLASH_PITCH = 90.0F;
   private static final int RETRY_TICKS = 8;
   private static final int PEARL_COOLDOWN_TIMEOUT = 20;
   private final Map<KeyActionModule.Action, Setting<Bind>> binds = new EnumMap<>(KeyActionModule.Action.class);
   private final AtomicBoolean pending = new AtomicBoolean();
   private final AtomicBoolean done = new AtomicBoolean();
   private final AtomicLong generation = new AtomicLong();
   private final ArrayDeque<KeyActionModule.Press> queue = new ArrayDeque<>();
   private final Map<KeyActionModule.Action, Boolean> down = new EnumMap<>(KeyActionModule.Action.class);
   private int awaitingPearlCooldown;

   public KeyActionModule() {
      super("KeyAction", Category.EXTRA, "Binds keys to throw potions, pearls and fireworks.");

      for (KeyActionModule.Action action : KeyActionModule.Action.values()) {
         this.binds.put(action, this.register(new Setting<>(action.label, Bind.none())).describe(action.description));
         this.down.put(action, false);
      }
   }

   @Override
   public int getTickPriority() {
      return 2147483645;
   }

   @Override
   public void onDisable() {
      this.generation.incrementAndGet();

      for (KeyActionModule.Press press : this.queue) {
         press.trace.dropped("module disabled");
      }

      this.queue.clear();
      this.awaitingPearlCooldown = 0;
      this.pending.set(false);
      this.done.set(false);
      Homovore2Client.rotations().releaseSilent(this);
   }

   @Override
   public void onTick() {
      for (KeyActionModule.Action action : KeyActionModule.Action.values()) {
         Bind bind = this.binds.get(action).getValue();
         boolean isDown = !bind.isEmpty() && bind.isDown();
         boolean pressed = isDown && !this.down.get(action);
         this.down.put(action, isDown);
         if (pressed && mc.field_1755 == null) {
            ActionTrace trace = ActionTrace.press(this, action.label, bind);
            if (this.queued(action)) {
               trace.dropped("same action already queued");
            } else {
               this.queue.add(new KeyActionModule.Press(action, trace));
            }
         }
      }

      if (this.done.getAndSet(false)) {
         KeyActionModule.Press finished = this.queue.poll();
         if (finished != null && finished.action == KeyActionModule.Action.PEARL) {
            this.awaitingPearlCooldown = 20;
         }
      }

      this.tickPearlCooldown();
      if (!this.pending.get()) {
         while (!this.queue.isEmpty()) {
            KeyActionModule.Press press = this.queue.peek();
            if (press.attemptsLeft > 0 && this.tryAct(press)) {
               press.attemptsLeft--;
               return;
            }

            this.queue.poll();
            press.trace.dropped(press.attemptsLeft > 0 ? press.blockedBy : "all 8 attempts used, last: " + press.blockedBy);
         }

         Homovore2Client.rotations().releaseSilent(this);
      }
   }

   private void tickPearlCooldown() {
      if (this.awaitingPearlCooldown > 0 && mc.field_1724 != null) {
         if (mc.field_1724.method_7357().method_7904(class_1802.field_8634.method_7854())) {
            this.awaitingPearlCooldown = 0;
         } else {
            this.awaitingPearlCooldown--;
         }
      }
   }

   private boolean tryAct(KeyActionModule.Press press) {
      KeyActionModule.Action action = press.action;
      if (mc.field_1755 == null && mc.field_1724 != null && mc.field_1687 != null) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (!snapshot.available()) {
            return press.abandon("inventory snapshot unavailable");
         }

         if (snapshot.menuId() != 0) {
            return press.abandon("container open");
         }

         if (snapshot.deadOrDying()) {
            return press.abandon("dead");
         }

         Result item = InventoryUtil.find(action.item, InventoryUtil.FULL_SCOPE);
         if (!item.found()) {
            return press.abandon("no item");
         }

         switch (action) {
            case PEARL:
               if (this.awaitingPearlCooldown > 0) {
                  return press.abandon("awaiting the last pearl's cooldown packet");
               }

               if (mc.field_1724.method_7357().method_7904(item.stack())) {
                  return press.abandon("pearl cooldown");
               }
               break;
            case FIREWORK:
               if (!mc.field_1724.method_6128()) {
                  return press.abandon("not gliding");
               }
         }

         if (!useItemFits()) {
            return press.waitFor("use-item budget full " + useBudget());
         }

         if (action.rotates) {
            Homovore2Client.rotations().reserveSilent(this);
            if (Homovore2Client.rotations().silentReservedByOther(this)) {
               return press.waitFor("silent rotation held by another module");
            }
         } else {
            Homovore2Client.rotations().releaseSilent(this);
         }

         this.submit(press, snapshot, item);
         return true;
      } else {
         return press.abandon("screen open or not in game");
      }
   }

   private void submit(KeyActionModule.Press press, InventorySnapshot snapshot, Result item) {
      if (this.pending.compareAndSet(false, true)) {
         KeyActionModule.Action action = press.action;
         ActionTrace trace = press.trace;
         trace.attempt("slot=" + item.hand());
         long submission = this.generation.get();
         boolean rotates = action.rotates;
         RotationManager.Angles angles = action == KeyActionModule.Action.PEARL
            ? new RotationManager.Angles(snapshot.yaw(), snapshot.pitch())
            : new RotationManager.Angles(snapshot.yaw(), 90.0F);
         NetworkAction task = context -> {
            if (this.generation.get() == submission) {
               if (!context.limits().canSend(PacketLimiter.Bucket.USE_ITEM, 1, this)) {
                  trace.refused("use-item budget " + useBudget());
               } else if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < (rotates ? 2 : 1)) {
                  trace.refused("global budget");
               } else if (!rotates) {
                  this.use(context, trace, item, snapshot.yaw(), snapshot.pitch());
               } else {
                  try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
                        .beginSilentNow(context, snapshot, angles, snapshot.horizontalCollision(), this)) {
                     if (!rotation.applied()) {
                        trace.refused("silent rotation not applied");
                        return;
                     }

                     this.use(context, trace, item, angles.yaw(), angles.pitch());
                  }

                  return;
               }
            }
         };

         try {
            InventoryUtil.withSwap(item, task).whenComplete((swapped, failure) -> {
               if (failure != null) {
                  trace.refused("swap failed: " + failure);
               } else if (!Boolean.TRUE.equals(swapped)) {
                  trace.refused("swap refused");
               }

               this.pending.set(false);
            });
         } catch (RuntimeException exception) {
            trace.refused("swap threw: " + exception);
            this.pending.set(false);
         }
      }
   }

   private void use(NetworkActionContext context, ActionTrace trace, Result item, float yaw, float pitch) {
      if (context.useItem(item.hand(), yaw, pitch, this)) {
         trace.sent(context.lastUseSequence());
         this.done.set(true);
      } else {
         trace.refused("use-item budget at send " + useBudget());
      }
   }

   private boolean queued(KeyActionModule.Action action) {
      for (KeyActionModule.Press press : this.queue) {
         if (press.action == action) {
            return true;
         }
      }

      return false;
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

   private static boolean isSplash(class_1799 stack) {
      return stack.method_31574(class_1802.field_8436);
   }

   private static boolean splashWith(class_1799 stack, Predicate<Iterable<class_1293>> effects) {
      if (!isSplash(stack)) {
         return false;
      }

      class_1844 contents = (class_1844)stack.method_58694(class_9334.field_49651);
      return contents != null && effects.test(contents.method_57397());
   }

   private static boolean hasEffect(Iterable<class_1293> effects, class_6880<class_1291> effect) {
      for (class_1293 instance : effects) {
         if (instance.method_55654(effect)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isTurtleMaster(class_1799 stack) {
      return splashWith(stack, effects -> hasEffect(effects, class_1294.field_5907) && hasEffect(effects, class_1294.field_5909));
   }

   private static boolean isStrength(class_1799 stack) {
      return splashWith(stack, effects -> hasEffect(effects, class_1294.field_5910));
   }

   private enum Action {
      SPLASH("Splash", "Splashes whichever splash potion is found first at your feet.", KeyActionModule::isSplash, true),
      TURTLE("TurtleMaster", "Splashes a Turtle Master potion at your feet.", KeyActionModule::isTurtleMaster, true),
      STRENGTH("Strength", "Splashes a Strength potion at your feet.", KeyActionModule::isStrength, true),
      PEARL("Pearl", "Throws an ender pearl where you are looking.", stack -> stack.method_31574(class_1802.field_8634), true),
      FIREWORK("Firework", "Fires a rocket while gliding with an elytra.", stack -> stack.method_31574(class_1802.field_8639), false);

      private final String label;
      private final String description;
      private final Predicate<class_1799> item;
      private final boolean rotates;

      Action(String label, String description, Predicate<class_1799> item, boolean rotates) {
         this.label = label;
         this.description = description;
         this.item = item;
         this.rotates = rotates;
      }
   }

   private static final class Press {
      private final KeyActionModule.Action action;
      private final ActionTrace trace;
      private int attemptsLeft = 8;
      private String blockedBy = "";

      private Press(KeyActionModule.Action action, ActionTrace trace) {
         this.action = action;
         this.trace = trace;
      }

      private boolean abandon(String reason) {
         this.blockedBy = reason;
         return false;
      }

      private boolean waitFor(String reason) {
         this.blockedBy = reason;
         this.trace.waiting(reason);
         return true;
      }
   }
}
