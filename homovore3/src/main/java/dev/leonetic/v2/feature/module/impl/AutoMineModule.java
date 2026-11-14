package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import dev.leonetic.v2.util.network.NetworkAction;
import dev.leonetic.v2.util.world.ExplosionMath;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

public final class AutoMineModule extends Module {
   private static final double INVALID_SCORE = -1000.0;
   private static final int GLASS_REBREAK_HOLD_TICKS = 5;
   private static final Direction[] HORIZONTAL = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   public final Setting<Double> range = this.number("Range", (double)7.0, (double)0.0, (double)7.0)
      .describe("How far a player may be to be targeted. Blocks are still limited to Double Mine's range.");
   public final Setting<AutoMineModule.Priority> priority = this.mode("Priority", AutoMineModule.Priority.ANGLE)
      .describe("Angle picks the player closest to your crosshair.");
   public final Setting<Boolean> ignoreNakeds = this.bool("IgnoreNakeds", true).describe("Skips players wearing no armour.");
   public final Setting<AutoMineModule.Extend> extend = this.mode("Extend", AutoMineModule.Extend.CORNER)
      .describe("Also considers the block past a surround block, in a line or at a corner.");
   public final Setting<Boolean> underMine = this.bool("UnderMine", true).describe("Mines non-crystal blocks under the surround to open a base.");
   public final Setting<Boolean> crawlMine = this.bool("CrawlMine", false).describe("Mines your own feet block while you are stuck crawling.");
   public final Setting<AutoMineModule.AntiSwim> antiSwim = this.mode("AntiSwim", AutoMineModule.AntiSwim.NONE)
      .describe("Mines the obsidian over your head. Always: whenever it is there. Mine: while someone mines your feet. Mine Or Swim: that, or while crawling.");
   public final Setting<AutoMineModule.AntiSurround> antiSurround = this.mode("AntiSurround", AutoMineModule.AntiSurround.OUTER)
      .describe(
         "Places a crystal once a surround block breaks, through Crystal Placer. Inner fills the hole; Outer blocks it from beside; Auto tries Outer first."
      );
   public final Setting<Double> outerCooldown = this.number("OuterCooldown", (double)0.1, (double)0.0, (double)1.0)
      .describe("Seconds between outer anti-surround crystals.")
      .visibleWhen(() -> this.antiSurround.getValue() == AutoMineModule.AntiSurround.AUTO || this.antiSurround.getValue() == AutoMineModule.AntiSurround.OUTER);
   public final Setting<Boolean> comboScoring = this.bool("ComboScoring", true)
      .describe("Prefers mine targets that open a higher-damage crystal follow-up.")
      .page("Synergy");
   public final Setting<Boolean> avoidPistonCrystal = this.bool("AvoidPistonCrystal", true)
      .describe("Does not mine pistons or power blocks freshly placed by Piston Crystal.")
      .page("Synergy");
   public final Setting<Integer> rebreakDelay = this.integer("RebreakDelay", 0, 0, 20)
      .describe("Ticks to hold Auto Mine's rebreak after its block is replaced. The feet block remains the delayed destroy while the other block rebreaks.")
      .page("Synergy");
   public final Setting<Boolean> glassPush = this.bool("GlassPush", false)
      .describe(
         "Pushes dropped items out of a crystal rebreak cell with glass, then rebreaks it and retries the crystal. Glass must be in the hotbar or offhand."
      )
      .page("Synergy");
   public final Setting<Integer> glassAttempts = this.integer("GlassAttempts", 2, 1, 5)
      .describe("Maximum glass pushes attempted for one blocked rebreak cell.")
      .visibleWhen(this.glassPush::getValue)
      .page("Synergy");
   public final Setting<Boolean> glassRender = this.bool("GlassRender", false).page("Render");
   public final Setting<Double> glassFade = this.number("GlassFade", (double)0.5, (double)0.05, (double)2.0)
      .describe("Seconds the glass-push box takes to fade.")
      .visibleWhen(this.glassRender::getValue)
      .page("Render");
   public final Setting<Color> glassFillColor = this.color("GlassFill", 0, 222, 255, 44).visibleWhen(this.glassRender::getValue).page("Render");
   public final Setting<Color> glassLineColor = this.color("GlassLine", 255, 255, 255, 0).visibleWhen(this.glassRender::getValue).page("Render");
   public final Setting<Boolean> debugScores = this.bool("DebugScores", false)
      .describe("Outlines every candidate block, brighter for a higher score.")
      .page("Render");
   private final Consumer<BlockPos> finishListener = this::onMineFinish;
   private final Function<BlockPos, NetworkAction> finishActionProvider = this::prepareMineFinishAction;
   private PlayerEntity targetPlayer;
   private BlockPos ignorePos;
   private boolean terrainFight;
   private long lastOuterPlaceMillis;
   private List<AutoMineModule.ScoredPos> debugCandidates = List.of();
   private BlockPos glassTargetPos;
   private int glassUsedAttempts;
   private BlockPos glassRenderPos;
   private long glassRenderStartMillis;
   private final AtomicLong glassGeneration = new AtomicLong();
   private final AtomicLong nextGlassSubmission = new AtomicLong();
   private final AtomicLong pendingGlassSubmission = new AtomicLong();
   private final AtomicReference<AutoMineModule.GlassCompletion> glassCompletion = new AtomicReference<>();

   public AutoMineModule() {
      super("AutoMine", Category.COMBAT, "Mines the blocks around a nearby player through Double Mine.");
   }

   @Override
   public void onEnable() {
      DoubleMineModule mine = Homovore2Client.modules().get(DoubleMineModule.class);
      if (mine != null) {
         mine.addFinishListener(this.finishListener);
         mine.addFinishActionProvider(this.finishActionProvider);
      }

      this.lastOuterPlaceMillis = 0L;
      this.resetGlass();
      this.glassRenderPos = null;
   }

   @Override
   public void onDisable() {
      DoubleMineModule mine = Homovore2Client.modules().get(DoubleMineModule.class);
      if (mine != null) {
         mine.removeFinishListener(this.finishListener);
         mine.removeFinishActionProvider(this.finishActionProvider);
      }

      this.targetPlayer = null;
      this.ignorePos = null;
      this.debugCandidates = List.of();
      this.resetGlass();
      this.glassRenderPos = null;
   }

   @Override
   public void onTick() {
      this.applyGlassCompletion();
      this.debugCandidates = List.of();
      DoubleMineModule mine = this.miner();
      if (mine == null) {
         this.targetPlayer = null;
         this.resetGlass();
      } else if (this.tryCrawlMine(mine)) {
         this.targetPlayer = null;
         this.resetGlass();
      } else {
         BlockPos selfHeadPos = mc.player.getBlockPos().up();
         BlockState selfHeadBlock = mc.world.getBlockState(selfHeadPos);
         boolean breakSelfHead = this.canBreak(selfHeadPos, selfHeadBlock)
            && (selfHeadBlock.isOf(Blocks.OBSIDIAN) || selfHeadBlock.isOf(Blocks.CRYING_OBSIDIAN));
         boolean prioritiseHead = false;
         if (breakSelfHead) {
            AutoMineModule.AntiSwim swim = this.antiSwim.getValue();
            boolean crawling = swim == AutoMineModule.AntiSwim.MINE_OR_SWIM && mc.player.isCrawling();
            boolean feetMined = (swim == AutoMineModule.AntiSwim.MINE || swim == AutoMineModule.AntiSwim.MINE_OR_SWIM)
               && this.isBlockBeingBroken(mc.player.getBlockPos());
            if (swim == AutoMineModule.AntiSwim.ALWAYS || crawling || feetMined) {
               request(mine, selfHeadPos);
               prioritiseHead = true;
            }
         }

         this.targetPlayer = this.selectTarget();
         if (this.targetPlayer == null) {
            this.resetGlass();
         } else {
            boolean handlingGlass = this.handleGlassPush(mine);
            if (!prioritiseHead && !handlingGlass) {
               if (this.targetPlayer.isCrawling()) {
                  AutoMineModule.CrawlPair crawl = this.selectCrawlPair(mine);
                  this.ignorePos = crawl == null ? null : crawl.delayed;
                  if (this.debugScores.getValue()) {
                     if (crawl == null) {
                        this.debugCandidates = List.of();
                     } else if (crawl.rebreak == null) {
                        this.debugCandidates = List.of(new AutoMineModule.ScoredPos(crawl.delayed, crawl.delayedDamage));
                     } else {
                        this.debugCandidates = List.of(
                           new AutoMineModule.ScoredPos(crawl.delayed, crawl.delayedDamage), new AutoMineModule.ScoredPos(crawl.rebreak, crawl.rebreakDamage)
                        );
                     }
                  }

                  if (crawl != null) {
                     if (crawl.rebreak != null) {
                        mine.requestBreakPair(crawl.delayed, crawl.rebreak, this.rebreakDelay.getValue());
                     } else {
                        request(mine, crawl.delayed);
                     }
                  }
               } else {
                  AutoMineModule.CityBlock target1 = null;
                  AutoMineModule.CityBlock target2 = null;
                  this.ignorePos = null;
                  if (this.isPhased(this.targetPlayer) || this.targetHeadCovered()) {
                     AutoMineModule.CityBlock delayed = this.findCityBlock(mine, null, true);
                     if (delayed != null) {
                        this.ignorePos = delayed.pos;
                        AutoMineModule.CityBlock rebreakTarget = this.findCityBlock(mine, delayed.pos, false);
                        if (rebreakTarget != null) {
                           if (this.debugScores.getValue()) {
                              this.debugCandidates = this.scoreCandidates(mine);
                           }

                           mine.requestBreakPair(delayed.pos, rebreakTarget.pos, this.rebreakDelay.getValue());
                           return;
                        }
                     }
                  }

                  AutoMineModule.CityBlock best = this.findCityBlock(mine, null);
                  if (best != null) {
                     target1 = best;
                     this.ignorePos = best.pos;
                     BlockPos surroundAbove = best.pos.up();
                     if (best.type == AutoMineModule.CheckType.TERRAIN_BASE && !this.claimedByOther(mine, surroundAbove)) {
                        target2 = new AutoMineModule.CityBlock(surroundAbove, best.score - 10.0, AutoMineModule.CheckType.SURROUND);
                     } else {
                        target2 = this.findCityBlock(mine, best.pos);
                     }
                  }

                  if (this.debugScores.getValue()) {
                     this.debugCandidates = this.scoreCandidates(mine);
                  }

                  BlockPos activeRebreak = mine.rebreakPos();
                  boolean targetingFeet = target1 != null && target1.type == AutoMineModule.CheckType.FEET
                     || target2 != null && target2.type == AutoMineModule.CheckType.FEET;
                  boolean keepsActiveRebreak = activeRebreak != null
                     && (target1 != null && target1.pos.equals(activeRebreak) || target2 != null && target2.pos.equals(activeRebreak));
                  if ((targetingFeet || !keepsActiveRebreak) && (!mine.hasDelayedDestroy() || !mine.hasRebreakBlock())) {
                     Deque<BlockPos> wanted = new ArrayDeque<>();
                     if (target1 != null) {
                        wanted.add(target1.pos);
                     }

                     if (target2 != null) {
                        wanted.add(target2.pos);
                     }

                     if (!wanted.isEmpty() && mine.hasDelayedDestroy()) {
                        mine.requestBreak(wanted.remove(), this.rebreakDelay.getValue());
                     }

                     if (!wanted.isEmpty() && !mine.hasRebreakBlock()) {
                        mine.requestBreak(wanted.remove(), this.rebreakDelay.getValue());
                     }
                  }
               }
            }
         }
      }
   }

   public int targetId() {
      return this.targetPlayer == null ? -1 : this.targetPlayer.getId();
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      this.renderGlassPush();
      List<AutoMineModule.ScoredPos> candidates = this.debugCandidates;
      if (!candidates.isEmpty()) {
         double bestScore = 0.0;

         for (AutoMineModule.ScoredPos candidate : candidates) {
            bestScore = Math.max(bestScore, candidate.score);
         }

         if (!(bestScore <= 0.0)) {
            for (AutoMineModule.ScoredPos candidate : candidates) {
               int alpha = (int)Math.clamp(200.0 * (candidate.score / bestScore), 20.0, 200.0);
               GizmoDrawing.box(candidate.pos, DrawStyle.stroked(new Color(255, 40, 40, alpha).getRGB(), 1.0F)).ignoreOcclusion();
            }
         }
      }
   }

   private boolean handleGlassPush(DoubleMineModule mine) {
      if (!this.glassPush.getValue()) {
         this.resetGlass();
         return false;
      }

      CrystalPlacerModule crystal = Homovore2Client.modules().get(CrystalPlacerModule.class);
      if (crystal != null && crystal.isEnabled() && crystal.place.getValue()) {
         BlockPos pos = mine.rebreakPos();
         if (pos != null && this.isGoodRebreak(mine, pos, this.inBedrockCase()) && this.isDesirableGlassPlacement(pos, crystal)) {
            if (!pos.equals(this.glassTargetPos)) {
               this.glassGeneration.incrementAndGet();
               this.glassTargetPos = pos.toImmutable();
               this.glassUsedAttempts = 0;
            }

            if (Homovore2Client.placements().isPending(pos)) {
               mine.holdRebreak(pos, 5);
               return true;
            }

            if (this.itemInCrystalSpace(pos)) {
               if (this.glassUsedAttempts >= this.glassAttempts.getValue()) {
                  return false;
               }

               if (this.pendingGlassSubmission.get() != 0L) {
                  return true;
               }

               if (!mc.world.getBlockState(pos).isAir()) {
                  return true;
               }

               if (!InventoryUtil.find(Items.GLASS, InventoryUtil.HOTBAR_SCOPE).found()) {
                  return false;
               }

               long submission = this.nextGlassSubmission.incrementAndGet();
               if (!this.pendingGlassSubmission.compareAndSet(0L, submission)) {
                  return true;
               }

               long generation = this.glassGeneration.get();
               BlockPos immutablePos = pos.toImmutable();
               mine.holdRebreak(immutablePos, 5);
               Homovore2Client.placements().enqueue(immutablePos, Items.GLASS).whenComplete((queued, failure) -> {
                  if (this.glassGeneration.get() == generation) {
                     this.glassCompletion.set(new AutoMineModule.GlassCompletion(generation, immutablePos, failure == null && Boolean.TRUE.equals(queued)));
                  }

                  this.pendingGlassSubmission.compareAndSet(submission, 0L);
               });
               return true;
            } else if (this.glassUsedAttempts > 0 && mc.world.getBlockState(pos).isAir()) {
               crystal.preplace(pos);
               this.glassUsedAttempts = 0;
               return true;
            } else {
               this.glassUsedAttempts = 0;
               return false;
            }
         } else {
            this.resetGlass();
            return false;
         }
      } else {
         this.resetGlass();
         return false;
      }
   }

   private void applyGlassCompletion() {
      AutoMineModule.GlassCompletion completed = this.glassCompletion.getAndSet(null);
      if (completed != null && completed.generation == this.glassGeneration.get() && completed.queued && completed.pos.equals(this.glassTargetPos)) {
         this.glassUsedAttempts++;
         this.glassRenderPos = completed.pos;
         this.glassRenderStartMillis = System.currentTimeMillis();
      }
   }

   private boolean itemInCrystalSpace(BlockPos airPos) {
      Box space = new Box(airPos).stretch(0.0, 1.0, 0.0);
      return !mc.world.getOtherEntities((Entity)null, space, entity -> entity instanceof ItemEntity && !entity.isSpectator()).isEmpty();
   }

   private boolean isGoodRebreak(DoubleMineModule mine, BlockPos pos, boolean inBedrock) {
      if (mine.canRebreak() && pos.equals(mine.rebreakPos())) {
         if (inBedrock) {
            if (this.feetCellCount() != 1) {
               return false;
            }

            BlockPos feet = this.targetPlayer.getBlockPos();
            if (pos.equals(feet.up(2))) {
               return true;
            }

            if (!mc.world.getBlockState(feet.up()).isAir()) {
               return false;
            }

            for (Direction direction : HORIZONTAL) {
               if (pos.equals(feet.up().offset(direction))) {
                  return true;
               }
            }

            return false;
         } else {
            if (this.isBlockInFeet(pos)) {
               return false;
            }

            for (BlockPos feet : iterate(this.targetFeetBox())) {
               for (Direction direction : HORIZONTAL) {
                  if (pos.equals(feet.offset(direction)) && this.isCrystalBlock(pos.down())) {
                     return true;
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isDesirableGlassPlacement(BlockPos airPos, CrystalPlacerModule crystal) {
      if (!this.isCrystalBlock(airPos.down())) {
         return false;
      }

      Vec3d eye = mc.player.getEyePos();
      double placeRange = 4.5;
      if (distanceSquaredToBox(eye, new Box(airPos.down())) > placeRange * placeRange) {
         return false;
      }

      double x = airPos.getX() + 0.5;
      double y = airPos.getY();
      double z = airPos.getZ() + 0.5;
      double breakRange = 3.0;
      if (distanceSquaredToBox(eye, new Box(x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0)) > breakRange * breakRange) {
         return false;
      }

      Vec3d source = new Vec3d(x, y, z);
      double selfDamage = ExplosionMath.damage(
         mc.player.squaredDistanceTo(source),
         ExplosionMath.exposure(mc.world, source, mc.player),
         mc.player.getArmor(),
         mc.player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
         ExplosionMath.resistanceFactor(mc.player),
         ExplosionMath.protectionPoints(mc.player),
         mc.world.getDifficulty().getId()
      );
      if (mc.player.getAbilities().invulnerable
         || !(selfDamage > crystal.maxSelfDamage.getValue()) && !(selfDamage + 1.0 >= mc.player.getHealth() + mc.player.getAbsorptionAmount())) {
         if (this.potentialCrystalDamage(airPos, true) >= crystal.minDamage.getValue()) {
            return true;
         }

         if (this.antiSurround.getValue() == AutoMineModule.AntiSurround.NONE) {
            return false;
         }

         for (BlockPos feet : iterate(this.targetFeetBox())) {
            for (Direction direction : HORIZONTAL) {
               if (airPos.equals(feet.offset(direction))) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static double distanceSquaredToBox(Vec3d point, Box box) {
      double x = Math.clamp(point.x, box.minX, box.maxX);
      double y = Math.clamp(point.y, box.minY, box.maxY);
      double z = Math.clamp(point.z, box.minZ, box.maxZ);
      return point.squaredDistanceTo(x, y, z);
   }

   private void resetGlass() {
      if (this.glassTargetPos != null || this.glassUsedAttempts != 0) {
         this.glassGeneration.incrementAndGet();
      }

      this.glassTargetPos = null;
      this.glassUsedAttempts = 0;
      this.glassCompletion.set(null);
   }

   private void renderGlassPush() {
      if (this.glassRender.getValue() && this.glassRenderPos != null) {
         double fadeMillis = this.glassFade.getValue() * 1000.0;
         double age = System.currentTimeMillis() - this.glassRenderStartMillis;
         if (age >= fadeMillis) {
            this.glassRenderPos = null;
         } else {
            double alpha = 1.0 - age / fadeMillis;
            Color fill = faded(this.glassFillColor.getValue(), alpha);
            Color line = faded(this.glassLineColor.getValue(), alpha);
            GizmoDrawing.box(new Box(this.glassRenderPos), DrawStyle.filledAndStroked(line.getRGB(), 1.5F, fill.getRGB()), false).ignoreOcclusion();
         }
      }
   }

   private static Color faded(Color color, double alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)Math.clamp(Math.round(color.getAlpha() * alpha), 0L, 255L));
   }

   private void onMineFinish(BlockPos pos) {
      AutoMineModule.AntiSurroundPlacement placement = this.findAntiSurroundPlacement(pos);
      if (placement != null && placement.crystal.preplace(placement.airPos) && placement.outer) {
         this.lastOuterPlaceMillis = System.currentTimeMillis();
      }
   }

   private NetworkAction prepareMineFinishAction(BlockPos pos) {
      AutoMineModule.AntiSurroundPlacement placement = this.findAntiSurroundPlacement(pos);
      if (placement == null) {
         return null;
      }

      NetworkAction action = placement.crystal.preparePreplaceAction(placement.airPos);
      if (action != null && placement.outer) {
         this.lastOuterPlaceMillis = System.currentTimeMillis();
      }

      return action;
   }

   private AutoMineModule.AntiSurroundPlacement findAntiSurroundPlacement(BlockPos pos) {
      if (mc.player != null && mc.world != null && this.targetPlayer != null) {
         AutoMineModule.AntiSurround mode = this.antiSurround.getValue();
         if (mode == AutoMineModule.AntiSurround.NONE) {
            return null;
         }

         CrystalPlacerModule crystal = Homovore2Client.modules().get(CrystalPlacerModule.class);
         if (crystal != null && crystal.isEnabled()) {
            boolean surroundBlock = false;

            label96:
            for (BlockPos feet : iterate(this.targetFeetBox())) {
               Direction[] targetFeet = HORIZONTAL;
               int best = targetFeet.length;
               int bestDistance = 0;

               while (true) {
                  if (bestDistance < best) {
                     Direction dir = targetFeet[bestDistance];
                     if (!feet.offset(dir).equals(pos)) {
                        bestDistance++;
                        continue;
                     }

                     surroundBlock = true;
                  }

                  if (surroundBlock) {
                     break label96;
                  }
                  break;
               }
            }

            if (!surroundBlock) {
               return null;
            }

            if (mode == AutoMineModule.AntiSurround.AUTO || mode == AutoMineModule.AntiSurround.OUTER) {
               long now = System.currentTimeMillis();
               if (now - this.lastOuterPlaceMillis > this.outerCooldown.getValue() * 1000.0) {
                  Set<BlockPos> targetFeet = new HashSet<>();

                  for (BlockPos feetCell : iterate(this.targetFeetBox())) {
                     targetFeet.add(feetCell.toImmutable());
                  }

                  BlockPos best = null;
                  double bestDistance = -1.0;

                  for (Direction direction : HORIZONTAL) {
                     BlockPos candidate = pos.offset(direction);
                     if (!targetFeet.contains(candidate)) {
                        BlockState state = mc.world.getBlockState(candidate);
                        if ((state.isAir() || state.isReplaceable()) && this.isCrystalBlock(candidate.down())) {
                           Box crystalSpace = new Box(candidate).stretch(0.0, 1.0, 0.0);
                           if (mc.world.getOtherEntities((Entity)null, crystalSpace, entity -> !entity.isSpectator()).isEmpty()) {
                              double distance = this.targetPlayer.squaredDistanceTo(Vec3d.ofCenter(candidate));
                              if (distance > bestDistance) {
                                 bestDistance = distance;
                                 best = candidate.toImmutable();
                              }
                           }
                        }
                     }
                  }

                  if (best != null) {
                     return new AutoMineModule.AntiSurroundPlacement(crystal, best, true);
                  }
               }
            }

            return mode != AutoMineModule.AntiSurround.AUTO && mode != AutoMineModule.AntiSurround.INNER
               ? null
               : new AutoMineModule.AntiSurroundPlacement(crystal, pos.toImmutable(), false);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static void request(DoubleMineModule mine, BlockPos pos) {
      if (!mine.isMining(pos)) {
         mine.requestBreak(pos);
      }
   }

   private boolean tryCrawlMine(DoubleMineModule mine) {
      if (this.crawlMine.getValue() && !mc.player.isInSneakingPose()) {
         BlockPos feetPos = mc.player.getBlockPos();
         BlockPos headPos = feetPos.up();
         BlockState feetBlock = mc.world.getBlockState(feetPos);
         if (!this.isBlocked(feetPos, feetBlock) || !this.isBlocked(headPos, mc.world.getBlockState(headPos))) {
            return false;
         }

         if (!this.canBreak(feetPos, feetBlock)) {
            return false;
         }

         request(mine, feetPos);
         return mine.isMining(feetPos);
      } else {
         return false;
      }
   }

   private boolean isBlocked(BlockPos pos, BlockState state) {
      return !state.isReplaceable() && state.getOutlineShape(mc.world, pos) != VoxelShapes.empty();
   }

   private boolean isBlockBeingBroken(BlockPos pos) {
      return Homovore2Client.breaks().isBeingBroken(pos, mc.player.getId());
   }

   private boolean claimedByOther(DoubleMineModule mine, BlockPos pos) {
      return !mine.isMining(pos) && this.isBlockBeingBroken(pos);
   }

   private PlayerEntity selectTarget() {
      double maxRange = this.range.getValue();
      Vec3d eye = mc.player.getEyePos();
      PlayerEntity best = null;
      double bestMetric = Double.MAX_VALUE;
      boolean bestPhased = false;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player
            && player.isAlive()
            && !player.isSpectator()
            && !Homovore2Client.friends().isFriend(player)
            && !(player.getEntityPos().distanceTo(eye) > maxRange)
            && (!this.ignoreNakeds.getValue() || !isNaked(player))) {
            boolean phased = this.isPhased(player);

            double metric = switch ((AutoMineModule.Priority)this.priority.getValue()) {
               case ANGLE -> this.angleTo(player);
               case DISTANCE -> mc.player.squaredDistanceTo(player);
            };
            if (phased && !bestPhased || phased == bestPhased && metric < bestMetric) {
               bestMetric = metric;
               best = player;
               bestPhased = phased;
            }
         }
      }

      return best;
   }

   private double angleTo(PlayerEntity player) {
      Vec3d delta = player.getEyePos().subtract(mc.player.getEyePos());
      double yaw = Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0;
      double pitch = -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength()));
      double dYaw = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
      double dPitch = pitch - mc.player.getPitch();
      return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
   }

   private static boolean isNaked(PlayerEntity player) {
      return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
         && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
         && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
         && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
   }

   private Set<AutoMineModule.CheckPos> collectCandidates(boolean inBedrock) {
      Set<AutoMineModule.CheckPos> candidates = new HashSet<>();
      this.terrainFight = !inBedrock && this.computeTerrainFight();
      if (inBedrock) {
         this.addBedrockCandidates(candidates);
      } else {
         this.addNormalCandidates(candidates);
      }

      return candidates;
   }

   private AutoMineModule.CityBlock findCityBlock(DoubleMineModule mine, BlockPos exclude) {
      return this.findCityBlock(mine, exclude, null);
   }

   private AutoMineModule.CityBlock findCityBlock(DoubleMineModule mine, BlockPos exclude, Boolean feetOnly) {
      boolean inBedrock = this.inBedrockCase();
      AutoMineModule.CityBlock best = null;

      for (AutoMineModule.CheckPos candidate : this.collectCandidates(inBedrock)) {
         if (!candidate.pos.equals(exclude) && (feetOnly == null || candidate.type == AutoMineModule.CheckType.FEET == feetOnly)) {
            double score = this.evaluate(mine, candidate, inBedrock);
            if (score != -1000.0 && (best == null || score > best.score)) {
               best = new AutoMineModule.CityBlock(candidate.pos, score, candidate.type);
            }
         }
      }

      return best;
   }

   private List<AutoMineModule.ScoredPos> scoreCandidates(DoubleMineModule mine) {
      boolean inBedrock = this.inBedrockCase();
      List<AutoMineModule.ScoredPos> scored = new ArrayList<>();

      for (AutoMineModule.CheckPos candidate : this.collectCandidates(inBedrock)) {
         double score = this.evaluate(mine, candidate, inBedrock);
         if (score != -1000.0) {
            scored.add(new AutoMineModule.ScoredPos(candidate.pos, score));
         }
      }

      return List.copyOf(scored);
   }

   private double evaluate(DoubleMineModule mine, AutoMineModule.CheckPos candidate, boolean inBedrock) {
      BlockPos pos = candidate.pos;
      BlockState state = mc.world.getBlockState(pos);
      boolean goodRebreak = this.isGoodRebreak(mine, pos, inBedrock);
      if (!this.canBreak(pos, state) && !goodRebreak) {
         return -1000.0;
      }

      if (!mine.inMineRange(pos)) {
         return -1000.0;
      }

      if (this.claimedByOther(mine, pos)) {
         return -1000.0;
      }

      double score = inBedrock ? this.scoreBedrock(candidate) : this.scoreNormal(candidate);
      if (goodRebreak) {
         score += inBedrock && state.isAir() ? 10.0 : 40.0;
      }

      return score + this.comboFollowupScore(pos);
   }

   private double comboFollowupScore(BlockPos minedPos) {
      if (this.comboScoring.getValue() && this.targetPlayer != null) {
         double bestDamage = this.potentialCrystalDamage(minedPos, true);

         for (Direction direction : HORIZONTAL) {
            bestDamage = Math.max(bestDamage, this.potentialCrystalDamage(minedPos.offset(direction), false));
         }

         double score = bestDamage * 5.0;
         if (bestDamage >= this.targetPlayer.getHealth() + this.targetPlayer.getAbsorptionAmount()) {
            score += 200.0;
         }

         return score;
      } else {
         return 0.0;
      }
   }

   private double potentialCrystalDamage(BlockPos airPos, boolean minedCell) {
      BlockState base = mc.world.getBlockState(airPos.down());
      if (!base.isOf(Blocks.OBSIDIAN) && !base.isOf(Blocks.BEDROCK)) {
         return 0.0;
      }

      BlockState space = mc.world.getBlockState(airPos);
      if (!minedCell && !space.isAir() && !space.isReplaceable()) {
         return 0.0;
      }

      Vec3d source = new Vec3d(airPos.getX() + 0.5, airPos.getY(), airPos.getZ() + 0.5);
      return ExplosionMath.damage(
         this.targetPlayer.squaredDistanceTo(source),
         ExplosionMath.exposureIgnoring(mc.world, source, this.targetPlayer, minedCell ? airPos : null),
         this.targetPlayer.getArmor(),
         this.targetPlayer.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
         ExplosionMath.resistanceFactor(this.targetPlayer),
         ExplosionMath.protectionPoints(this.targetPlayer),
         mc.world.getDifficulty().getId()
      );
   }

   private void addNormalCandidates(Set<AutoMineModule.CheckPos> out) {
      Box feetBox = this.targetFeetBox();

      for (BlockPos raw : iterate(feetBox)) {
         out.add(new AutoMineModule.CheckPos(raw.toImmutable(), AutoMineModule.CheckType.FEET));
      }

      for (BlockPos raw : iterate(feetBox)) {
         BlockPos feet = raw.toImmutable();

         for (Direction dir : HORIZONTAL) {
            BlockPos surround = feet.offset(dir);
            if (!this.isBlockInFeet(surround)) {
               out.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckType.SURROUND));
               if (this.underMine.getValue()) {
                  BlockPos base = surround.down();
                  if (!this.isCrystalBlock(base) && this.canBreak(base, mc.world.getBlockState(base))) {
                     out.add(new AutoMineModule.CheckPos(base, AutoMineModule.CheckType.TERRAIN_BASE));
                  }
               }

               switch ((AutoMineModule.Extend)this.extend.getValue()) {
                  case NONE:
                  default:
                     break;
                  case LONG:
                     out.add(new AutoMineModule.CheckPos(surround.offset(dir), AutoMineModule.CheckType.EXTEND));
                     break;
                  case CORNER:
                     out.add(new AutoMineModule.CheckPos(surround.offset(cornerDirection(dir)), AutoMineModule.CheckType.EXTEND));
               }
            }
         }
      }
   }

   private boolean computeTerrainFight() {
      for (BlockPos feet : iterate(this.targetFeetBox())) {
         for (Direction dir : HORIZONTAL) {
            BlockPos neighbour = feet.offset(dir);
            if (!this.isBlockInFeet(neighbour) && this.isCrystalBlock(neighbour.down())) {
               return false;
            }
         }
      }

      return true;
   }

   private void addBedrockCandidates(Set<AutoMineModule.CheckPos> out) {
      boolean crawling = this.targetPlayer.isCrawling();

      for (BlockPos raw : iterate(this.targetFeetBox())) {
         BlockPos pos = raw.toImmutable();
         BlockPos headPos = crawling ? pos : pos.up();
         BlockPos aboveHeadPos = headPos.up();
         if (this.canBreak(headPos, mc.world.getBlockState(headPos))) {
            out.add(new AutoMineModule.CheckPos(headPos, AutoMineModule.CheckType.FACE_PLACE));
         } else if (this.canBreak(aboveHeadPos, mc.world.getBlockState(aboveHeadPos))) {
            out.add(new AutoMineModule.CheckPos(aboveHeadPos, AutoMineModule.CheckType.HEAD));
         }

         if (!mc.world.getBlockState(pos.down()).isOf(Blocks.BEDROCK)) {
            BlockPos center = pos.down();
            boolean plusRemaining = false;

            for (Direction dir : HORIZONTAL) {
               BlockPos side = center.offset(dir);
               if (this.canBreak(side, mc.world.getBlockState(side)) && (!this.isCrystalBlock(side) || !mc.world.getBlockState(side.up()).isAir())) {
                  out.add(new AutoMineModule.CheckPos(side, AutoMineModule.CheckType.BELOW));
                  plusRemaining = true;
               }
            }

            if (!plusRemaining && this.canBreak(center, mc.world.getBlockState(center))) {
               out.add(new AutoMineModule.CheckPos(center, AutoMineModule.CheckType.BELOW));
            }
         }

         out.add(new AutoMineModule.CheckPos(pos, AutoMineModule.CheckType.SURROUND));

         for (Direction dir : HORIZONTAL) {
            BlockPos surround = pos.offset(dir);
            if (!this.isBlockInFeet(surround)) {
               out.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckType.SURROUND));
            }
         }
      }
   }

   private double scoreNormal(AutoMineModule.CheckPos candidate) {
      BlockPos pos = candidate.pos;
      double score = 0.0;
      if (candidate.type == AutoMineModule.CheckType.FEET) {
         if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) {
            return -1000.0;
         }

         if (this.isPhased(this.targetPlayer)) {
            score += 200.0;
         } else if (mc.world.getBlockState(pos.up()).isOf(Blocks.OBSIDIAN)) {
            score += 100.0;
         } else {
            score += 50.0;
         }
      } else {
         BlockState selfHead = mc.world.getBlockState(mc.player.getBlockPos().up());
         if (pos.equals(mc.player.getBlockPos()) && (selfHead.isOf(Blocks.OBSIDIAN) || selfHead.isOf(Blocks.BEDROCK))) {
            return -1000.0;
         }

         if (candidate.type == AutoMineModule.CheckType.SURROUND) {
            BlockState down = mc.world.getBlockState(pos.down());
            if (down.isAir()) {
               score += 35.0;
            } else if (!down.isOf(Blocks.OBSIDIAN) && !down.isOf(Blocks.BEDROCK)) {
               score += 10.0;
            } else {
               score += 55.0;
               if (this.targetPlayer.squaredDistanceTo(Vec3d.ofCenter(pos)) < 4.0) {
                  for (Direction dir : HORIZONTAL) {
                     BlockPos beside = pos.offset(dir);
                     if (this.getBlockStateIgnoring(beside).isAir() && this.isCrystalBlock(beside.down())) {
                        score += 25.0;
                        break;
                     }
                  }
               }
            }
         }

         if (candidate.type == AutoMineModule.CheckType.EXTEND) {
            score += 20.0;
         }

         if (candidate.type == AutoMineModule.CheckType.TERRAIN_BASE) {
            score += this.terrainFight ? 30.0 : 1.0;
         }
      }

      score += 10.0 / this.targetPlayer.getEntityPos().distanceTo(Vec3d.ofCenter(pos));
      Vec3d velocity = this.targetPlayer.getVelocity();
      if (velocity.horizontalLengthSquared() > 0.001) {
         Vec3d moveDir = new Vec3d(velocity.x, 0.0, velocity.z).normalize();
         Vec3d toBlock = Vec3d.ofCenter(pos).subtract(this.targetPlayer.getEntityPos()).normalize();
         double dot = moveDir.dotProduct(toBlock);
         if (dot > 0.0) {
            score += dot * 5.0;
         }
      }

      return score;
   }

   private double scoreBedrock(AutoMineModule.CheckPos candidate) {
      BlockPos pos = candidate.pos;
      BlockPos feet = this.targetPlayer.getBlockPos();
      double score = 0.0;
      if (pos.getY() == feet.getY() + 2 || pos.getY() == feet.getY() - 1) {
         score += 10.0;
      }

      if (this.feetCellCount() == 1 && !mc.world.getBlockState(feet.up()).isOf(Blocks.BEDROCK)) {
         if (pos.equals(feet.up())) {
            score += 20.0;
         } else {
            for (Direction dir : HORIZONTAL) {
               if (feet.up().offset(dir).equals(pos)) {
                  score += 7.5;
                  break;
               }
            }
         }
      }

      if (candidate.type == AutoMineModule.CheckType.BELOW) {
         score += pos.equals(feet.down()) ? 25.0 : 30.0;
      }

      return score + 10.0 / this.targetPlayer.getEntityPos().distanceTo(Vec3d.ofCenter(pos));
   }

   private DoubleMineModule miner() {
      if (mc.player != null && mc.world != null) {
         DoubleMineModule mine = Homovore2Client.modules().get(DoubleMineModule.class);
         return mine != null && mine.isAvailable() ? mine : null;
      } else {
         return null;
      }
   }

   private Box targetFeetBox() {
      return feetBox(this.targetPlayer);
   }

   private static Box feetBox(PlayerEntity player) {
      Box box = player.getBoundingBox().contract(0.01, 0.1, 0.01);
      double feetY = player.getY();
      return new Box(box.minX, feetY, box.minZ, box.maxX, feetY + 0.1, box.maxZ);
   }

   private boolean isPhased(PlayerEntity player) {
      Box playerBox = player.getBoundingBox().contract(1.0E-4);

      for (BlockPos pos : iterate(playerBox)) {
         BlockState state = mc.world.getBlockState(pos);

         for (Box shape : state.getCollisionShape(mc.world, pos).getBoundingBoxes()) {
            if (shape.offset(pos.getX(), pos.getY(), pos.getZ()).intersects(playerBox)) {
               return true;
            }
         }
      }

      return false;
   }

   private AutoMineModule.CrawlPair selectCrawlPair(DoubleMineModule mine) {
      List<BlockPos> phased = this.phasedCrawlCells();
      Set<BlockPos> hitbox = this.crawlHitboxCells();
      double yaw = Math.toRadians(this.targetPlayer.getYaw());
      double lookX = -Math.sin(yaw);
      double lookZ = Math.cos(yaw);
      if (phased.isEmpty()) {
         return this.selectUnphasedCrawlPair(mine, hitbox, lookX, lookZ);
      }

      List<BlockPos> valid = new ArrayList<>();

      for (BlockPos pos : phased) {
         if (this.canBreak(pos, mc.world.getBlockState(pos)) && mine.inMineRange(pos) && !this.claimedByOther(mine, pos)) {
            valid.add(pos);
         }
      }

      Set<BlockPos> validSet = new HashSet<>(valid);
      AutoMineModule.CrawlPair best = null;
      double bestYaw = -Double.MAX_VALUE;
      double bestDamage = -1.0;

      for (BlockPos delayed : valid) {
         double yawScore = this.facingScore(delayed, lookX, lookZ);
         BlockPos rebreak = null;
         double rebreakDamage = -1.0;

         for (Direction direction : HORIZONTAL) {
            BlockPos adjacent = delayed.offset(direction);
            boolean eligible = phased.size() <= 1 ? !hitbox.contains(adjacent) : validSet.contains(adjacent);
            if (eligible && this.validCrawlCrystalMine(mine, adjacent)) {
               double damage = this.potentialCrystalDamage(adjacent, true);
               if (!(damage <= 1.0) && !(damage <= rebreakDamage)) {
                  rebreak = adjacent;
                  rebreakDamage = damage;
               }
            }
         }

         double damage = Math.max(rebreakDamage, 0.0);
         if (yawScore > bestYaw + 1.0E-6 || Math.abs(yawScore - bestYaw) <= 1.0E-6 && damage > bestDamage) {
            bestYaw = yawScore;
            bestDamage = damage;
            best = new AutoMineModule.CrawlPair(delayed, rebreak, this.potentialCrystalDamage(delayed, true), damage);
         }
      }

      return best;
   }

   private AutoMineModule.CrawlPair selectUnphasedCrawlPair(DoubleMineModule mine, Set<BlockPos> hitbox, double lookX, double lookZ) {
      if (hitbox.isEmpty()) {
         return null;
      }

      int minX = hitbox.stream().mapToInt(Vec3i::getX).min().orElse(0) - 2;
      int maxX = hitbox.stream().mapToInt(Vec3i::getX).max().orElse(0) + 2;
      int minZ = hitbox.stream().mapToInt(Vec3i::getZ).min().orElse(0) - 2;
      int maxZ = hitbox.stream().mapToInt(Vec3i::getZ).max().orElse(0) + 2;
      int y = MathHelper.floor(this.targetPlayer.getBoundingBox().minY);
      List<BlockPos> candidates = new ArrayList<>();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (!hitbox.contains(candidate) && this.validCrawlCrystalMine(mine, candidate)) {
               candidates.add(candidate);
            }
         }
      }

      AutoMineModule.CrawlPair best = null;
      double bestCombinedDamage = -1.0;
      double bestYaw = -Double.MAX_VALUE;

      for (int firstIndex = 0; firstIndex < candidates.size(); firstIndex++) {
         BlockPos first = candidates.get(firstIndex);

         for (int secondIndex = firstIndex + 1; secondIndex < candidates.size(); secondIndex++) {
            BlockPos second = candidates.get(secondIndex);
            if (cardinallyAdjacent(first, second)) {
               double firstDamage = this.potentialCrystalDamage(first, true);
               double secondDamage = this.potentialCrystalDamage(second, true);
               if (!(firstDamage <= 1.0) && !(secondDamage <= 1.0)) {
                  BlockPos delayed = this.facingScore(first, lookX, lookZ) >= this.facingScore(second, lookX, lookZ) ? first : second;
                  BlockPos rebreak = delayed.equals(first) ? second : first;
                  double combinedDamage = firstDamage + secondDamage;
                  double yawScore = this.facingScore(delayed, lookX, lookZ);
                  if (combinedDamage > bestCombinedDamage + 1.0E-4 || Math.abs(combinedDamage - bestCombinedDamage) <= 1.0E-4 && yawScore > bestYaw) {
                     bestCombinedDamage = combinedDamage;
                     bestYaw = yawScore;
                     best = new AutoMineModule.CrawlPair(
                        delayed, rebreak, delayed.equals(first) ? firstDamage : secondDamage, rebreak.equals(first) ? firstDamage : secondDamage
                     );
                  }
               }
            }
         }
      }

      return best;
   }

   private List<BlockPos> phasedCrawlCells() {
      Box playerBox = this.targetPlayer.getBoundingBox().contract(1.0E-4);
      List<BlockPos> cells = new ArrayList<>();

      for (BlockPos raw : iterate(playerBox)) {
         BlockPos pos = raw.toImmutable();
         BlockState state = mc.world.getBlockState(pos);

         for (Box shape : state.getCollisionShape(mc.world, pos).getBoundingBoxes()) {
            if (shape.offset(pos.getX(), pos.getY(), pos.getZ()).intersects(playerBox)) {
               cells.add(pos);
               break;
            }
         }
      }

      return cells;
   }

   private Set<BlockPos> crawlHitboxCells() {
      Set<BlockPos> cells = new HashSet<>();

      for (BlockPos pos : iterate(this.targetPlayer.getBoundingBox().contract(1.0E-4))) {
         cells.add(pos.toImmutable());
      }

      return cells;
   }

   private boolean validCrawlCrystalMine(DoubleMineModule mine, BlockPos pos) {
      return this.canBreak(pos, mc.world.getBlockState(pos))
         && mine.inMineRange(pos)
         && !this.claimedByOther(mine, pos)
         && this.isCrystalBlock(pos.down())
         && this.crystalIntersectsTarget(pos);
   }

   private boolean crystalIntersectsTarget(BlockPos airPos) {
      double x = airPos.getX() + 0.5;
      double z = airPos.getZ() + 0.5;
      return new Box(x - 1.0, airPos.getY(), z - 1.0, x + 1.0, airPos.getY() + 2.0, z + 1.0).intersects(this.targetPlayer.getBoundingBox());
   }

   private double facingScore(BlockPos pos, double lookX, double lookZ) {
      Vec3d offset = Vec3d.ofCenter(pos).subtract(this.targetPlayer.getEntityPos());
      return offset.x * lookX + offset.z * lookZ;
   }

   private static boolean cardinallyAdjacent(BlockPos first, BlockPos second) {
      return first.getY() == second.getY() && Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ()) == 1;
   }

   private boolean targetHeadCovered() {
      boolean crawling = this.targetPlayer.isCrawling();

      for (BlockPos feet : iterate(this.targetFeetBox())) {
         BlockPos head = crawling ? feet : feet.up();
         if (this.isBlocked(head, mc.world.getBlockState(head))) {
            return true;
         }
      }

      return false;
   }

   private int feetCellCount() {
      int count = 0;

      for (BlockPos ignored : iterate(this.targetFeetBox())) {
         count++;
      }

      return count;
   }

   private boolean inBedrockCase() {
      for (BlockPos pos : iterate(this.targetFeetBox())) {
         if (mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
            return true;
         }
      }

      return false;
   }

   private boolean isBlockInFeet(BlockPos pos) {
      for (BlockPos feet : iterate(this.targetFeetBox())) {
         if (pos.equals(feet)) {
            return true;
         }
      }

      return false;
   }

   private boolean isCrystalBlock(BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
   }

   private BlockState getBlockStateIgnoring(BlockPos pos) {
      return pos.equals(this.ignorePos) ? Blocks.AIR.getDefaultState() : mc.world.getBlockState(pos);
   }

   private boolean canBreak(BlockPos pos, BlockState state) {
      if (!state.isAir() && !state.isOf(Blocks.FIRE) && !state.isOf(Blocks.SOUL_FIRE)) {
         if (this.avoidPistonCrystal.getValue()) {
            PistonCrystalModule pistonCrystal = Homovore2Client.modules().get(PistonCrystalModule.class);
            if (pistonCrystal != null && pistonCrystal.isOwnPistonBlock(pos)) {
               return false;
            }
         }

         return state.getHardness(mc.world, pos) >= 0.0F;
      } else {
         return false;
      }
   }

   private static Direction cornerDirection(Direction dir) {
      return switch (dir) {
         case NORTH -> Direction.EAST;
         case SOUTH -> Direction.WEST;
         case EAST -> Direction.NORTH;
         default -> Direction.SOUTH;
      };
   }

   private static Iterable<BlockPos> iterate(Box box) {
      return BlockPos.iterate(
         MathHelper.floor(box.minX),
         MathHelper.floor(box.minY),
         MathHelper.floor(box.minZ),
         MathHelper.floor(box.maxX),
         MathHelper.floor(box.maxY),
         MathHelper.floor(box.maxZ)
      );
   }

   public enum AntiSurround {
      NONE,
      INNER,
      OUTER,
      AUTO;
   }

   private record AntiSurroundPlacement(CrystalPlacerModule crystal, BlockPos airPos, boolean outer) {
   }

   public enum AntiSwim {
      NONE,
      ALWAYS,
      MINE,
      MINE_OR_SWIM;
   }

   private record CheckPos(BlockPos pos, AutoMineModule.CheckType type) {
      @Override
      public boolean equals(Object other) {
         return other instanceof AutoMineModule.CheckPos check && check.pos.equals(this.pos);
      }

      @Override
      public int hashCode() {
         return this.pos.hashCode();
      }
   }

   private enum CheckType {
      FEET,
      SURROUND,
      EXTEND,
      FACE_PLACE,
      HEAD,
      BELOW,
      TERRAIN_BASE;
   }

   private record CityBlock(BlockPos pos, double score, AutoMineModule.CheckType type) {
   }

   private record CrawlPair(BlockPos delayed, BlockPos rebreak, double delayedDamage, double rebreakDamage) {
   }

   public enum Extend {
      NONE,
      LONG,
      CORNER;
   }

   private record GlassCompletion(long generation, BlockPos pos, boolean queued) {
   }

   public enum Priority {
      ANGLE,
      DISTANCE;
   }

   private record ScoredPos(BlockPos pos, double score) {
   }
}
