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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

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
   private BlockPos target;
   private Direction direction;
   private Block targetBlock;
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
   private BlockPos secondary;
   private Block secondaryBlock;
   private float secondaryDelta;
   private int secondaryTicks;
   private int secondaryHoldTicks;
   private boolean secondaryToolPulsed;
   private DoubleMineModule.PendingSelection pending;
   private final List<Consumer<BlockPos>> finishListeners = new CopyOnWriteArrayList<>();
   private final List<Function<BlockPos, NetworkAction>> finishActionProviders = new CopyOnWriteArrayList<>();

   public DoubleMineModule() {
      super("DoubleMine", Category.COMBAT, "Packet-mines two blocks at once: a primary, and a secondary the server finishes.");
   }

   public boolean isAvailable() {
      return this.isEnabled() && mc.player != null && mc.world != null && NetworkThreadExecutor.isConnected();
   }

   public boolean requestBreak(BlockPos pos) {
      return this.requestBreak(pos, 0);
   }

   public boolean requestBreak(BlockPos pos, int replacementDelayTicks) {
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
               BlockState state = mc.world.getBlockState(pos);
               if (!state.isAir() && !(state.getHardness(mc.world, pos) < 0.0F)) {
                  Direction face = legalFace(snapshot, pos, null);
                  this.select(pos, face != null ? face : Direction.UP, delay, false);
                  return this.isMining(pos);
               } else {
                  return false;
               }
            }
         }
      }
   }

   public boolean requestBreakPair(BlockPos delayedPos, BlockPos rebreakTarget, int replacementDelayTicks) {
      if (this.isAvailable() && !delayedPos.equals(rebreakTarget)) {
         BlockPos delayed = this.delayedDestroyPos();
         if (delayed != null && !delayed.equals(delayedPos)) {
            return false;
         }

         BlockPos held = this.rebreakPos();
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

   public boolean isMining(BlockPos pos) {
      return pos.equals(this.target) || pos.equals(this.secondary) || this.pending != null && pos.equals(this.pending.pos);
   }

   public boolean inMineRange(BlockPos pos) {
      return mc.player != null && this.withinRange(InventoryUtil.snapshot(), pos);
   }

   public BlockPos delayedDestroyPos() {
      if (this.secondary != null) {
         return this.secondary;
      } else {
         return this.target != null && !this.primaryIsRebreak ? this.target : null;
      }
   }

   public boolean hasDelayedDestroy() {
      return this.delayedDestroyPos() != null;
   }

   public BlockPos rebreakPos() {
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

   public void holdRebreak(BlockPos pos, int ticks) {
      if (pos != null && pos.equals(this.target) && this.stopSent) {
         this.rebreakSawAir = true;
         this.rebreakWaitTicks = Math.max(this.rebreakWaitTicks, Math.max(0, ticks));
      }
   }

   public void addFinishListener(Consumer<BlockPos> listener) {
      if (!this.finishListeners.contains(listener)) {
         this.finishListeners.add(listener);
      }
   }

   public void removeFinishListener(Consumer<BlockPos> listener) {
      this.finishListeners.remove(listener);
   }

   public void addFinishActionProvider(Function<BlockPos, NetworkAction> provider) {
      if (!this.finishActionProviders.contains(provider)) {
         this.finishActionProviders.add(provider);
      }
   }

   public void removeFinishActionProvider(Function<BlockPos, NetworkAction> provider) {
      this.finishActionProviders.remove(provider);
   }

   private void notifyFinish(BlockPos pos) {
      for (Consumer<BlockPos> listener : this.finishListeners) {
         listener.accept(pos);
      }
   }

   private List<NetworkAction> prepareFinishActions(BlockPos pos) {
      if (this.finishActionProviders.isEmpty()) {
         return List.of();
      }

      List<NetworkAction> actions = new ArrayList<>(this.finishActionProviders.size());

      for (Function<BlockPos, NetworkAction> provider : this.finishActionProviders) {
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
      if (mc.player != null && mc.world != null && NetworkThreadExecutor.isConnected()) {
         if (this.stopCooldown > 0) {
            this.stopCooldown--;
         }

         this.tickSecondary();
         this.tickPending();
         if (this.target != null) {
            BlockState state = mc.world.getBlockState(this.target);
            if (this.stopSent && this.rebreak.getValue()) {
               this.tickRebreak(state);
            } else if (!state.isAir() && state.getBlock() == this.targetBlock) {
               InventorySnapshot positionSnapshot = InventoryUtil.snapshot();
               if (!this.withinRange(positionSnapshot, this.target)) {
                  this.abortAndClearTarget();
               } else if (this.started) {
                  if (!this.stopSent) {
                     this.ticks++;
                  }

                  if (!this.actionPending.get() && !this.secondarySwapBusy() && !this.instant) {
                     if (!this.stopSent) {
                        DoubleMineModule.ToolChoice tool = this.bestTool(positionSnapshot, state, state.getHardness(mc.world, this.target), 36);
                        this.breakDelta = tool != null ? tool.progress : 0.0F;
                        this.updateBreakingTexture();
                        if (this.rawProgress() >= this.threshold.getValue()) {
                           Direction finishFace = legalFace(positionSnapshot, this.target, this.direction);
                           if (finishFace != null) {
                              this.submitStop(state, finishFace);
                           }
                        }
                     } else if (++this.stopWaitTicks >= 10) {
                        this.stopSent = false;
                        this.stopWaitTicks = 0;
                        InventorySnapshot snapshot = InventoryUtil.snapshot();
                        Direction finishFace = legalFace(snapshot, this.target, this.direction);
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

   private void tickRebreak(BlockState state) {
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      if (!this.withinRange(snapshot, this.target)) {
         this.clearTarget(true);
      } else if (state.isAir()) {
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
         && !(state.getHardness(mc.world, this.target) < 0.0F)) {
         if (this.rebreakSawAir && this.rebreakWaitTicks > 0) {
            this.rebreakWaitTicks--;
         } else {
            Direction face = legalFace(snapshot, this.target, this.direction);
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
   public void onRender3D(Camera camera, float partialTick) {
      if (this.render.getValue()) {
         if (this.target != null) {
            if (!this.stopSent || mc.world == null || !mc.world.getBlockState(this.target).isAir()) {
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

   private void renderOutline(BlockPos pos) {
      VisibilityConfigurable outline = GizmoDrawing.box(
         new Box(pos), DrawStyle.stroked(this.outlineColor.getValue().getRGB(), this.lineWidth.getValue().floatValue()), false
      );
      if (this.throughWalls.getValue()) {
         outline.ignoreOcclusion();
      }
   }

   private void renderBox(BlockPos pos, float progress) {
      Box block = new Box(pos);
      double centerX = (block.minX + block.maxX) * 0.5;
      double centerY = (block.minY + block.maxY) * 0.5;
      double centerZ = (block.minZ + block.maxZ) * 0.5;
      Box center = new Box(centerX, centerY, centerZ, centerX, centerY, centerZ);
      Box box = this.animation.getValue() == DoubleMineModule.Animation.OUT ? lerpBox(center, block, progress) : lerpBox(block, center, progress);
      DrawStyle style = DrawStyle.filledAndStroked(
         this.lineColor.getValue().getRGB(), this.lineWidth.getValue().floatValue(), this.sideColor.getValue().getRGB()
      );
      VisibilityConfigurable gizmo = GizmoDrawing.box(box, style, false);
      if (this.throughWalls.getValue()) {
         gizmo.ignoreOcclusion();
      }
   }

   @Override
   public void onDisable() {
      this.generation.incrementAndGet();
      BlockPos oldTarget = this.target;
      Direction oldDirection = this.direction;
      boolean oldUnfinished = !this.instant && !this.stopSent;
      this.clearTarget(false);
      this.clearSecondary();
      this.pending = null;
      this.stopCooldown = 0;
      this.completion.set(null);
      this.actionPending.set(false);
      if (oldTarget != null && oldDirection != null && oldUnfinished && NetworkThreadExecutor.isConnected()) {
         NetworkThreadExecutor.execute(context -> context.playerAction(Action.ABORT_DESTROY_BLOCK, oldTarget, Direction.DOWN));
      }
   }

   private void select(BlockPos pos, Direction face, int replacementDelayTicks, boolean manual) {
      if (!this.actionPending.get()
         && !pos.equals(this.secondary)
         && (this.pending == null || pos.equals(this.pending.pos))
         && (this.target == null || this.started || pos.equals(this.target))) {
         if (this.canDemote(pos)) {
            this.submitDemote(pos.toImmutable(), face, replacementDelayTicks, manual);
         } else if (!this.secondarySwapBusy() && this.stopCooldown <= 0 && startFits()) {
            this.start(pos, face, replacementDelayTicks, manual);
         } else {
            this.pending = new DoubleMineModule.PendingSelection(pos.toImmutable(), face, replacementDelayTicks, manual);
         }
      }
   }

   private void start(BlockPos pos, Direction face, int replacementDelayTicks, boolean manual) {
      if (!this.actionPending.get()
         && mc.player != null
         && mc.world != null
         && !mc.player.isSpectator()
         && !mc.player.isRiding()
         && NetworkThreadExecutor.isConnected()) {
         BlockState state = mc.world.getBlockState(pos);
         float hardness = state.getHardness(mc.world, pos);
         if (!state.isAir() && !(hardness < 0.0F)) {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (snapshot.available()
               && snapshot.menuId() == 0
               && !snapshot.deadOrDying()
               && snapshot.playerId() == mc.player.getId()
               && (!snapshot.usingItem() || !snapshot.usingMainHand())
               && this.withinRange(snapshot, pos)) {
               Direction startFace = legalFace(snapshot, pos, face);
               if (startFace != null) {
                  DoubleMineModule.ToolChoice tool = this.bestTool(snapshot, state, hardness, 36);
                  if (tool != null && !(tool.progress <= 0.0F)) {
                     BlockPos immutablePos = pos.toImmutable();
                     Direction immutableFace = startFace;
                     BlockPos oldTarget = this.target;
                     Direction oldDirection = this.direction;
                     boolean oldUnfinished = !this.instant && !this.stopSent;
                     boolean instantBreak = tool.progress >= 1.0F;
                     boolean rebreakHalf = manual || this.secondary != null || this.target != null && this.started && !this.rebreakSawAir;
                     List<NetworkAction> finishActions = instantBreak && !this.finishNotified ? this.prepareFinishActions(immutablePos) : List.of();
                     long selection = this.generation.incrementAndGet();
                     if (this.actionPending.compareAndSet(false, true)) {
                        this.clearTexture();
                        this.target = immutablePos;
                        this.direction = immutableFace;
                        this.targetBlock = state.getBlock();
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
                        BlockPos decoy = immutablePos.down(2000);
                        boolean[] sent = new boolean[]{false};
                        long[] sentAtNanos = new long[]{0L};
                        NetworkAction action = context -> {
                           if (this.generation.get() == selection) {
                              boolean abortOld = oldTarget != null && oldDirection != null && oldUnfinished && !oldTarget.equals(immutablePos);
                              int cost = (instantBreak ? 2 : 3) + (abortOld ? 1 : 0);
                              if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= cost
                                 && (!abortOld || context.playerAction(Action.ABORT_DESTROY_BLOCK, oldTarget, Direction.DOWN))) {
                                 long startNanos = System.nanoTime();
                                 sent[0] = instantBreak
                                    ? context.startInstantBreak(immutablePos, immutableFace, true)
                                    : context.startGrimBreak(immutablePos, immutableFace, decoy, Direction.UP, true);
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

   private void submitStop(BlockState state, Direction finishFace) {
      if (this.target != null && this.direction != null && this.actionPending.compareAndSet(false, true)) {
         InventorySnapshot snapshot = InventoryUtil.snapshot();
         float hardness = state.getHardness(mc.world, this.target);
         DoubleMineModule.ToolChoice tool = this.bestTool(snapshot, state, hardness, 36);
         if (tool == null) {
            this.actionPending.set(false);
         } else {
            long submission = this.generation.get();
            BlockPos immutableTarget = this.target;
            Direction immutableDirection = finishFace;
            List<NetworkAction> finishActions = this.finishNotified ? List.of() : this.prepareFinishActions(immutableTarget);
            boolean[] sent = new boolean[]{false};
            NetworkAction action = context -> {
               if (this.generation.get() == submission
                  && context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= 2
                  && context.playerAction(Action.STOP_DESTROY_BLOCK, immutableTarget, immutableDirection)) {
                  sent[0] = context.swing(Hand.MAIN_HAND);
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

   private boolean canDemote(BlockPos pos) {
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

   private void submitDemote(BlockPos next, Direction face, int replacementDelayTicks, boolean manual) {
      this.pending = new DoubleMineModule.PendingSelection(next, face, replacementDelayTicks, manual);
      InventorySnapshot snapshot = InventoryUtil.snapshot();
      Direction stopFace = legalFace(snapshot, this.target, this.direction);
      if (stopFace != null && this.actionPending.compareAndSet(false, true)) {
         long submission = this.generation.get();
         BlockPos immutableTarget = this.target;
         boolean[] sent = new boolean[]{false};
         NetworkAction action = context -> {
            if (this.generation.get() == submission
               && context.limits().remaining(PacketLimiter.Bucket.GLOBAL) >= 2
               && context.playerAction(Action.STOP_DESTROY_BLOCK, immutableTarget, stopFace)) {
               sent[0] = context.swing(Hand.MAIN_HAND);
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

         BlockState state = mc.world.getBlockState(this.secondary);
         if (state.isAir() || state.getBlock() != this.secondaryBlock) {
            BlockPos broken = this.secondary;
            this.clearSecondary();
            if (state.isAir()) {
               this.notifyFinish(broken);
            }
         } else if (!swapInFlight) {
            InventorySnapshot snapshot = InventoryUtil.snapshot();
            if (snapshot.available()) {
               float hardness = state.getHardness(mc.world, this.secondary);
               DoubleMineModule.ToolChoice tool = this.bestTool(snapshot, state, hardness, PlayerInventory.getHotbarSize());
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

   private boolean shouldUseVanillaMining(BlockPos pos) {
      if (mc.player != null && mc.world != null) {
         BlockState state = mc.world.getBlockState(pos);
         float hardness = state.getHardness(mc.world, pos);
         if (state.isAir() || hardness < 0.0F) {
            return false;
         }

         if (hardness <= 1.0F) {
            return true;
         }

         InventorySnapshot snapshot = InventoryUtil.snapshot();
         if (!snapshot.available()) {
            return false;
         }

         ItemStack held = snapshot.item(snapshot.selectedSlot());
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

   private DoubleMineModule.ToolChoice bestTool(InventorySnapshot snapshot, BlockState state, float hardness, int slots) {
      if (snapshot.available() && !(hardness < 0.0F)) {
         DoubleMineModule.ToolChoice best = null;
         int size = Math.min(slots, snapshot.size());

         for (int slot = 0; slot < size; slot++) {
            ItemStack stack = snapshot.item(slot);
            float progress = this.destroyProgress(stack, state, hardness);
            if (best == null || progress > best.progress) {
               Result result = stack.isEmpty() ? null : InventoryUtil.resultAt(snapshot, slot);
               best = new DoubleMineModule.ToolChoice(result, progress);
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private boolean withinRange(InventorySnapshot snapshot, BlockPos pos) {
      if (snapshot.available() && mc.player != null) {
         double allowed = Math.min(this.range.getValue(), mc.player.getBlockInteractionRange());
         double allowedSqr = allowed * allowed;
         Box box = new Box(pos);
         Vec3d eye = snapshot.eyePosition();
         double x = Math.clamp(eye.x, box.minX, box.maxX);
         double y = Math.clamp(eye.y, box.minY, box.maxY);
         double z = Math.clamp(eye.z, box.minZ, box.maxZ);
         return eye.squaredDistanceTo(x, y, z) <= allowedSqr;
      } else {
         return false;
      }
   }

   private static Direction legalFace(InventorySnapshot snapshot, BlockPos pos, Direction preferred) {
      if (!snapshot.available()) {
         return null;
      }

      double x = snapshot.x();
      double z = snapshot.z();
      double minEyeY = snapshot.eyeY();
      double maxEyeY = snapshot.eyeY();
      double minX = pos.getX();
      double minY = pos.getY();
      double minZ = pos.getZ();
      double maxX = minX + 1.0;
      double maxY = minY + 1.0;
      double maxZ = minZ + 1.0;
      if (x >= minX && x <= maxX && z >= minZ && z <= maxZ && maxEyeY >= minY && minEyeY <= maxY) {
         return preferred;
      }

      if (facePossible(preferred, x, minEyeY, maxEyeY, z, minX, minY, minZ, maxX, maxY, maxZ)) {
         return preferred;
      }

      Direction best = null;
      double bestDistance = Double.POSITIVE_INFINITY;

      for (Direction candidate : Direction.values()) {
         if (facePossible(candidate, x, minEyeY, maxEyeY, z, minX, minY, minZ, maxX, maxY, maxZ)) {
            double distance = switch (candidate) {
               case WEST -> minX - x;
               case EAST -> x - maxX;
               case NORTH -> minZ - z;
               case SOUTH -> z - maxZ;
               case DOWN -> minY - minEyeY;
               case UP -> maxEyeY - maxY;
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
      Direction face, double x, double minEyeY, double maxEyeY, double z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ
   ) {
      if (face == null) {
         return false;
      }

      return switch (face) {
         case WEST -> x <= minX;
         case EAST -> x >= maxX;
         case NORTH -> z <= minZ;
         case SOUTH -> z >= maxZ;
         case DOWN -> minEyeY <= minY;
         case UP -> maxEyeY >= maxY;
         default -> throw new MatchException(null, null);
      };
   }

   private float destroyProgress(ItemStack stack, BlockState state, float hardness) {
      if (hardness < 0.0F) {
         return 0.0F;
      }

      float speed = stack.getMiningSpeedMultiplier(state);
      if (speed > 1.0F) {
         speed += (float)miningEfficiency(stack);
      }

      if (StatusEffectUtil.hasHaste(mc.player)) {
         speed *= 1.0F + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2F;
      }

      if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
         int amplifier = mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier();

         speed *= switch (amplifier) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      speed *= (float)mc.player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
      if (mc.player.isSubmergedIn(FluidTags.WATER)) {
         speed *= (float)mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
      }

      if (!mc.player.isOnGround()) {
         speed /= 5.0F;
      }

      boolean correctTool = !state.isToolRequired() || stack.isSuitableFor(state);
      return speed / hardness / (correctTool ? 30.0F : 100.0F);
   }

   private static double miningEfficiency(ItemStack stack) {
      double[] total = new double[]{0.0};
      EnchantmentHelper.applyAttributeModifiers(stack, EquipmentSlot.MAINHAND, (attribute, modifier) -> {
         if (EntityAttributes.MINING_EFFICIENCY.equals(attribute) && modifier.operation() == Operation.ADD_VALUE) {
            total[0] += modifier.value();
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
               if (mc.player != null) {
                  mc.player.swingHand(Hand.MAIN_HAND, false);
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

               if (mc.player != null) {
                  mc.player.swingHand(Hand.MAIN_HAND, false);
               }
            } else {
               this.stopSent = true;
               this.stopWaitTicks = 0;
               this.stopCooldown = this.breakDelay.getValue();
               if (mc.player != null) {
                  mc.player.swingHand(Hand.MAIN_HAND, false);
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
      if (mc.world != null && mc.player != null && !(this.breakDelta <= 0.0F)) {
         int stage = Math.min(9, (int)(this.miningProgress() * 10.0F));
         mc.world.setBlockBreakingInfo(mc.player.getId(), this.target, stage);
      }
   }

   private void updateSecondaryTexture() {
      if (mc.player != null && !(this.secondaryDelta <= 0.0F)) {
         int stage = Math.min(9, (int)(Math.clamp(this.secondaryProgress(), 0.0F, 1.0F) * 10.0F));
         mc.world.setBlockBreakingInfo(this.secondaryBreakerId(), this.secondary, stage);
      }
   }

   private int secondaryBreakerId() {
      return -1 - mc.player.getId();
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

   private static Box lerpBox(Box from, Box to, float progress) {
      return new Box(
         lerp(from.minX, to.minX, progress),
         lerp(from.minY, to.minY, progress),
         lerp(from.minZ, to.minZ, progress),
         lerp(from.maxX, to.maxX, progress),
         lerp(from.maxY, to.maxY, progress),
         lerp(from.maxZ, to.maxZ, progress)
      );
   }

   private static double lerp(double from, double to, float progress) {
      return from + (to - from) * progress;
   }

   private void clearTexture() {
      if (this.target != null && mc.world != null && mc.player != null) {
         mc.world.setBlockBreakingInfo(mc.player.getId(), this.target, -1);
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
      if (this.secondary != null && mc.world != null && mc.player != null) {
         mc.world.setBlockBreakingInfo(this.secondaryBreakerId(), this.secondary, -1);
      }

      this.secondary = null;
      this.secondaryBlock = null;
      this.secondaryDelta = 0.0F;
      this.secondaryTicks = 0;
      this.secondaryToolPulsed = false;
   }

   private void abortAndClearTarget() {
      BlockPos oldTarget = this.target;
      boolean oldUnfinished = !this.instant && !this.stopSent;
      this.clearTarget(true);
      this.completion.set(null);
      if (oldTarget != null && oldUnfinished && NetworkThreadExecutor.isConnected()) {
         NetworkThreadExecutor.execute(context -> context.playerAction(Action.ABORT_DESTROY_BLOCK, oldTarget, Direction.DOWN));
      }
   }

   public enum Animation {
      OUT,
      IN;
   }

   private record Completion(long generation, DoubleMineModule.Phase phase, boolean sent, long sentAtNanos) {
   }

   private record PendingSelection(BlockPos pos, Direction face, int replacementDelayTicks, boolean manual) {
   }

   private enum Phase {
      START,
      STOP,
      DEMOTE;
   }

   private record ToolChoice(Result result, float progress) {
   }
}
