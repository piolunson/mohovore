package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2;
import dev.leonetic.event.impl.BlockAttackEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapManager;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.network.NetworkActionContext;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.class_12178;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_1268;
import net.minecraft.class_1292;
import net.minecraft.class_1294;
import net.minecraft.class_1304;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_1890;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3486;
import net.minecraft.class_4184;
import net.minecraft.class_5134;
import net.minecraft.class_1322.class_1323;
import net.minecraft.class_2846.class_2847;

public final class DoubleMineModule extends Module {
   private static final int DECOY_Y_OFFSET = 2000;
   private static final int STOP_RETRY_TICKS = 10;
   private static final int VANILLA_FAST_BREAK_TICKS = 5;
   private static final float VANILLA_SOFT_BLOCK_HARDNESS = 1.0F;
   private static final int SECONDARY_HOLD_TICKS = 2;
   private static final int SECONDARY_TIMEOUT_TICKS = 20;
   private static final double START_BALANCE_LIMIT = 900.0;
   private static final int GRIM_BREAK_STARTS = 2;
   private static final double STOP_MARGIN_TICKS = 1.0;
   public final Setting<Double> threshold = this.number("Threshold", (double)0.7, (double)0.1, (double)1.0)
      .describe("Fraction of predicted mining progress before sending the finish packet.");
   public final Setting<Double> range = this.number("Range", (double)5.0, (double)1.0, (double)6.0)
      .describe("Maximum mining range. The player's current interaction range is still enforced when it is lower.");
   public final Setting<Integer> breakDelay = this.integer("BreakDelay", 6, 0, 10)
      .describe("Client ticks a finish packet holds back the next block's start packet, so the two never share a server tick.");
   public final Setting<Boolean> rebreak = this.bool("Rebreak", true)
      .describe("Keeps the finished primary selected and breaks any block placed back into it with a silent-swap STOP every tick.");
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<DoubleMineModule.Animation> animation = this.mode("Animation", DoubleMineModule.Animation.IN)
      .visibleWhen(this.render::getValue)
      .page("Render")
      .describe("Out grows from the centre; In shrinks into the centre.");
   public final Setting<Color> sideColor = this.color("SideColor", 58, 58, 58, 0).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 0, 0, 255).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)5.0, (double)0.5, (double)5.0).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Color> outlineColor = this.color("OutlineColor", 255, 255, 255, 200)
      .visibleWhen(this.render::getValue)
      .page("Render")
      .describe("Full-block outline around the primary, which stays while it is rebroken.");
   private final AtomicBoolean actionPending = new AtomicBoolean();
   private final AtomicLong generation = new AtomicLong();
   private final AtomicReference<DoubleMineModule.Completion> completion = new AtomicReference<>();
   private final AtomicLong secondaryGeneration = new AtomicLong();
   private final AtomicBoolean secondarySwapPending = new AtomicBoolean();
   private final AtomicReference<SwapManager.MainhandLease> secondaryLease = new AtomicReference<>();
   private class_2338 target;
   private class_2350 direction;
   private class_2248 targetBlock;
   private float breakDelta;
   private float shownProgress;
   private long startedAtNanos;
   private int ticks;
   private int stopWaitTicks;
   private int stopCooldown;
   private boolean started;
   private boolean stopSent;
   private boolean instant;
   private boolean finishNotified;
   private boolean primaryIsRebreak;
   private int rebreakDelayTicks;
   private int rebreakWaitTicks;
   private boolean rebreakSawAir;
   private class_2338 secondary;
   private class_2248 secondaryBlock;
   private float secondaryDelta;
   private int secondaryTicks;
   private int secondaryHoldTicks;
   private boolean secondaryToolPulsed;
   private DoubleMineModule.PendingSelection pending;
   private final List<Consumer<class_2338>> finishListeners = new CopyOnWriteArrayList<>();
   private final List<Function<class_2338, NetworkAction>> finishActionProviders = new CopyOnWriteArrayList<>();

   public DoubleMineModule() {
      super("DoubleMine", Category.COMBAT, "Packet-mines two blocks at once: a primary, and a secondary the server finishes.");
   }

   public boolean isAvailable() {
      return this.isEnabled() && mc.field_1724 != null && mc.field_1687 != null && NetworkThreadExecutor.isConnected();
   }

   public boolean requestBreak(class_2338 pos) {
      return this.requestBreak(pos, 0);
   }

   public boolean requestBreak(class_2338 pos, int replacementDelayTicks) {
      if (!this.isAvailable()) {
         return false;
      } else {
         int delay = Math.max(0, replacementDelayTicks);
         if (pos.equals(this.target)) {
            this.rebreakDelayTicks = delay;
            return true;
         } else if (this.pending != null && pos.equals(this.pending.pos)) {
            this.pending = new DoubleMineModule.PendingSelection(this.pending.pos, this.pending.face, delay, this.pending.manual);
            return true;
         } else if (pos.equals(this.secondary)) {
            return true;
         } else if (this.target != null && !this.started) {
            return false;
         } else {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (!this.withinRange(snapshot, pos)) {
               return false;
            } else {
               class_2680 state = mc.field_1687.method_8320(pos);
               if (!state.method_26215() && !(state.method_26214(mc.field_1687, pos) < 0.0F)) {
                  class_2350 face = legalFace(snapshot, pos, null);
                  this.select(pos, face != null ? face : class_2350.field_11036, delay, false);
                  return this.isMining(pos);
               } else {
                  return false;
               }
            }
         }
      }
   }

   public boolean requestBreakPair(class_2338 delayedPos, class_2338 rebreakTarget, int replacementDelayTicks) {
      if (this.isAvailable() && !delayedPos.equals(rebreakTarget)) {
         class_2338 delayed = this.delayedDestroyPos();
         if (delayed != null && !delayed.equals(delayedPos)) {
            return false;
         }

         class_2338 held = this.rebreakPos();
         if (held != null && !held.equals(rebreakTarget)) {
            this.clearRebreak();
         }

         this.requestBreak(delayedPos, 0);
         if (delayedPos.equals(this.delayedDestroyPos())) {
            this.requestBreak(rebreakTarget, replacementDelayTicks);
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean isMining(class_2338 pos) {
      return pos.equals(this.target) || pos.equals(this.secondary) || this.pending != null && pos.equals(this.pending.pos);
   }

   public boolean inMineRange(class_2338 pos) {
      return mc.field_1724 != null && this.withinRange(InventoryUtil.snapshot(), pos);
   }

   public class_2338 delayedDestroyPos() {
      if (this.secondary != null) {
         return this.secondary;
      } else {
         return this.target != null && !this.primaryIsRebreak ? this.target : null;
      }
   }

   public boolean hasDelayedDestroy() {
      return this.delayedDestroyPos() != null;
   }

   public class_2338 rebreakPos() {
      return this.primaryIsRebreak && this.rebreak.getValue() ? this.target : null;
   }

   public boolean hasRebreakBlock() {
      return this.rebreakPos() != null && (!this.stopSent || !this.rebreakSawAir);
   }

   public boolean canRebreak() {
      return this.rebreakPos() != null && this.stopSent && this.rebreakSawAir;
   }

   public void clearRebreak() {
      if (this.rebreakPos() != null) {
         if (!this.stopSent && !this.instant) {
            this.abortAndClearTarget();
         } else {
            this.clearTarget(true);
         }
      }
   }

   public void holdRebreak(class_2338 pos, int ticks) {
      if (pos != null && pos.equals(this.target) && this.stopSent) {
         this.rebreakSawAir = true;
         this.rebreakWaitTicks = Math.max(this.rebreakWaitTicks, Math.max(0, ticks));
      }
   }

   public void addFinishListener(Consumer<class_2338> listener) {
      if (!this.finishListeners.contains(listener)) {
         this.finishListeners.add(listener);
      }
   }

   public void removeFinishListener(Consumer<class_2338> listener) {
      this.finishListeners.remove(listener);
   }

   public void addFinishActionProvider(Function<class_2338, NetworkAction> provider) {
      if (!this.finishActionProviders.contains(provider)) {
         this.finishActionProviders.add(provider);
      }
   }

   public void removeFinishActionProvider(Function<class_2338, NetworkAction> provider) {
      this.finishActionProviders.remove(provider);
   }

   private void notifyFinish(class_2338 pos) {
      for (Consumer<class_2338> listener : this.finishListeners) {
         listener.accept(pos);
      }
   }

   private List<NetworkAction> prepareFinishActions(class_2338 pos) {
      if (this.finishActionProviders.isEmpty()) {
         return List.of();
      }

      List<NetworkAction> actions = new ArrayList<>(this.finishActionProviders.size());

      for (Function<class_2338, NetworkAction> provider : this.finishActionProviders) {
         NetworkAction action = provider.apply(pos);
         if (action != null) {
            actions.add(action);
         }
      }

      return List.copyOf(actions);
   }

   private static void runFinishActions(NetworkActionContext context, List<NetworkAction> actions) {
      for (NetworkAction action : actions) {
         try {
            action.run(context);
         } catch (Exception exception) {
            Homovore2.LOGGER.warn("Mine finish follow-up failed", exception);
         }
      }
   }

   @Subscribe
   public void onBlockAttack(BlockAttackEvent event) {
      if ((this.target == null || this.stopSent && this.rebreak.getValue() && !event.pos().equals(this.target)) && this.shouldUseVanillaMining(event.pos())) {
         if (this.target != null) {
            this.clearTarget(true);
         }
      } else {
         event.cancel();
         if (event.initial()) {
            this.select(event.pos(), event.direction(), 0, true);
         }
      }
   }

   @Override
   public void onTick() {
      this.applyCompletion();
      if (mc.field_1724 != null && mc.field_1687 != null && NetworkThreadExecutor.isConnected()) {
         if (this.stopCooldown > 0) {
            this.stopCooldown--;
         }

         this.tickSecondary();
         this.tickPending();
         if (this.target != null) {
            class_2680 state = mc.field_1687.method_8320(this.target);
            if (this.stopSent && this.rebreak.getValue()) {
               this.tickRebreak(state);
            } else if (!state.method_26215() && state.method_26204() == this.targetBlock) {
               InventorySnapshot positionSnapshot = InventoryUtil.snapshot();
               if (!this.withinRange(positionSnapshot, this.target)) {
                  this.abortAndClearTarget();
               } else if (this.started) {
                  if (!this.stopSent) {
                     this.ticks++;
                  }

                  if (!this.actionPending.get() && !this.secondarySwapBusy() && !this.instant) {
                     if (!this.stopSent) {
                        DoubleMineModule.ToolChoice tool = this.bestTool(positionSnapshot, state, state.method_26214(mc.field_1687, this.target), 36);
                        this.breakDelta = tool != null ? tool.progress : 0.0F;
                        this.updateBreakingTexture();
                        if (this.rawProgress() >= this.threshold.getValue()) {
                           class_2350 finishFace = legalFace(positionSnapshot, this.target, this.direction);
                           if (finishFace != null) {
                              this.submitStop(state, finishFace);
                           }
                        }
                     } else if (++this.stopWaitTicks >= 10) {
                        this.stopSent = false;
                        this.stopWaitTicks = 0;
                        InventorySnapshot snapshot = InventoryUtil.snapshot();
                        class_2350 finishFace = legalFace(snapshot, this.target, this.direction);
                        if (this.withinRange(snapshot, this.target) && finishFace != null) {
                           this.submitStop(state, finishFace);
                        }
                     }
                  }
               }
            } else {
               this.clearTarget(true);
            }
         }
      } else {
         this.clearTarget(false);
         this.clearSecondary();
         this.pending = null;
      }
   }

   private void tickRebreak(class_2680 state) {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!this.withinRange(snapshot, this.target)) {
         this.clearTarget(true);
      } else if (state.method_26215()) {
         if (!this.primaryIsRebreak) {
            this.clearTarget(true);
         } else {
            this.finishNotified = false;
            this.rebreakSawAir = true;
            this.rebreakWaitTicks = Math.max(this.rebreakWaitTicks, this.rebreakDelayTicks);
         }
      } else if (this.started
         && this.pending == null
         && !this.actionPending.get()
         && !this.secondarySwapBusy()
         && !(state.method_26214(mc.field_1687, this.target) < 0.0F)) {
         if (this.rebreakSawAir && this.rebreakWaitTicks > 0) {
            this.rebreakWaitTicks--;
         } else {
            class_2350 face = legalFace(snapshot, this.target, this.direction);
            if (face != null) {
               if (this.instant) {
                  if (this.stopCooldown == 0 && startFits()) {
                     this.start(this.target, face, this.rebreakDelayTicks, this.primaryIsRebreak);
                  }
               } else {
                  this.submitStop(state, face);
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (this.render.getValue()) {
         if (this.target != null) {
            if (!this.stopSent || mc.field_1687 == null || !mc.field_1687.method_8320(this.target).method_26215()) {
               this.renderBox(this.target, this.miningProgress());
            }

            this.renderOutline(this.target);
         }

         if (this.pending != null) {
            this.renderOutline(this.pending.pos);
         }

         if (this.secondary != null) {
            this.renderBox(this.secondary, Math.clamp(this.secondaryProgress(), 0.0F, 1.0F));
         }
      }
   }

   private void renderOutline(class_2338 pos) {
      class_12178 outline = class_12180.method_75542(
         new class_238(pos), class_12179.method_75536(this.outlineColor.getValue().getRGB(), this.lineWidth.getValue().floatValue()), false
      );
      if (this.throughWalls.getValue()) {
         outline.method_75533();
      }
   }

   private void renderBox(class_2338 pos, float progress) {
      class_238 block = new class_238(pos);
      double centerX = (block.field_1323 + block.field_1320) * 0.5;
      double centerY = (block.field_1322 + block.field_1325) * 0.5;
      double centerZ = (block.field_1321 + block.field_1324) * 0.5;
      class_238 center = new class_238(centerX, centerY, centerZ, centerX, centerY, centerZ);
      class_238 box = this.animation.getValue() == DoubleMineModule.Animation.OUT ? lerpBox(center, block, progress) : lerpBox(block, center, progress);
      class_12179 style = class_12179.method_75537(
         this.lineColor.getValue().getRGB(), this.lineWidth.getValue().floatValue(), this.sideColor.getValue().getRGB()
      );
      class_12178 gizmo = class_12180.method_75542(box, style, false);
      if (this.throughWalls.getValue()) {
         gizmo.method_75533();
      }
   }

   @Override
   public void onDisable() {
      this.generation.incrementAndGet();
      class_2338 oldTarget = this.target;
      class_2350 oldDirection = this.direction;
      boolean oldUnfinished = !this.instant && !this.stopSent;
      this.clearTarget(false);
      this.clearSecondary();
      this.pending = null;
      this.stopCooldown = 0;
      this.completion.set(null);
      this.actionPending.set(false);
      if (oldTarget != null && oldDirection != null && oldUnfinished && NetworkThreadExecutor.isConnected()) {
         NetworkThreadExecutor.execute(context -> context.playerAction(class_2847.field_12971, oldTarget, class_2350.field_11033));
      }
   }

   private void select(class_2338 pos, class_2350 face, int replacementDelayTicks, boolean manual) {
      if (!this.actionPending.get()
         && !pos.equals(this.secondary)
         && (this.pending == null || pos.equals(this.pending.pos))
         && (this.target == null || this.started || pos.equals(this.target))) {
         if (this.canDemote(pos)) {
            this.submitDemote(pos.method_10062(), face, replacementDelayTicks, manual);
         } else if (!this.secondarySwapBusy() && this.stopCooldown <= 0 && startFits()) {
            this.start(pos, face, replacementDelayTicks, manual);
         } else {
            this.pending = new DoubleMineModule.PendingSelection(pos.method_10062(), face, replacementDelayTicks, manual);
         }
      }
   }

   private void start(class_2338 pos, class_2350 face, int replacementDelayTicks, boolean manual) {
      if (!this.actionPending.get()
         && mc.field_1724 != null
         && mc.field_1687 != null
         && !mc.field_1724.method_7325()
         && !mc.field_1724.method_3144()
         && NetworkThreadExecutor.isConnected()) {
         class_2680 state = mc.field_1687.method_8320(pos);
         float hardness = state.method_26214(mc.field_1687, pos);
         if (!state.method_26215() && !(hardness < 0.0F)) {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (snapshot.available()
               && snapshot.menuId() == 0
               && !snapshot.deadOrDying()
               && snapshot.playerId() == mc.field_1724.method_5628()
               && (!snapshot.usingItem() || !snapshot.usingMainHand())
               && this.withinRange(snapshot, pos)) {
               class_2350 startFace = legalFace(snapshot, pos, face);
               if (startFace != null) {
                  DoubleMineModule.ToolChoice tool = this.bestTool(snapshot, state, hardness, 36);
                  if (tool != null && !(tool.progress <= 0.0F)) {
                     class_2338 immutablePos = pos.method_10062();
                     class_2350 immutableFace = startFace;
                     class_2338 oldTarget = this.target;
                     class_2350 oldDirection = this.direction;
                     boolean oldUnfinished = !this.instant && !this.stopSent;
                     boolean instantBreak = tool.progress >= 1.0F;
                     boolean rebreakHalf = manual || this.secondary != null || this.target != null && this.started && !this.rebreakSawAir;
                     List<NetworkAction> finishActions = instantBreak && !this.finishNotified ? this.prepareFinishActions(immutablePos) : List.of();
                     long selection = this.generation.incrementAndGet();
                     if (this.actionPending.compareAndSet(false, true)) {
                        this.clearTexture();
                        this.target = immutablePos;
                        this.direction = immutableFace;
                        this.targetBlock = state.method_26204();
                        this.breakDelta = tool.progress;
                        this.shownProgress = 0.0F;
                        this.startedAtNanos = 0L;
                        this.ticks = 0;
                        this.stopWaitTicks = 0;
                        this.started = false;
                        this.stopSent = false;
                        this.instant = instantBreak;
                        this.primaryIsRebreak = rebreakHalf;
                        this.rebreakDelayTicks = Math.max(0, replacementDelayTicks);
                        this.rebreakWaitTicks = 0;
                        this.rebreakSawAir = false;
                        class_2338 decoy = immutablePos.method_10087(2000);
                        boolean[] sent = new boolean[]{false};
                        long[] sentAtNanos = new long[]{0L};
                        NetworkAction action = context -> {
                           if (this.generation.get() == selection) {
                              boolean abortOld = oldTarget != null && oldDirection != null && oldUnfinished && !oldTarget.equals(immutablePos);
                              int cost = (instantBreak ? 2 : 3) + (abortOld ? 1 : 0);
                              if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= cost
                                 && (!abortOld || context.playerAction(class_2847.field_12971, oldTarget, class_2350.field_11033))) {
                                 long startNanos = System.nanoTime();
                                 sent[0] = instantBreak
                                    ? context.startInstantBreak(immutablePos, immutableFace, true)
                                    : context.startGrimBreak(immutablePos, immutableFace, decoy, class_2350.field_11036, true);
                                 if (sent[0]) {
                                    sentAtNanos[0] = startNanos;
                                    runFinishActions(context, finishActions);
                                 }
                              }
                           }
                        };
                        this.submitWithTool(tool.result, action).whenComplete((swapped, failure) -> {
                           boolean applied = failure == null && Boolean.TRUE.equals(swapped) && sent[0];
                           this.completion.set(new DoubleMineModule.Completion(selection, DoubleMineModule.Phase.START, applied, sentAtNanos[0]));
                           this.actionPending.set(false);
                        });
                     }
                  }
               }
            }
         }
      }
   }

   private void submitStop(class_2680 state, class_2350 finishFace) {
      if (this.target != null && this.direction != null && this.actionPending.compareAndSet(false, true)) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         float hardness = state.method_26214(mc.field_1687, this.target);
         DoubleMineModule.ToolChoice tool = this.bestTool(snapshot, state, hardness, 36);
         if (tool == null) {
            this.actionPending.set(false);
         } else {
            long submission = this.generation.get();
            class_2338 immutableTarget = this.target;
            class_2350 immutableDirection = finishFace;
            List<NetworkAction> finishActions = this.finishNotified ? List.of() : this.prepareFinishActions(immutableTarget);
            boolean[] sent = new boolean[]{false};
            NetworkAction action = context -> {
               if (this.generation.get() == submission
                  && context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= 2
                  && context.playerAction(class_2847.field_12973, immutableTarget, immutableDirection)) {
                  sent[0] = context.swing(class_1268.field_5808);
                  if (sent[0]) {
                     runFinishActions(context, finishActions);
                  }
               }
            };
            this.submitWithTool(tool.result, action).whenComplete((swapped, failure) -> {
               boolean applied = failure == null && Boolean.TRUE.equals(swapped) && sent[0];
               this.completion.set(new DoubleMineModule.Completion(submission, DoubleMineModule.Phase.STOP, applied, 0L));
               this.actionPending.set(false);
            });
         }
      }
   }

   private static boolean startFits() {
      PacketLimiter limiter = NetworkThreadExecutor.limiter();
      return limiter == null || limiter.startDelayFits(2, 900.0);
   }

   private boolean canDemote(class_2338 pos) {
      return this.target != null && this.secondary == null && this.started && !this.stopSent && !this.instant && !this.target.equals(pos);
   }

   private void tickPending() {
      if (this.pending != null) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (snapshot.available() && !this.withinRange(snapshot, this.pending.pos)) {
            this.pending = null;
         } else if (!this.actionPending.get() && !this.secondarySwapBusy()) {
            this.applyCompletion();
            if (this.stopCooldown <= 0 && startFits()) {
               DoubleMineModule.PendingSelection next = this.pending;
               this.pending = null;
               if (!next.pos.equals(this.secondary)) {
                  this.start(next.pos, next.face, next.replacementDelayTicks, next.manual);
               }
            }
         }
      }
   }

   private void submitDemote(class_2338 next, class_2350 face, int replacementDelayTicks, boolean manual) {
      this.pending = new DoubleMineModule.PendingSelection(next, face, replacementDelayTicks, manual);
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      class_2350 stopFace = legalFace(snapshot, this.target, this.direction);
      if (stopFace != null && this.actionPending.compareAndSet(false, true)) {
         long submission = this.generation.get();
         class_2338 immutableTarget = this.target;
         boolean[] sent = new boolean[]{false};
         NetworkAction action = context -> {
            if (this.generation.get() == submission
               && context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= 2
               && context.playerAction(class_2847.field_12973, immutableTarget, stopFace)) {
               sent[0] = context.swing(class_1268.field_5808);
            }
         };
         this.submitWithTool(null, action).whenComplete((swapped, failure) -> {
            boolean applied = failure == null && Boolean.TRUE.equals(swapped) && sent[0];
            this.completion.set(new DoubleMineModule.Completion(submission, DoubleMineModule.Phase.DEMOTE, applied, 0L));
            this.actionPending.set(false);
         });
      }
   }

   private void demoteTarget() {
      this.clearTexture();
      this.secondary = this.target;
      this.secondaryBlock = this.targetBlock;
      this.secondaryTicks = this.ticks;
      this.secondaryDelta = this.breakDelta;
      this.secondaryHoldTicks = 0;
      this.secondaryToolPulsed = false;
      this.secondaryGeneration.incrementAndGet();
      this.clearTarget(true);
   }

   private void tickSecondary() {
      if (this.secondary == null) {
         this.releaseSecondaryTool();
      } else {
         boolean swapInFlight = this.secondarySwapPending.get();
         if (this.secondaryLease.get() != null) {
            if (this.secondaryHoldTicks <= 0) {
               this.secondaryHoldTicks = 2;
            }

            if (--this.secondaryHoldTicks > 0) {
               return;
            }

            this.releaseSecondaryTool();
         }

         class_2680 state = mc.field_1687.method_8320(this.secondary);
         if (state.method_26215() || state.method_26204() != this.secondaryBlock) {
            class_2338 broken = this.secondary;
            this.clearSecondary();
            if (state.method_26215()) {
               this.notifyFinish(broken);
            }
         } else if (!swapInFlight) {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (snapshot.available()) {
               float hardness = state.method_26214(mc.field_1687, this.secondary);
               DoubleMineModule.ToolChoice tool = this.bestTool(snapshot, state, hardness, class_1661.method_7368());
               if (tool != null && !(tool.progress <= 0.0F)) {
                  this.secondaryDelta = tool.progress;
                  this.secondaryTicks++;
                  this.updateSecondaryTexture();
                  int expected = (int)Math.ceil(1.0F / tool.progress) - 1;
                  if (this.secondaryTicks > expected + 20) {
                     this.clearSecondary();
                  } else if (this.secondaryTicks >= expected - 1 && !this.secondaryToolPulsed) {
                     float held = this.destroyProgress(snapshot.item(snapshot.selectedSlot()), state, hardness);
                     if (tool.result == null || held >= tool.progress) {
                        this.secondaryToolPulsed = true;
                     } else if (!this.actionPending.get() && snapshot.menuId() == 0 && !snapshot.usingItem()) {
                        this.submitSecondarySwap(tool.result);
                     }
                  }
               } else {
                  this.clearSecondary();
               }
            }
         }
      }
   }

   private void submitSecondarySwap(Result tool) {
      if (this.secondarySwapPending.compareAndSet(false, true)) {
         this.secondaryToolPulsed = true;
         long owner = this.secondaryGeneration.get();
         InventoryUtil.leaseMainhand(tool).whenComplete((lease, failure) -> {
            if (lease != null) {
               this.secondaryLease.set(lease);
               if (this.secondaryGeneration.get() != owner) {
                  SwapManager.MainhandLease stale = this.secondaryLease.getAndSet(null);
                  if (stale != null) {
                     stale.close();
                  }
               }
            }

            this.secondarySwapPending.set(false);
         });
      }
   }

   private boolean secondarySwapBusy() {
      return this.secondarySwapPending.get() || this.secondaryLease.get() != null;
   }

   private void releaseSecondaryTool() {
      SwapManager.MainhandLease lease = this.secondaryLease.getAndSet(null);
      if (lease != null) {
         lease.close();
      }

      this.secondaryHoldTicks = 0;
   }

   private CompletableFuture<Boolean> submitWithTool(Result tool, NetworkAction action) {
      return tool != null && tool.found() ? InventoryUtil.withSwap(tool, action) : NetworkThreadExecutor.execute(action).thenApply(ignored -> true);
   }

   private boolean shouldUseVanillaMining(class_2338 pos) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_2680 state = mc.field_1687.method_8320(pos);
         float hardness = state.method_26214(mc.field_1687, pos);
         if (state.method_26215() || hardness < 0.0F) {
            return false;
         }

         if (hardness <= 1.0F) {
            return true;
         }

         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (!snapshot.available()) {
            return false;
         }

         class_1799 held = snapshot.item(snapshot.selectedSlot());
         float progress = this.destroyProgress(held, state, hardness);
         if (progress <= 0.0F) {
            return false;
         }

         int predictedTicks = (int)Math.ceil(1.0 / progress);
         return predictedTicks <= 5;
      } else {
         return false;
      }
   }

   private DoubleMineModule.ToolChoice bestTool(InventorySnapshot snapshot, class_2680 state, float hardness, int slots) {
      if (snapshot.available() && !(hardness < 0.0F)) {
         DoubleMineModule.ToolChoice best = null;
         int size = Math.min(slots, snapshot.size());

         for (int slot = 0; slot < size; slot++) {
            class_1799 stack = snapshot.item(slot);
            float progress = this.destroyProgress(stack, state, hardness);
            if (best == null || progress > best.progress) {
               Result result = stack.method_7960() ? null : InventoryUtil.resultAt(snapshot, slot);
               best = new DoubleMineModule.ToolChoice(result, progress);
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private boolean withinRange(InventorySnapshot snapshot, class_2338 pos) {
      if (snapshot.available() && mc.field_1724 != null) {
         double allowed = Math.min(this.range.getValue(), mc.field_1724.method_55754());
         double allowedSqr = allowed * allowed;
         class_238 box = new class_238(pos);
         class_243 eye = snapshot.eyePosition();
         double x = Math.clamp(eye.field_1352, box.field_1323, box.field_1320);
         double y = Math.clamp(eye.field_1351, box.field_1322, box.field_1325);
         double z = Math.clamp(eye.field_1350, box.field_1321, box.field_1324);
         return eye.method_1028(x, y, z) <= allowedSqr;
      } else {
         return false;
      }
   }

   private static class_2350 legalFace(InventorySnapshot snapshot, class_2338 pos, class_2350 preferred) {
      if (!snapshot.available()) {
         return null;
      }

      double x = snapshot.x();
      double z = snapshot.z();
      double minEyeY = snapshot.eyeY();
      double maxEyeY = snapshot.eyeY();
      double minX = pos.method_10263();
      double minY = pos.method_10264();
      double minZ = pos.method_10260();
      double maxX = minX + 1.0;
      double maxY = minY + 1.0;
      double maxZ = minZ + 1.0;
      if (x >= minX && x <= maxX && z >= minZ && z <= maxZ && maxEyeY >= minY && minEyeY <= maxY) {
         return preferred;
      }

      if (facePossible(preferred, x, minEyeY, maxEyeY, z, minX, minY, minZ, maxX, maxY, maxZ)) {
         return preferred;
      }

      class_2350 best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (class_2350 candidate : class_2350.values()) {
         if (facePossible(candidate, x, minEyeY, maxEyeY, z, minX, minY, minZ, maxX, maxY, maxZ)) {
            double distance = switch (candidate) {
               case field_11039 -> minX - x;
               case field_11034 -> x - maxX;
               case field_11043 -> minZ - z;
               case field_11035 -> z - maxZ;
               case field_11033 -> minY - minEyeY;
               case field_11036 -> maxEyeY - maxY;
               default -> throw new MatchException(null, null);
            };
            if (distance < bestDistance) {
               best = candidate;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private static boolean facePossible(
      class_2350 face, double x, double minEyeY, double maxEyeY, double z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ
   ) {
      if (face == null) {
         return false;
      }

      return switch (face) {
         case field_11039 -> x <= minX;
         case field_11034 -> x >= maxX;
         case field_11043 -> z <= minZ;
         case field_11035 -> z >= maxZ;
         case field_11033 -> minEyeY <= minY;
         case field_11036 -> maxEyeY >= maxY;
         default -> throw new MatchException(null, null);
      };
   }

   private float destroyProgress(class_1799 stack, class_2680 state, float hardness) {
      if (hardness < 0.0F) {
         return 0.0F;
      }

      float speed = stack.method_7924(state);
      if (speed > 1.0F) {
         speed += (float)miningEfficiency(stack);
      }

      if (class_1292.method_5576(mc.field_1724)) {
         speed *= 1.0F + (class_1292.method_5575(mc.field_1724) + 1) * 0.2F;
      }

      if (mc.field_1724.method_6059(class_1294.field_5901)) {
         int amplifier = mc.field_1724.method_6112(class_1294.field_5901).method_5578();

         speed *= switch (amplifier) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      speed *= (float)mc.field_1724.method_45325(class_5134.field_49076);
      if (mc.field_1724.method_5777(class_3486.field_15517)) {
         speed *= (float)mc.field_1724.method_45325(class_5134.field_51576);
      }

      if (!mc.field_1724.method_24828()) {
         speed /= 5.0F;
      }

      boolean correctTool = !state.method_29291() || stack.method_7951(state);
      return speed / hardness / (correctTool ? 30.0F : 100.0F);
   }

   private static double miningEfficiency(class_1799 stack) {
      double[] total = new double[]{0.0};
      class_1890.method_60140(stack, class_1304.field_6173, (attribute, modifier) -> {
         if (class_5134.field_51581.equals(attribute) && modifier.comp_2450() == class_1323.field_6328) {
            total[0] += modifier.comp_2449();
         }
      });
      return total[0];
   }

   private void applyCompletion() {
      DoubleMineModule.Completion applied = this.completion.getAndSet(null);
      if (applied != null && applied.generation == this.generation.get() && this.target != null) {
         if (!applied.sent) {
            if (applied.phase == DoubleMineModule.Phase.START) {
               this.clearTarget(false);
            }
         } else {
            if (applied.phase == DoubleMineModule.Phase.DEMOTE) {
               this.stopCooldown = this.breakDelay.getValue();
               if (mc.field_1724 != null) {
                  mc.field_1724.method_23667(class_1268.field_5808, false);
               }

               this.demoteTarget();
            } else if (applied.phase == DoubleMineModule.Phase.START) {
               this.startedAtNanos = applied.sentAtNanos;
               this.started = true;
               if (this.instant) {
                  this.stopSent = true;
               }

               if (this.secondary != null) {
                  Homovore2.LOGGER.debug("Double Mine roles: delayed={}, rebreak={}", this.secondary, this.target);
               }

               if (mc.field_1724 != null) {
                  mc.field_1724.method_23667(class_1268.field_5808, false);
               }
            } else {
               this.stopSent = true;
               this.stopWaitTicks = 0;
               this.stopCooldown = this.breakDelay.getValue();
               if (mc.field_1724 != null) {
                  mc.field_1724.method_23667(class_1268.field_5808, false);
               }
            }

            if (this.stopSent && !this.finishNotified) {
               this.finishNotified = true;
               this.notifyFinish(this.target);
            }
         }
      }
   }

   private void updateBreakingTexture() {
      if (mc.field_1687 != null && mc.field_1724 != null && !(this.breakDelta <= 0.0F)) {
         int stage = Math.min(9, (int)(this.miningProgress() * 10.0F));
         mc.field_1687.method_8517(mc.field_1724.method_5628(), this.target, stage);
      }
   }

   private void updateSecondaryTexture() {
      if (mc.field_1724 != null && !(this.secondaryDelta <= 0.0F)) {
         int stage = Math.min(9, (int)(Math.clamp(this.secondaryProgress(), 0.0F, 1.0F) * 10.0F));
         mc.field_1687.method_8517(this.secondaryBreakerId(), this.secondary, stage);
      }
   }

   private int secondaryBreakerId() {
      return -1 - mc.field_1724.method_5628();
   }

   private float secondaryProgress() {
      return this.secondary != null && !(this.secondaryDelta <= 0.0F) ? this.secondaryTicks * this.secondaryDelta : 0.0F;
   }

   private float rawProgress() {
      if (this.started && this.startedAtNanos != 0L) {
         double elapsedMillis = Math.max(System.nanoTime() - this.startedAtNanos, 0L) / 1000000.0;
         return (float)(Math.max(elapsedMillis / 50.0 - 1.0, 0.0) * this.breakDelta);
      } else {
         return 0.0F;
      }
   }

   private float miningProgress() {
      if (!this.started) {
         return 0.0F;
      }

      if (this.stopSent) {
         return 1.0F;
      }

      float progress = Math.clamp(this.rawProgress() / this.threshold.getValue().floatValue(), 0.0F, 1.0F);
      this.shownProgress = Math.max(this.shownProgress, progress);
      return this.shownProgress;
   }

   private static class_238 lerpBox(class_238 from, class_238 to, float progress) {
      return new class_238(
         lerp(from.field_1323, to.field_1323, progress),
         lerp(from.field_1322, to.field_1322, progress),
         lerp(from.field_1321, to.field_1321, progress),
         lerp(from.field_1320, to.field_1320, progress),
         lerp(from.field_1325, to.field_1325, progress),
         lerp(from.field_1324, to.field_1324, progress)
      );
   }

   private static double lerp(double from, double to, float progress) {
      return from + (to - from) * progress;
   }

   private void clearTexture() {
      if (this.target != null && mc.field_1687 != null && mc.field_1724 != null) {
         mc.field_1687.method_8517(mc.field_1724.method_5628(), this.target, -1);
      }
   }

   private void clearTarget(boolean advanceGeneration) {
      this.clearTexture();
      if (advanceGeneration) {
         this.generation.incrementAndGet();
      }

      this.target = null;
      this.direction = null;
      this.targetBlock = null;
      this.breakDelta = 0.0F;
      this.shownProgress = 0.0F;
      this.startedAtNanos = 0L;
      this.ticks = 0;
      this.stopWaitTicks = 0;
      this.started = false;
      this.stopSent = false;
      this.instant = false;
      this.finishNotified = false;
      this.primaryIsRebreak = false;
      this.rebreakDelayTicks = 0;
      this.rebreakWaitTicks = 0;
      this.rebreakSawAir = false;
   }

   private void clearSecondary() {
      this.secondaryGeneration.incrementAndGet();
      this.releaseSecondaryTool();
      if (this.secondary != null && mc.field_1687 != null && mc.field_1724 != null) {
         mc.field_1687.method_8517(this.secondaryBreakerId(), this.secondary, -1);
      }

      this.secondary = null;
      this.secondaryBlock = null;
      this.secondaryDelta = 0.0F;
      this.secondaryTicks = 0;
      this.secondaryToolPulsed = false;
   }

   private void abortAndClearTarget() {
      class_2338 oldTarget = this.target;
      boolean oldUnfinished = !this.instant && !this.stopSent;
      this.clearTarget(true);
      this.completion.set(null);
      if (oldTarget != null && oldUnfinished && NetworkThreadExecutor.isConnected()) {
         NetworkThreadExecutor.execute(context -> context.playerAction(class_2847.field_12971, oldTarget, class_2350.field_11033));
      }
   }

   public enum Animation {
      OUT,
      IN;
   }

   private record Completion(long generation, DoubleMineModule.Phase phase, boolean sent, long sentAtNanos) {
   }

   private record PendingSelection(class_2338 pos, class_2350 face, int replacementDelayTicks, boolean manual) {
   }

   private enum Phase {
      START,
      STOP,
      DEMOTE;
   }

   private record ToolChoice(Result result, float progress) {
   }
}
