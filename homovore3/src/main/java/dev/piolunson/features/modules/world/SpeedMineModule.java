package dev.piolunson.features.modules.world;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.impl.network.AttackBlockEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.InteractionUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.ResultType;
import dev.piolunson.util.inventory.SwapMode;
import dev.piolunson.util.render.RenderUtil;
import java.awt.Color;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.shape.VoxelShape;

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
   private BlockPos pos;
   private Direction direction;
   private int ticks;
   private double progress;
   private boolean started;
   private boolean finished;
   private BlockPos secondaryPos;
   private int secondaryTicks;
   private boolean secondaryHolding;
   private double secondaryProgress;
   private int stopCooldown;
   private long lastStopMs;
   private double delayBalance;
   private BlockPos rebreakHoldPos;
   private int rebreakHoldTicks;
   private final CopyOnWriteArrayList<SpeedMineModule.MineFinishListener> finishListeners = new CopyOnWriteArrayList<>();

   public void addFinishListener(SpeedMineModule.MineFinishListener l) {
      this.finishListeners.addIfAbsent(l);
   }

   public void removeFinishListener(SpeedMineModule.MineFinishListener l) {
      this.finishListeners.remove(l);
   }

   private void fireFinish(BlockPos target) {
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
         if (!mc.player.isCreative() && !mc.player.isSpectator()) {
            event.cancel();
            if (!this.isMining(event.getPos())) {
               if (InteractionUtil.canBreak(event.getPos(), event.getState())) {
                  BlockPos ahead = this.breakAheadPos(event.getPos());
                  if (ahead != null) {
                     this.startMine(ahead, this.validFace(ahead));
                  }

                  this.startMine(event.getPos(), event.getDirection());
               }
            }
         }
      }
   }

   private BlockPos breakAheadPos(BlockPos target) {
      if (this.breakAhead.getValue() && this.doubleBreak.getValue()) {
         if (this.secondaryPos == null && (this.pos == null || !this.started || this.finished)) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d dir = mc.player.getRotationVector();
            Box box = new Box(target);
            double tEnter = Double.NEGATIVE_INFINITY;
            double tExit = Double.POSITIVE_INFINITY;
            Direction exitFace = null;

            for (Axis axis : Axis.values()) {
               double d = axis.choose(dir.x, dir.y, dir.z);
               double o = axis.choose(eye.x, eye.y, eye.z);
               double min = axis.choose(box.minX, box.minY, box.minZ);
               double max = axis.choose(box.maxX, box.maxY, box.maxZ);
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
                     exitFace = Direction.from(axis, d > 0.0 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
                  }
               }
            }

            if (exitFace != null && !(tEnter > tExit) && !(tExit <= 0.0)) {
               Vec3d exit = eye.add(dir.multiply(tExit));

               for (Axis axis : Axis.values()) {
                  if (axis != exitFace.getAxis()) {
                     double p = axis.choose(exit.x, exit.y, exit.z) - axis.choose(target.getX(), target.getY(), target.getZ());
                     if (p < 0.05 || p > 0.95) {
                        return null;
                     }
                  }
               }

               BlockPos ahead = target.offset(exitFace);
               if (!this.inMineRange(ahead)) {
                  return null;
               }

               BlockState state = mc.world.getBlockState(ahead);
               return !state.isAir() && InteractionUtil.canBreak(ahead, state) ? ahead : null;
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

   private void startMine(BlockPos target, Direction dir) {
      if (this.pos != null && this.started && !this.finished && (!this.doubleBreak.getValue() || this.secondaryPos != null || !this.demote())) {
         this.abortBreak();
      }

      this.pos = target.toImmutable();
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

      this.delayBalance = MathHelper.clamp(this.delayBalance, -1000.0, 1000.0);
   }

   @Override
   public boolean isAvailable() {
      return this.isEnabled() && !nullCheck() && !mc.player.isCreative() && !mc.player.isSpectator();
   }

   @Override
   public boolean requestBreak(BlockPos target) {
      if (!this.isAvailable()) {
         return false;
      }

      if (this.isMining(target)) {
         return true;
      }

      if (!this.inMineRange(target)) {
         return false;
      }

      BlockState state = mc.world.getBlockState(target);
      if (!state.isAir() && InteractionUtil.canBreak(target, state)) {
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
   public boolean isMining(BlockPos target) {
      return target.equals(this.pos) || target.equals(this.secondaryPos);
   }

   @Override
   public BlockPos getRebreakPos() {
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
   public boolean inMineRange(BlockPos target) {
      return mc.player.getEyePos().distanceTo(Vec3d.ofCenter(target)) <= this.range.getValue();
   }

   public BlockPos getPrimaryPos() {
      return this.pos;
   }

   public BlockPos getSecondaryPos() {
      return this.secondaryPos;
   }

   public void holdRebreak(BlockPos target, int ticks) {
      this.rebreakHoldPos = target != null ? target.toImmutable() : null;
      this.rebreakHoldTicks = target != null ? ticks : 0;
   }

   public void collectMiningPositions(Set<BlockPos> out, double minProgress) {
      if (this.pos != null && this.started && (this.finished || this.progress >= minProgress)) {
         out.add(this.pos);
      }

      if (this.secondaryPos != null && !nullCheck()) {
         BlockState state = mc.world.getBlockState(this.secondaryPos);
         if (!state.isAir()) {
            double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getStack(this.bestSlot(state, this.secondaryPos)), state, this.secondaryPos);
            if (delta > 0.0 && this.secondaryTicks * delta >= minProgress) {
               out.add(this.secondaryPos);
            }
         }
      }
   }

   private boolean demote() {
      BlockState state = mc.world.getBlockState(this.pos);
      if (state.isAir()) {
         return false;
      }

      if (!this.stopBreak(this.bestSlot(state, this.pos), false)) {
         this.sendAction(Action.STOP_DESTROY_BLOCK, this.pos, this.validFace(this.pos));
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

      this.sendAction(Action.START_DESTROY_BLOCK, this.pos, this.direction);
      if (this.decoy.getValue()) {
         BlockPos decoyPos = this.pos.down(2000);
         this.sendAction(Action.START_DESTROY_BLOCK, decoyPos, this.validFace(decoyPos));
      }

      if (this.swing.getValue()) {
         mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
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
            if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(this.pos)) > this.range.getValue()) {
               if (this.started && !this.finished) {
                  this.abortBreak();
               }

               this.clearMine();
            } else {
               BlockState state = mc.world.getBlockState(this.pos);
               if (!this.started) {
                  if (state.isAir() || !InteractionUtil.canBreak(this.pos, state)) {
                     this.clearMine();
                  } else if (this.stopCooldown <= 0 && this.canBegin()) {
                     this.direction = this.validFace(this.pos);
                     this.begin();
                  }
               } else if (!this.finished) {
                  if (state.isAir()) {
                     this.clearMine();
                  } else {
                     int slot = this.bestSlot(state, this.pos);
                     double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getStack(slot), state, this.pos);
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
               } else if (!state.isAir()) {
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
         BlockState state = mc.world.getBlockState(this.secondaryPos);
         if (state.isAir()) {
            this.fireFinish(this.secondaryPos);
            this.clearSecondary();
         } else {
            this.secondaryTicks++;
            int slot = this.bestSlot(state, this.secondaryPos);
            double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getStack(slot), state, this.secondaryPos);
            if (delta <= 0.0) {
               this.clearSecondary();
            } else {
               int expected = MathHelper.ceil(1.0 / delta) - 1;
               this.secondaryProgress = Math.min(this.secondaryTicks * delta, 1.0);
               if (this.secondaryTicks > expected + 10) {
                  this.clearSecondary();
               } else {
                  if (this.secondaryTicks >= expected - 1 && !this.secondaryHolding) {
                     Result result = new Result(slot, mc.player.getInventory().getStack(slot), ResultType.HOTBAR);
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

   private void drawBlock(Render3DEvent event, BlockPos target, Color side, double progress) {
      if (!mc.world.getBlockState(target).isAir()) {
         float t = (float)MathHelper.clamp(progress, 0.0, 1.0);
         double cx = target.getX() + 0.5;
         double cy = target.getY() + 0.5;
         double cz = target.getZ() + 0.5;
         double half = 0.5 * t;
         Box box = new Box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
         RenderUtil.drawBoxFilled(event.getMatrix(), box, side);
         RenderUtil.drawBox(event.getMatrix(), box, this.lineColor.getValue(), this.lineWidth.getValue());
      }
   }

   private boolean stopBreak(int slot) {
      return this.stopBreak(slot, true);
   }

   private boolean stopBreak(int slot, boolean cooldown) {
      if (!Homovore.swapManager.isLatched() || !this.pauseOnEat.getValue() && !this.secondaryHolding) {
         ItemStack stack = mc.player.getInventory().getStack(slot);
         return Homovore.swapManager.withSwap(new Result(slot, stack, ResultType.HOTBAR), SwapMode.SILENT, 50, () -> {
            if (cooldown) {
               this.stopCooldown = this.breakDelay.getValue();
            }

            this.sendAction(Action.STOP_DESTROY_BLOCK, this.pos, this.validFace(this.pos));
            if (this.swing.getValue()) {
               mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
         });
      } else {
         return false;
      }
   }

   private void abortBreak() {
      this.sendAction(Action.ABORT_DESTROY_BLOCK, this.pos, Direction.DOWN);
   }

   private Direction validFace(BlockPos target) {
      Box box = this.targetBox(target);
      Direction best = Direction.UP;
      double bestMargin = -Double.MAX_VALUE;

      for (Direction dir : Direction.values()) {
         double margin = this.faceMargin(dir, box);
         if (margin > bestMargin) {
            bestMargin = margin;
            best = dir;
         }
      }

      return best;
   }

   private double faceMargin(Direction dir, Box box) {
      Vec3d now = mc.player.getEntityPos();
      Vec3d prev = new Vec3d(mc.player.lastX, mc.player.lastY, mc.player.lastZ);
      return Math.min(this.feetMargin(dir, box, now), this.feetMargin(dir, box, prev));
   }

   private double feetMargin(Direction dir, Box box, Vec3d feet) {
      return switch (dir) {
         case UP -> feet.y + 1.62 - box.maxY;
         case DOWN -> box.minY - (feet.y + 0.4);
         case EAST -> feet.x - box.maxX;
         case WEST -> box.minX - feet.x;
         case SOUTH -> feet.z - box.maxZ;
         case NORTH -> box.minZ - feet.z;
         default -> throw new MatchException(null, null);
      };
   }

   private Box targetBox(BlockPos target) {
      VoxelShape shape = mc.world.getBlockState(target).getOutlineShape(mc.world, target);
      return shape.isEmpty() ? new Box(target) : shape.getBoundingBox().offset(target);
   }

   private int bestSlot(BlockState state, BlockPos target) {
      int best = mc.player.getInventory().getSelectedSlot();
      double bestDelta = InteractionUtil.getBreakDelta(mc.player.getInventory().getStack(best), state, target);

      for (int i = 0; i < 9; i++) {
         double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getStack(i), state, target);
         if (delta > bestDelta) {
            best = i;
            bestDelta = delta;
         }
      }

      return best;
   }

   private void sendAction(Action action, BlockPos target, Direction face) {
      if (action == Action.STOP_DESTROY_BLOCK) {
         this.lastStopMs = System.currentTimeMillis();
      }

      mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, target, face));
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
      void onMineFinish(BlockPos var1);
   }
}
