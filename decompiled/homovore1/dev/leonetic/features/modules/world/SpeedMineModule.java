package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.AttackBlockEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.InteractionUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.inventory.SwapMode;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_2846;
import net.minecraft.class_2879;
import net.minecraft.class_3532;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_2350.class_2352;
import net.minecraft.class_2846.class_2847;

public class SpeedMineModule extends Module implements MineApi {
   private static final int DECOY_Y_OFFSET = 2000;
   private static final double GRIM_MIN_EYE = 0.4;
   private static final double GRIM_MAX_EYE = 1.62;
   private static final int SECONDARY_TIMEOUT = 10;
   private static final double BREAK_AHEAD_EDGE = 0.05;
   private final Setting<Double> threshold = this.num("Threshold", 0.7, 0.1, 1.0);
   private final Setting<Integer> fudgeTicks = this.num("FudgeTicks", 1, 0, 5);
   private final Setting<Integer> breakDelay = this.num("BreakDelay", 6, 0, 10);
   private final Setting<Boolean> pauseOnEat = this.bool("PauseOnEat", true);
   private final Setting<Boolean> decoy = this.bool("GrimDecoy", true);
   private final Setting<Boolean> doubleBreak = this.bool("DoubleBreak", true);
   private final Setting<Boolean> breakAhead = this.bool("BreakAhead", false);
   private final Setting<Boolean> rebreak = this.bool("Rebreak", true);
   private final Setting<Boolean> swing = this.bool("Swing", true);
   private final Setting<Double> range = this.num("Range", 5.0, 1.0, 7.0);
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 2.0F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 150).setPage("Render");
   private final Setting<Color> sideColor = this.color("SideColor", 255, 255, 255, 40).setPage("Render");
   private final Setting<Color> primaryColor = this.color("PrimaryColor", 255, 180, 255, 60).setPage("Render");
   private class_2338 pos;
   private class_2350 direction;
   private int ticks;
   private double progress;
   private boolean started;
   private boolean finished;
   private class_2338 secondaryPos;
   private int secondaryTicks;
   private boolean secondaryHolding;
   private double secondaryProgress;
   private int stopCooldown;
   private long lastStopMs;
   private double delayBalance;
   private class_2338 rebreakHoldPos;
   private int rebreakHoldTicks;
   private final CopyOnWriteArrayList<SpeedMineModule.MineFinishListener> finishListeners = new CopyOnWriteArrayList<>();

   public void addFinishListener(SpeedMineModule.MineFinishListener l) {
      this.finishListeners.addIfAbsent(l);
   }

   public void removeFinishListener(SpeedMineModule.MineFinishListener l) {
      this.finishListeners.remove(l);
   }

   private void fireFinish(class_2338 target) {
      for (SpeedMineModule.MineFinishListener l : this.finishListeners) {
         l.onMineFinish(target);
      }
   }

   public SpeedMineModule() {
      super("SpeedMine", "Packet mines the clicked block at best-tool speed behind a Grim decoy, then rebreaks it.", Module.Category.WORLD);
      this.breakAhead.setVisibility(v -> this.doubleBreak.getValue());
      this.lineWidth.setVisibility(v -> this.render.getValue());
      this.lineColor.setVisibility(v -> this.render.getValue());
      this.sideColor.setVisibility(v -> this.render.getValue());
      this.primaryColor.setVisibility(v -> this.render.getValue());
   }

   @Override
   public void onDisable() {
      if (!nullCheck() && this.pos != null && this.started && !this.finished) {
         this.abortBreak();
      }

      this.clearSecondary();
      this.clearMine();
      this.rebreakHoldPos = null;
      this.rebreakHoldTicks = 0;
   }

   @Override
   public String getDisplayInfo() {
      String extra = this.secondaryPos != null ? " +1" : "";
      if (this.pos == null) {
         return this.secondaryPos != null ? "+1" : null;
      } else {
         return !this.started
            ? "wait" + extra
            : (this.finished ? "rebreak" : (int)(Math.min(this.progress / this.threshold.getValue(), 1.0) * 100.0) + "%") + extra;
      }
   }

   @Subscribe
   private void onAttackBlock(AttackBlockEvent event) {
      if (!nullCheck()) {
         if (!mc.field_1724.method_68878() && !mc.field_1724.method_7325()) {
            event.cancel();
            if (!this.isMining(event.getPos())) {
               if (InteractionUtil.canBreak(event.getPos(), event.getState())) {
                  class_2338 ahead = this.breakAheadPos(event.getPos());
                  if (ahead != null) {
                     this.startMine(ahead, this.validFace(ahead));
                  }

                  this.startMine(event.getPos(), event.getDirection());
               }
            }
         }
      }
   }

   private class_2338 breakAheadPos(class_2338 target) {
      if (this.breakAhead.getValue() && this.doubleBreak.getValue()) {
         if (this.secondaryPos == null && (this.pos == null || !this.started || this.finished)) {
            class_243 eye = mc.field_1724.method_33571();
            class_243 dir = mc.field_1724.method_5720();
            class_238 box = new class_238(target);
            double tEnter = Double.NEGATIVE_INFINITY;
            double tExit = Double.POSITIVE_INFINITY;
            class_2350 exitFace = null;

            for (class_2351 axis : class_2351.values()) {
               double d = axis.method_10172(dir.field_1352, dir.field_1351, dir.field_1350);
               double o = axis.method_10172(eye.field_1352, eye.field_1351, eye.field_1350);
               double min = axis.method_10172(box.field_1323, box.field_1322, box.field_1321);
               double max = axis.method_10172(box.field_1320, box.field_1325, box.field_1324);
               if (Math.abs(d) < 1.0E-7) {
                  if (o < min || o > max) {
                     return null;
                  }
               } else {
                  double t1 = (min - o) / d;
                  double t2 = (max - o) / d;
                  double near = Math.min(t1, t2);
                  double far = Math.max(t1, t2);
                  if (near > tEnter) {
                     tEnter = near;
                  }

                  if (far < tExit) {
                     tExit = far;
                     exitFace = class_2350.method_10169(axis, d > 0.0 ? class_2352.field_11056 : class_2352.field_11060);
                  }
               }
            }

            if (exitFace != null && !(tEnter > tExit) && !(tExit <= 0.0)) {
               class_243 exit = eye.method_1019(dir.method_1021(tExit));

               for (class_2351 axis : class_2351.values()) {
                  if (axis != exitFace.method_10166()) {
                     double p = axis.method_10172(exit.field_1352, exit.field_1351, exit.field_1350)
                        - axis.method_10173(target.method_10263(), target.method_10264(), target.method_10260());
                     if (p < 0.05 || p > 0.95) {
                        return null;
                     }
                  }
               }

               class_2338 ahead = target.method_10093(exitFace);
               if (!this.inMineRange(ahead)) {
                  return null;
               }

               class_2680 state = mc.field_1687.method_8320(ahead);
               return !state.method_26215() && InteractionUtil.canBreak(ahead, state) ? ahead : null;
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private void startMine(class_2338 target, class_2350 dir) {
      if (this.pos != null && this.started && !this.finished && (!this.doubleBreak.getValue() || this.secondaryPos != null || !this.demote())) {
         this.abortBreak();
      }

      this.pos = target.method_10062();
      this.direction = dir;
      this.ticks = 0;
      this.progress = 0.0;
      this.started = false;
      this.finished = false;
      if (this.stopCooldown == 0 && this.canBegin()) {
         this.begin();
      }
   }

   private boolean canBegin() {
      long delay = System.currentTimeMillis() - this.lastStopMs;
      if (delay >= 275L) {
         return true;
      }

      double cost = (300L - delay) * (this.decoy.getValue() ? 2 : 1);
      return this.delayBalance + cost <= 900.0;
   }

   private void trackStarts(int starts) {
      long delay = System.currentTimeMillis() - this.lastStopMs;

      for (int i = 0; i < starts; i++) {
         if (delay >= 275L) {
            this.delayBalance *= 0.9;
         } else {
            this.delayBalance += 300L - delay;
         }
      }

      this.delayBalance = class_3532.method_15350(this.delayBalance, -1000.0, 1000.0);
   }

   @Override
   public boolean isAvailable() {
      return this.isEnabled() && !nullCheck() && !mc.field_1724.method_68878() && !mc.field_1724.method_7325();
   }

   @Override
   public boolean requestBreak(class_2338 target) {
      if (!this.isAvailable()) {
         return false;
      }

      if (this.isMining(target)) {
         return true;
      }

      if (!this.inMineRange(target)) {
         return false;
      }

      class_2680 state = mc.field_1687.method_8320(target);
      if (!state.method_26215() && InteractionUtil.canBreak(target, state)) {
         if (this.pos != null && !this.finished) {
            if (!this.started) {
               return false;
            }

            if (!this.doubleBreak.getValue() || this.secondaryPos != null) {
               return false;
            }
         }

         this.startMine(target, this.validFace(target));
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean isMining(class_2338 target) {
      return target.equals(this.pos) || target.equals(this.secondaryPos);
   }

   @Override
   public class_2338 getRebreakPos() {
      return this.finished && this.rebreak.getValue() ? this.pos : null;
   }

   @Override
   public boolean hasFreePrimary() {
      return this.pos == null || this.finished;
   }

   @Override
   public boolean hasFreeSecondary() {
      return this.doubleBreak.getValue() && this.secondaryPos == null;
   }

   @Override
   public boolean inMineRange(class_2338 target) {
      return mc.field_1724.method_33571().method_1022(class_243.method_24953(target)) <= this.range.getValue();
   }

   public class_2338 getPrimaryPos() {
      return this.pos;
   }

   public class_2338 getSecondaryPos() {
      return this.secondaryPos;
   }

   public void holdRebreak(class_2338 target, int ticks) {
      this.rebreakHoldPos = target != null ? target.method_10062() : null;
      this.rebreakHoldTicks = target != null ? ticks : 0;
   }

   public void collectMiningPositions(Set<class_2338> out, double minProgress) {
      if (this.pos != null && this.started && (this.finished || this.progress >= minProgress)) {
         out.add(this.pos);
      }

      if (this.secondaryPos != null && !nullCheck()) {
         class_2680 state = mc.field_1687.method_8320(this.secondaryPos);
         if (!state.method_26215()) {
            double delta = InteractionUtil.getBreakDelta(
               mc.field_1724.method_31548().method_5438(this.bestSlot(state, this.secondaryPos)), state, this.secondaryPos
            );
            if (delta > 0.0 && this.secondaryTicks * delta >= minProgress) {
               out.add(this.secondaryPos);
            }
         }
      }
   }

   private boolean demote() {
      class_2680 state = mc.field_1687.method_8320(this.pos);
      if (state.method_26215()) {
         return false;
      }

      if (!this.stopBreak(this.bestSlot(state, this.pos), false)) {
         this.sendAction(class_2847.field_12973, this.pos, this.validFace(this.pos));
      }

      this.secondaryPos = this.pos;
      this.secondaryTicks = this.ticks;
      this.secondaryHolding = false;
      this.secondaryProgress = Math.min(this.progress, 1.0);
      return true;
   }

   private void begin() {
      this.started = true;
      this.trackStarts(this.decoy.getValue() ? 2 : 1);
      if (this.faceMargin(this.direction, this.targetBox(this.pos)) <= 0.0) {
         this.direction = this.validFace(this.pos);
      }

      this.sendAction(class_2847.field_12968, this.pos, this.direction);
      if (this.decoy.getValue()) {
         class_2338 decoyPos = this.pos.method_10087(2000);
         this.sendAction(class_2847.field_12968, decoyPos, this.validFace(decoyPos));
      }

      if (this.swing.getValue()) {
         mc.method_1562().method_52787(new class_2879(class_1268.field_5808));
      }
   }

   @Subscribe
   private void onTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (this.stopCooldown > 0) {
            this.stopCooldown--;
         }

         if (this.rebreakHoldTicks > 0) {
            this.rebreakHoldTicks--;
         }

         this.tickSecondary();
         if (this.pos != null) {
            if (mc.field_1724.method_33571().method_1022(class_243.method_24953(this.pos)) > this.range.getValue()) {
               if (this.started && !this.finished) {
                  this.abortBreak();
               }

               this.clearMine();
            } else {
               class_2680 state = mc.field_1687.method_8320(this.pos);
               if (!this.started) {
                  if (state.method_26215() || !InteractionUtil.canBreak(this.pos, state)) {
                     this.clearMine();
                  } else if (this.stopCooldown <= 0 && this.canBegin()) {
                     this.direction = this.validFace(this.pos);
                     this.begin();
                  }
               } else if (!this.finished) {
                  if (state.method_26215()) {
                     this.clearMine();
                  } else {
                     int slot = this.bestSlot(state, this.pos);
                     double delta = InteractionUtil.getBreakDelta(mc.field_1724.method_31548().method_5438(slot), state, this.pos);
                     if (delta <= 0.0) {
                        this.abortBreak();
                        this.clearMine();
                     } else {
                        this.ticks++;
                        this.progress = Math.max(this.ticks - this.fudgeTicks.getValue(), 0) * delta;
                        if (this.progress >= this.threshold.getValue()) {
                           this.finished = this.stopBreak(slot);
                           if (this.finished) {
                              this.fireFinish(this.pos);
                           }
                        }
                     }
                  }
               } else if (!this.rebreak.getValue()) {
                  this.clearMine();
               } else if (!state.method_26215()) {
                  if (this.rebreakHoldTicks <= 0 || !this.pos.equals(this.rebreakHoldPos)) {
                     this.stopBreak(this.bestSlot(state, this.pos));
                  }
               }
            }
         }
      }
   }

   private void tickSecondary() {
      if (this.secondaryPos != null) {
         class_2680 state = mc.field_1687.method_8320(this.secondaryPos);
         if (state.method_26215()) {
            this.fireFinish(this.secondaryPos);
            this.clearSecondary();
         } else {
            this.secondaryTicks++;
            int slot = this.bestSlot(state, this.secondaryPos);
            double delta = InteractionUtil.getBreakDelta(mc.field_1724.method_31548().method_5438(slot), state, this.secondaryPos);
            if (delta <= 0.0) {
               this.clearSecondary();
            } else {
               int expected = class_3532.method_15384(1.0 / delta) - 1;
               this.secondaryProgress = Math.min(this.secondaryTicks * delta, 1.0);
               if (this.secondaryTicks > expected + 10) {
                  this.clearSecondary();
               } else {
                  if (this.secondaryTicks >= expected - 1 && !this.secondaryHolding) {
                     Result result = new Result(slot, mc.field_1724.method_31548().method_5438(slot), ResultType.HOTBAR);
                     if (!result.holding() && !Homovore.swapManager.isLatched() && Homovore.swapManager.latch(result)) {
                        this.secondaryHolding = true;
                     }
                  }
               }
            }
         }
      }
   }

   private void clearSecondary() {
      if (this.secondaryHolding) {
         Homovore.swapManager.release();
      }

      this.secondaryPos = null;
      this.secondaryTicks = 0;
      this.secondaryHolding = false;
      this.secondaryProgress = 0.0;
   }

   @Subscribe
   private void onRender(Render3DEvent event) {
      if (!nullCheck() && this.render.getValue()) {
         if (this.pos != null && this.started) {
            this.drawBlock(event, this.pos, this.primaryColor.getValue(), this.finished ? 1.0 : Math.min(this.progress / this.threshold.getValue(), 1.0));
         }

         if (this.secondaryPos != null) {
            this.drawBlock(event, this.secondaryPos, this.sideColor.getValue(), this.secondaryProgress);
         }
      }
   }

   private void drawBlock(Render3DEvent event, class_2338 target, Color side, double progress) {
      if (!mc.field_1687.method_8320(target).method_26215()) {
         float t = (float)class_3532.method_15350(progress, 0.0, 1.0);
         double cx = target.method_10263() + 0.5;
         double cy = target.method_10264() + 0.5;
         double cz = target.method_10260() + 0.5;
         double half = 0.5 * t;
         class_238 box = new class_238(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
         RenderUtil.drawBoxFilled(event.getMatrix(), box, side);
         RenderUtil.drawBox(event.getMatrix(), box, this.lineColor.getValue(), this.lineWidth.getValue());
      }
   }

   private boolean stopBreak(int slot) {
      return this.stopBreak(slot, true);
   }

   private boolean stopBreak(int slot, boolean cooldown) {
      if (!Homovore.swapManager.isLatched() || !this.pauseOnEat.getValue() && !this.secondaryHolding) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
         return Homovore.swapManager.withSwap(new Result(slot, stack, ResultType.HOTBAR), SwapMode.SILENT, 50, () -> {
            if (cooldown) {
               this.stopCooldown = this.breakDelay.getValue();
            }

            this.sendAction(class_2847.field_12973, this.pos, this.validFace(this.pos));
            if (this.swing.getValue()) {
               mc.method_1562().method_52787(new class_2879(class_1268.field_5808));
            }
         });
      } else {
         return false;
      }
   }

   private void abortBreak() {
      this.sendAction(class_2847.field_12971, this.pos, class_2350.field_11033);
   }

   private class_2350 validFace(class_2338 target) {
      class_238 box = this.targetBox(target);
      class_2350 best = class_2350.field_11036;
      double bestMargin = -Double.MAX_VALUE;

      for (class_2350 dir : class_2350.values()) {
         double margin = this.faceMargin(dir, box);
         if (margin > bestMargin) {
            bestMargin = margin;
            best = dir;
         }
      }

      return best;
   }

   private double faceMargin(class_2350 dir, class_238 box) {
      class_243 now = mc.field_1724.method_73189();
      class_243 prev = new class_243(mc.field_1724.field_6014, mc.field_1724.field_6036, mc.field_1724.field_5969);
      return Math.min(this.feetMargin(dir, box, now), this.feetMargin(dir, box, prev));
   }

   private double feetMargin(class_2350 dir, class_238 box, class_243 feet) {
      return switch (dir) {
         case field_11036 -> feet.field_1351 + 1.62 - box.field_1325;
         case field_11033 -> box.field_1322 - (feet.field_1351 + 0.4);
         case field_11034 -> feet.field_1352 - box.field_1320;
         case field_11039 -> box.field_1323 - feet.field_1352;
         case field_11035 -> feet.field_1350 - box.field_1324;
         case field_11043 -> box.field_1321 - feet.field_1350;
         default -> throw new MatchException(null, null);
      };
   }

   private class_238 targetBox(class_2338 target) {
      class_265 shape = mc.field_1687.method_8320(target).method_26218(mc.field_1687, target);
      return shape.method_1110() ? new class_238(target) : shape.method_1107().method_996(target);
   }

   private int bestSlot(class_2680 state, class_2338 target) {
      int best = mc.field_1724.method_31548().method_67532();
      double bestDelta = InteractionUtil.getBreakDelta(mc.field_1724.method_31548().method_5438(best), state, target);

      for (int i = 0; i < 9; i++) {
         double delta = InteractionUtil.getBreakDelta(mc.field_1724.method_31548().method_5438(i), state, target);
         if (delta > bestDelta) {
            best = i;
            bestDelta = delta;
         }
      }

      return best;
   }

   private void sendAction(class_2847 action, class_2338 target, class_2350 face) {
      if (action == class_2847.field_12973) {
         this.lastStopMs = System.currentTimeMillis();
      }

      mc.method_1562().method_52787(new class_2846(action, target, face));
   }

   private void clearMine() {
      this.pos = null;
      this.direction = null;
      this.ticks = 0;
      this.progress = 0.0;
      this.started = false;
      this.finished = false;
   }

   public interface MineFinishListener {
      void onMineFinish(class_2338 var1);
   }
}
