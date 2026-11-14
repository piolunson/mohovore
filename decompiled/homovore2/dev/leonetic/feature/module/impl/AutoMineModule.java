package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.network.NetworkAction;
import dev.leonetic.util.world.ExplosionMath;
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
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1542;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2382;
import net.minecraft.class_243;
import net.minecraft.class_259;
import net.minecraft.class_2680;
import net.minecraft.class_3532;
import net.minecraft.class_4184;
import net.minecraft.class_5134;

public final class AutoMineModule extends Module {
   private static final double INVALID_SCORE = -1000.0;
   private static final int GLASS_REBREAK_HOLD_TICKS = 5;
   private static final class_2350[] HORIZONTAL = new class_2350[]{
      class_2350.field_11043, class_2350.field_11035, class_2350.field_11034, class_2350.field_11039
   };
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
   private final Consumer<class_2338> finishListener = this::onMineFinish;
   private final Function<class_2338, NetworkAction> finishActionProvider = this::prepareMineFinishAction;
   private class_1657 targetPlayer;
   private class_2338 ignorePos;
   private boolean terrainFight;
   private long lastOuterPlaceMillis;
   private List<AutoMineModule.ScoredPos> debugCandidates = List.of();
   private class_2338 glassTargetPos;
   private int glassUsedAttempts;
   private class_2338 glassRenderPos;
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
         class_2338 selfHeadPos = mc.field_1724.method_24515().method_10084();
         class_2680 selfHeadBlock = mc.field_1687.method_8320(selfHeadPos);
         boolean breakSelfHead = this.canBreak(selfHeadPos, selfHeadBlock)
            && (selfHeadBlock.method_27852(class_2246.field_10540) || selfHeadBlock.method_27852(class_2246.field_22423));
         boolean prioritiseHead = false;
         if (breakSelfHead) {
            AutoMineModule.AntiSwim swim = this.antiSwim.getValue();
            boolean crawling = swim == AutoMineModule.AntiSwim.MINE_OR_SWIM && mc.field_1724.method_20448();
            boolean feetMined = (swim == AutoMineModule.AntiSwim.MINE || swim == AutoMineModule.AntiSwim.MINE_OR_SWIM)
               && this.isBlockBeingBroken(mc.field_1724.method_24515());
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
               if (this.targetPlayer.method_20448()) {
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
                     class_2338 surroundAbove = best.pos.method_10084();
                     if (best.type == AutoMineModule.CheckType.TERRAIN_BASE && !this.claimedByOther(mine, surroundAbove)) {
                        target2 = new AutoMineModule.CityBlock(surroundAbove, best.score - 10.0, AutoMineModule.CheckType.SURROUND);
                     } else {
                        target2 = this.findCityBlock(mine, best.pos);
                     }
                  }

                  if (this.debugScores.getValue()) {
                     this.debugCandidates = this.scoreCandidates(mine);
                  }

                  class_2338 activeRebreak = mine.rebreakPos();
                  boolean targetingFeet = target1 != null && target1.type == AutoMineModule.CheckType.FEET
                     || target2 != null && target2.type == AutoMineModule.CheckType.FEET;
                  boolean keepsActiveRebreak = activeRebreak != null
                     && (target1 != null && target1.pos.equals(activeRebreak) || target2 != null && target2.pos.equals(activeRebreak));
                  if ((targetingFeet || !keepsActiveRebreak) && (!mine.hasDelayedDestroy() || !mine.hasRebreakBlock())) {
                     Deque<class_2338> wanted = new ArrayDeque<>();
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
      return this.targetPlayer == null ? -1 : this.targetPlayer.method_5628();
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
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
               class_12180.method_75550(candidate.pos, class_12179.method_75536(new Color(255, 40, 40, alpha).getRGB(), 1.0F)).method_75533();
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
         class_2338 pos = mine.rebreakPos();
         if (pos != null && this.isGoodRebreak(mine, pos, this.inBedrockCase()) && this.isDesirableGlassPlacement(pos, crystal)) {
            if (!pos.equals(this.glassTargetPos)) {
               this.glassGeneration.incrementAndGet();
               this.glassTargetPos = pos.method_10062();
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

               if (!mc.field_1687.method_8320(pos).method_26215()) {
                  return true;
               }

               if (!InventoryUtil.find(class_1802.field_8280, InventoryUtil.HOTBAR_SCOPE).found()) {
                  return false;
               }

               long submission = this.nextGlassSubmission.incrementAndGet();
               if (!this.pendingGlassSubmission.compareAndSet(0L, submission)) {
                  return true;
               }

               long generation = this.glassGeneration.get();
               class_2338 immutablePos = pos.method_10062();
               mine.holdRebreak(immutablePos, 5);
               Homovore2Client.placements().enqueue(immutablePos, class_1802.field_8280).whenComplete((queued, failure) -> {
                  if (this.glassGeneration.get() == generation) {
                     this.glassCompletion.set(new AutoMineModule.GlassCompletion(generation, immutablePos, failure == null && Boolean.TRUE.equals(queued)));
                  }

                  this.pendingGlassSubmission.compareAndSet(submission, 0L);
               });
               return true;
            } else if (this.glassUsedAttempts > 0 && mc.field_1687.method_8320(pos).method_26215()) {
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

   private boolean itemInCrystalSpace(class_2338 airPos) {
      class_238 space = new class_238(airPos).method_1012(0.0, 1.0, 0.0);
      return !mc.field_1687.method_8333((class_1297)null, space, entity -> entity instanceof class_1542 && !entity.method_7325()).isEmpty();
   }

   private boolean isGoodRebreak(DoubleMineModule mine, class_2338 pos, boolean inBedrock) {
      if (mine.canRebreak() && pos.equals(mine.rebreakPos())) {
         if (inBedrock) {
            if (this.feetCellCount() != 1) {
               return false;
            }

            class_2338 feet = this.targetPlayer.method_24515();
            if (pos.equals(feet.method_10086(2))) {
               return true;
            }

            if (!mc.field_1687.method_8320(feet.method_10084()).method_26215()) {
               return false;
            }

            for (class_2350 direction : HORIZONTAL) {
               if (pos.equals(feet.method_10084().method_10093(direction))) {
                  return true;
               }
            }

            return false;
         } else {
            if (this.isBlockInFeet(pos)) {
               return false;
            }

            for (class_2338 feet : iterate(this.targetFeetBox())) {
               for (class_2350 direction : HORIZONTAL) {
                  if (pos.equals(feet.method_10093(direction)) && this.isCrystalBlock(pos.method_10074())) {
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

   private boolean isDesirableGlassPlacement(class_2338 airPos, CrystalPlacerModule crystal) {
      if (!this.isCrystalBlock(airPos.method_10074())) {
         return false;
      }

      class_243 eye = mc.field_1724.method_33571();
      double placeRange = 4.5;
      if (distanceSquaredToBox(eye, new class_238(airPos.method_10074())) > placeRange * placeRange) {
         return false;
      }

      double x = airPos.method_10263() + 0.5;
      double y = airPos.method_10264();
      double z = airPos.method_10260() + 0.5;
      double breakRange = 3.0;
      if (distanceSquaredToBox(eye, new class_238(x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0)) > breakRange * breakRange) {
         return false;
      }

      class_243 source = new class_243(x, y, z);
      double selfDamage = ExplosionMath.damage(
         mc.field_1724.method_5707(source),
         ExplosionMath.exposure(mc.field_1687, source, mc.field_1724),
         mc.field_1724.method_6096(),
         mc.field_1724.method_45325(class_5134.field_23725),
         ExplosionMath.resistanceFactor(mc.field_1724),
         ExplosionMath.protectionPoints(mc.field_1724),
         mc.field_1687.method_8407().method_5461()
      );
      if (mc.field_1724.method_31549().field_7480
         || !(selfDamage > crystal.maxSelfDamage.getValue()) && !(selfDamage + 1.0 >= mc.field_1724.method_6032() + mc.field_1724.method_6067())) {
         if (this.potentialCrystalDamage(airPos, true) >= crystal.minDamage.getValue()) {
            return true;
         }

         if (this.antiSurround.getValue() == AutoMineModule.AntiSurround.NONE) {
            return false;
         }

         for (class_2338 feet : iterate(this.targetFeetBox())) {
            for (class_2350 direction : HORIZONTAL) {
               if (airPos.equals(feet.method_10093(direction))) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static double distanceSquaredToBox(class_243 point, class_238 box) {
      double x = Math.clamp(point.field_1352, box.field_1323, box.field_1320);
      double y = Math.clamp(point.field_1351, box.field_1322, box.field_1325);
      double z = Math.clamp(point.field_1350, box.field_1321, box.field_1324);
      return point.method_1028(x, y, z);
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
            class_12180.method_75542(new class_238(this.glassRenderPos), class_12179.method_75537(line.getRGB(), 1.5F, fill.getRGB()), false).method_75533();
         }
      }
   }

   private static Color faded(Color color, double alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)Math.clamp(Math.round(color.getAlpha() * alpha), 0L, 255L));
   }

   private void onMineFinish(class_2338 pos) {
      AutoMineModule.AntiSurroundPlacement placement = this.findAntiSurroundPlacement(pos);
      if (placement != null && placement.crystal.preplace(placement.airPos) && placement.outer) {
         this.lastOuterPlaceMillis = System.currentTimeMillis();
      }
   }

   private NetworkAction prepareMineFinishAction(class_2338 pos) {
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

   private AutoMineModule.AntiSurroundPlacement findAntiSurroundPlacement(class_2338 pos) {
      if (mc.field_1724 != null && mc.field_1687 != null && this.targetPlayer != null) {
         AutoMineModule.AntiSurround mode = this.antiSurround.getValue();
         if (mode == AutoMineModule.AntiSurround.NONE) {
            return null;
         }

         CrystalPlacerModule crystal = Homovore2Client.modules().get(CrystalPlacerModule.class);
         if (crystal != null && crystal.isEnabled()) {
            boolean surroundBlock = false;

            label96:
            for (class_2338 feet : iterate(this.targetFeetBox())) {
               class_2350[] targetFeet = HORIZONTAL;
               int best = targetFeet.length;
               int bestDistance = 0;

               while (true) {
                  if (bestDistance < best) {
                     class_2350 dir = targetFeet[bestDistance];
                     if (!feet.method_10093(dir).equals(pos)) {
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
                  Set<class_2338> targetFeet = new HashSet<>();

                  for (class_2338 feetCell : iterate(this.targetFeetBox())) {
                     targetFeet.add(feetCell.method_10062());
                  }

                  class_2338 best = null;
                  double bestDistance = -1.0;

                  for (class_2350 direction : HORIZONTAL) {
                     class_2338 candidate = pos.method_10093(direction);
                     if (!targetFeet.contains(candidate)) {
                        class_2680 state = mc.field_1687.method_8320(candidate);
                        if ((state.method_26215() || state.method_45474()) && this.isCrystalBlock(candidate.method_10074())) {
                           class_238 crystalSpace = new class_238(candidate).method_1012(0.0, 1.0, 0.0);
                           if (mc.field_1687.method_8333((class_1297)null, crystalSpace, entity -> !entity.method_7325()).isEmpty()) {
                              double distance = this.targetPlayer.method_5707(class_243.method_24953(candidate));
                              if (distance > bestDistance) {
                                 bestDistance = distance;
                                 best = candidate.method_10062();
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
               : new AutoMineModule.AntiSurroundPlacement(crystal, pos.method_10062(), false);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static void request(DoubleMineModule mine, class_2338 pos) {
      if (!mine.isMining(pos)) {
         mine.requestBreak(pos);
      }
   }

   private boolean tryCrawlMine(DoubleMineModule mine) {
      if (this.crawlMine.getValue() && !mc.field_1724.method_18276()) {
         class_2338 feetPos = mc.field_1724.method_24515();
         class_2338 headPos = feetPos.method_10084();
         class_2680 feetBlock = mc.field_1687.method_8320(feetPos);
         if (!this.isBlocked(feetPos, feetBlock) || !this.isBlocked(headPos, mc.field_1687.method_8320(headPos))) {
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

   private boolean isBlocked(class_2338 pos, class_2680 state) {
      return !state.method_45474() && state.method_26218(mc.field_1687, pos) != class_259.method_1073();
   }

   private boolean isBlockBeingBroken(class_2338 pos) {
      return Homovore2Client.breaks().isBeingBroken(pos, mc.field_1724.method_5628());
   }

   private boolean claimedByOther(DoubleMineModule mine, class_2338 pos) {
      return !mine.isMining(pos) && this.isBlockBeingBroken(pos);
   }

   private class_1657 selectTarget() {
      double maxRange = this.range.getValue();
      class_243 eye = mc.field_1724.method_33571();
      class_1657 best = null;
      double bestMetric = Double.MAX_VALUE;
      boolean bestPhased = false;

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player != mc.field_1724
            && player.method_5805()
            && !player.method_7325()
            && !Homovore2Client.friends().isFriend(player)
            && !(player.method_73189().method_1022(eye) > maxRange)
            && (!this.ignoreNakeds.getValue() || !isNaked(player))) {
            boolean phased = this.isPhased(player);

            double metric = switch ((AutoMineModule.Priority)this.priority.getValue()) {
               case ANGLE -> this.angleTo(player);
               case DISTANCE -> mc.field_1724.method_5858(player);
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

   private double angleTo(class_1657 player) {
      class_243 delta = player.method_33571().method_1020(mc.field_1724.method_33571());
      double yaw = Math.toDegrees(Math.atan2(delta.field_1350, delta.field_1352)) - 90.0;
      double pitch = -Math.toDegrees(Math.atan2(delta.field_1351, delta.method_37267()));
      double dYaw = class_3532.method_15338(yaw - mc.field_1724.method_36454());
      double dPitch = pitch - mc.field_1724.method_36455();
      return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
   }

   private static boolean isNaked(class_1657 player) {
      return player.method_6118(class_1304.field_6169).method_7960()
         && player.method_6118(class_1304.field_6174).method_7960()
         && player.method_6118(class_1304.field_6172).method_7960()
         && player.method_6118(class_1304.field_6166).method_7960();
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

   private AutoMineModule.CityBlock findCityBlock(DoubleMineModule mine, class_2338 exclude) {
      return this.findCityBlock(mine, exclude, null);
   }

   private AutoMineModule.CityBlock findCityBlock(DoubleMineModule mine, class_2338 exclude, Boolean feetOnly) {
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
      class_2338 pos = candidate.pos;
      class_2680 state = mc.field_1687.method_8320(pos);
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
         score += inBedrock && state.method_26215() ? 10.0 : 40.0;
      }

      return score + this.comboFollowupScore(pos);
   }

   private double comboFollowupScore(class_2338 minedPos) {
      if (this.comboScoring.getValue() && this.targetPlayer != null) {
         double bestDamage = this.potentialCrystalDamage(minedPos, true);

         for (class_2350 direction : HORIZONTAL) {
            bestDamage = Math.max(bestDamage, this.potentialCrystalDamage(minedPos.method_10093(direction), false));
         }

         double score = bestDamage * 5.0;
         if (bestDamage >= this.targetPlayer.method_6032() + this.targetPlayer.method_6067()) {
            score += 200.0;
         }

         return score;
      } else {
         return 0.0;
      }
   }

   private double potentialCrystalDamage(class_2338 airPos, boolean minedCell) {
      class_2680 base = mc.field_1687.method_8320(airPos.method_10074());
      if (!base.method_27852(class_2246.field_10540) && !base.method_27852(class_2246.field_9987)) {
         return 0.0;
      }

      class_2680 space = mc.field_1687.method_8320(airPos);
      if (!minedCell && !space.method_26215() && !space.method_45474()) {
         return 0.0;
      }

      class_243 source = new class_243(airPos.method_10263() + 0.5, airPos.method_10264(), airPos.method_10260() + 0.5);
      return ExplosionMath.damage(
         this.targetPlayer.method_5707(source),
         ExplosionMath.exposureIgnoring(mc.field_1687, source, this.targetPlayer, minedCell ? airPos : null),
         this.targetPlayer.method_6096(),
         this.targetPlayer.method_45325(class_5134.field_23725),
         ExplosionMath.resistanceFactor(this.targetPlayer),
         ExplosionMath.protectionPoints(this.targetPlayer),
         mc.field_1687.method_8407().method_5461()
      );
   }

   private void addNormalCandidates(Set<AutoMineModule.CheckPos> out) {
      class_238 feetBox = this.targetFeetBox();

      for (class_2338 raw : iterate(feetBox)) {
         out.add(new AutoMineModule.CheckPos(raw.method_10062(), AutoMineModule.CheckType.FEET));
      }

      for (class_2338 raw : iterate(feetBox)) {
         class_2338 feet = raw.method_10062();

         for (class_2350 dir : HORIZONTAL) {
            class_2338 surround = feet.method_10093(dir);
            if (!this.isBlockInFeet(surround)) {
               out.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckType.SURROUND));
               if (this.underMine.getValue()) {
                  class_2338 base = surround.method_10074();
                  if (!this.isCrystalBlock(base) && this.canBreak(base, mc.field_1687.method_8320(base))) {
                     out.add(new AutoMineModule.CheckPos(base, AutoMineModule.CheckType.TERRAIN_BASE));
                  }
               }

               switch ((AutoMineModule.Extend)this.extend.getValue()) {
                  case NONE:
                  default:
                     break;
                  case LONG:
                     out.add(new AutoMineModule.CheckPos(surround.method_10093(dir), AutoMineModule.CheckType.EXTEND));
                     break;
                  case CORNER:
                     out.add(new AutoMineModule.CheckPos(surround.method_10093(cornerDirection(dir)), AutoMineModule.CheckType.EXTEND));
               }
            }
         }
      }
   }

   private boolean computeTerrainFight() {
      for (class_2338 feet : iterate(this.targetFeetBox())) {
         for (class_2350 dir : HORIZONTAL) {
            class_2338 neighbour = feet.method_10093(dir);
            if (!this.isBlockInFeet(neighbour) && this.isCrystalBlock(neighbour.method_10074())) {
               return false;
            }
         }
      }

      return true;
   }

   private void addBedrockCandidates(Set<AutoMineModule.CheckPos> out) {
      boolean crawling = this.targetPlayer.method_20448();

      for (class_2338 raw : iterate(this.targetFeetBox())) {
         class_2338 pos = raw.method_10062();
         class_2338 headPos = crawling ? pos : pos.method_10084();
         class_2338 aboveHeadPos = headPos.method_10084();
         if (this.canBreak(headPos, mc.field_1687.method_8320(headPos))) {
            out.add(new AutoMineModule.CheckPos(headPos, AutoMineModule.CheckType.FACE_PLACE));
         } else if (this.canBreak(aboveHeadPos, mc.field_1687.method_8320(aboveHeadPos))) {
            out.add(new AutoMineModule.CheckPos(aboveHeadPos, AutoMineModule.CheckType.HEAD));
         }

         if (!mc.field_1687.method_8320(pos.method_10074()).method_27852(class_2246.field_9987)) {
            class_2338 center = pos.method_10074();
            boolean plusRemaining = false;

            for (class_2350 dir : HORIZONTAL) {
               class_2338 side = center.method_10093(dir);
               if (this.canBreak(side, mc.field_1687.method_8320(side))
                  && (!this.isCrystalBlock(side) || !mc.field_1687.method_8320(side.method_10084()).method_26215())) {
                  out.add(new AutoMineModule.CheckPos(side, AutoMineModule.CheckType.BELOW));
                  plusRemaining = true;
               }
            }

            if (!plusRemaining && this.canBreak(center, mc.field_1687.method_8320(center))) {
               out.add(new AutoMineModule.CheckPos(center, AutoMineModule.CheckType.BELOW));
            }
         }

         out.add(new AutoMineModule.CheckPos(pos, AutoMineModule.CheckType.SURROUND));

         for (class_2350 dir : HORIZONTAL) {
            class_2338 surround = pos.method_10093(dir);
            if (!this.isBlockInFeet(surround)) {
               out.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckType.SURROUND));
            }
         }
      }
   }

   private double scoreNormal(AutoMineModule.CheckPos candidate) {
      class_2338 pos = candidate.pos;
      double score = 0.0;
      if (candidate.type == AutoMineModule.CheckType.FEET) {
         if (mc.field_1687.method_8320(pos).method_27852(class_2246.field_10343)) {
            return -1000.0;
         }

         if (this.isPhased(this.targetPlayer)) {
            score += 200.0;
         } else if (mc.field_1687.method_8320(pos.method_10084()).method_27852(class_2246.field_10540)) {
            score += 100.0;
         } else {
            score += 50.0;
         }
      } else {
         class_2680 selfHead = mc.field_1687.method_8320(mc.field_1724.method_24515().method_10084());
         if (pos.equals(mc.field_1724.method_24515()) && (selfHead.method_27852(class_2246.field_10540) || selfHead.method_27852(class_2246.field_9987))) {
            return -1000.0;
         }

         if (candidate.type == AutoMineModule.CheckType.SURROUND) {
            class_2680 down = mc.field_1687.method_8320(pos.method_10074());
            if (down.method_26215()) {
               score += 35.0;
            } else if (!down.method_27852(class_2246.field_10540) && !down.method_27852(class_2246.field_9987)) {
               score += 10.0;
            } else {
               score += 55.0;
               if (this.targetPlayer.method_5707(class_243.method_24953(pos)) < 4.0) {
                  for (class_2350 dir : HORIZONTAL) {
                     class_2338 beside = pos.method_10093(dir);
                     if (this.getBlockStateIgnoring(beside).method_26215() && this.isCrystalBlock(beside.method_10074())) {
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

      score += 10.0 / this.targetPlayer.method_73189().method_1022(class_243.method_24953(pos));
      class_243 velocity = this.targetPlayer.method_18798();
      if (velocity.method_37268() > 0.001) {
         class_243 moveDir = new class_243(velocity.field_1352, 0.0, velocity.field_1350).method_1029();
         class_243 toBlock = class_243.method_24953(pos).method_1020(this.targetPlayer.method_73189()).method_1029();
         double dot = moveDir.method_1026(toBlock);
         if (dot > 0.0) {
            score += dot * 5.0;
         }
      }

      return score;
   }

   private double scoreBedrock(AutoMineModule.CheckPos candidate) {
      class_2338 pos = candidate.pos;
      class_2338 feet = this.targetPlayer.method_24515();
      double score = 0.0;
      if (pos.method_10264() == feet.method_10264() + 2 || pos.method_10264() == feet.method_10264() - 1) {
         score += 10.0;
      }

      if (this.feetCellCount() == 1 && !mc.field_1687.method_8320(feet.method_10084()).method_27852(class_2246.field_9987)) {
         if (pos.equals(feet.method_10084())) {
            score += 20.0;
         } else {
            for (class_2350 dir : HORIZONTAL) {
               if (feet.method_10084().method_10093(dir).equals(pos)) {
                  score += 7.5;
                  break;
               }
            }
         }
      }

      if (candidate.type == AutoMineModule.CheckType.BELOW) {
         score += pos.equals(feet.method_10074()) ? 25.0 : 30.0;
      }

      return score + 10.0 / this.targetPlayer.method_73189().method_1022(class_243.method_24953(pos));
   }

   private DoubleMineModule miner() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         DoubleMineModule mine = Homovore2Client.modules().get(DoubleMineModule.class);
         return mine != null && mine.isAvailable() ? mine : null;
      } else {
         return null;
      }
   }

   private class_238 targetFeetBox() {
      return feetBox(this.targetPlayer);
   }

   private static class_238 feetBox(class_1657 player) {
      class_238 box = player.method_5829().method_35580(0.01, 0.1, 0.01);
      double feetY = player.method_23318();
      return new class_238(box.field_1323, feetY, box.field_1321, box.field_1320, feetY + 0.1, box.field_1324);
   }

   private boolean isPhased(class_1657 player) {
      class_238 playerBox = player.method_5829().method_1011(1.0E-4);

      for (class_2338 pos : iterate(playerBox)) {
         class_2680 state = mc.field_1687.method_8320(pos);

         for (class_238 shape : state.method_26220(mc.field_1687, pos).method_1090()) {
            if (shape.method_989(pos.method_10263(), pos.method_10264(), pos.method_10260()).method_994(playerBox)) {
               return true;
            }
         }
      }

      return false;
   }

   private AutoMineModule.CrawlPair selectCrawlPair(DoubleMineModule mine) {
      List<class_2338> phased = this.phasedCrawlCells();
      Set<class_2338> hitbox = this.crawlHitboxCells();
      double yaw = Math.toRadians(this.targetPlayer.method_36454());
      double lookX = -Math.sin(yaw);
      double lookZ = Math.cos(yaw);
      if (phased.isEmpty()) {
         return this.selectUnphasedCrawlPair(mine, hitbox, lookX, lookZ);
      }

      List<class_2338> valid = new ArrayList<>();

      for (class_2338 pos : phased) {
         if (this.canBreak(pos, mc.field_1687.method_8320(pos)) && mine.inMineRange(pos) && !this.claimedByOther(mine, pos)) {
            valid.add(pos);
         }
      }

      Set<class_2338> validSet = new HashSet<>(valid);
      AutoMineModule.CrawlPair best = null;
      double bestYaw = -Double.MAX_VALUE;
      double bestDamage = -1.0;

      for (class_2338 delayed : valid) {
         double yawScore = this.facingScore(delayed, lookX, lookZ);
         class_2338 rebreak = null;
         double rebreakDamage = -1.0;

         for (class_2350 direction : HORIZONTAL) {
            class_2338 adjacent = delayed.method_10093(direction);
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

   private AutoMineModule.CrawlPair selectUnphasedCrawlPair(DoubleMineModule mine, Set<class_2338> hitbox, double lookX, double lookZ) {
      if (hitbox.isEmpty()) {
         return null;
      }

      int minX = hitbox.stream().mapToInt(class_2382::method_10263).min().orElse(0) - 2;
      int maxX = hitbox.stream().mapToInt(class_2382::method_10263).max().orElse(0) + 2;
      int minZ = hitbox.stream().mapToInt(class_2382::method_10260).min().orElse(0) - 2;
      int maxZ = hitbox.stream().mapToInt(class_2382::method_10260).max().orElse(0) + 2;
      int y = class_3532.method_15357(this.targetPlayer.method_5829().field_1322);
      List<class_2338> candidates = new ArrayList<>();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            class_2338 candidate = new class_2338(x, y, z);
            if (!hitbox.contains(candidate) && this.validCrawlCrystalMine(mine, candidate)) {
               candidates.add(candidate);
            }
         }
      }

      AutoMineModule.CrawlPair best = null;
      double bestCombinedDamage = -1.0;
      double bestYaw = -Double.MAX_VALUE;

      for (int firstIndex = 0; firstIndex < candidates.size(); firstIndex++) {
         class_2338 first = candidates.get(firstIndex);

         for (int secondIndex = firstIndex + 1; secondIndex < candidates.size(); secondIndex++) {
            class_2338 second = candidates.get(secondIndex);
            if (cardinallyAdjacent(first, second)) {
               double firstDamage = this.potentialCrystalDamage(first, true);
               double secondDamage = this.potentialCrystalDamage(second, true);
               if (!(firstDamage <= 1.0) && !(secondDamage <= 1.0)) {
                  class_2338 delayed = this.facingScore(first, lookX, lookZ) >= this.facingScore(second, lookX, lookZ) ? first : second;
                  class_2338 rebreak = delayed.equals(first) ? second : first;
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

   private List<class_2338> phasedCrawlCells() {
      class_238 playerBox = this.targetPlayer.method_5829().method_1011(1.0E-4);
      List<class_2338> cells = new ArrayList<>();

      for (class_2338 raw : iterate(playerBox)) {
         class_2338 pos = raw.method_10062();
         class_2680 state = mc.field_1687.method_8320(pos);

         for (class_238 shape : state.method_26220(mc.field_1687, pos).method_1090()) {
            if (shape.method_989(pos.method_10263(), pos.method_10264(), pos.method_10260()).method_994(playerBox)) {
               cells.add(pos);
               break;
            }
         }
      }

      return cells;
   }

   private Set<class_2338> crawlHitboxCells() {
      Set<class_2338> cells = new HashSet<>();

      for (class_2338 pos : iterate(this.targetPlayer.method_5829().method_1011(1.0E-4))) {
         cells.add(pos.method_10062());
      }

      return cells;
   }

   private boolean validCrawlCrystalMine(DoubleMineModule mine, class_2338 pos) {
      return this.canBreak(pos, mc.field_1687.method_8320(pos))
         && mine.inMineRange(pos)
         && !this.claimedByOther(mine, pos)
         && this.isCrystalBlock(pos.method_10074())
         && this.crystalIntersectsTarget(pos);
   }

   private boolean crystalIntersectsTarget(class_2338 airPos) {
      double x = airPos.method_10263() + 0.5;
      double z = airPos.method_10260() + 0.5;
      return new class_238(x - 1.0, airPos.method_10264(), z - 1.0, x + 1.0, airPos.method_10264() + 2.0, z + 1.0).method_994(this.targetPlayer.method_5829());
   }

   private double facingScore(class_2338 pos, double lookX, double lookZ) {
      class_243 offset = class_243.method_24953(pos).method_1020(this.targetPlayer.method_73189());
      return offset.field_1352 * lookX + offset.field_1350 * lookZ;
   }

   private static boolean cardinallyAdjacent(class_2338 first, class_2338 second) {
      return first.method_10264() == second.method_10264()
         && Math.abs(first.method_10263() - second.method_10263()) + Math.abs(first.method_10260() - second.method_10260()) == 1;
   }

   private boolean targetHeadCovered() {
      boolean crawling = this.targetPlayer.method_20448();

      for (class_2338 feet : iterate(this.targetFeetBox())) {
         class_2338 head = crawling ? feet : feet.method_10084();
         if (this.isBlocked(head, mc.field_1687.method_8320(head))) {
            return true;
         }
      }

      return false;
   }

   private int feetCellCount() {
      int count = 0;

      for (class_2338 ignored : iterate(this.targetFeetBox())) {
         count++;
      }

      return count;
   }

   private boolean inBedrockCase() {
      for (class_2338 pos : iterate(this.targetFeetBox())) {
         if (mc.field_1687.method_8320(pos).method_27852(class_2246.field_9987)) {
            return true;
         }
      }

      return false;
   }

   private boolean isBlockInFeet(class_2338 pos) {
      for (class_2338 feet : iterate(this.targetFeetBox())) {
         if (pos.equals(feet)) {
            return true;
         }
      }

      return false;
   }

   private boolean isCrystalBlock(class_2338 pos) {
      class_2680 state = mc.field_1687.method_8320(pos);
      return state.method_27852(class_2246.field_10540) || state.method_27852(class_2246.field_9987);
   }

   private class_2680 getBlockStateIgnoring(class_2338 pos) {
      return pos.equals(this.ignorePos) ? class_2246.field_10124.method_9564() : mc.field_1687.method_8320(pos);
   }

   private boolean canBreak(class_2338 pos, class_2680 state) {
      if (!state.method_26215() && !state.method_27852(class_2246.field_10036) && !state.method_27852(class_2246.field_22089)) {
         if (this.avoidPistonCrystal.getValue()) {
            PistonCrystalModule pistonCrystal = Homovore2Client.modules().get(PistonCrystalModule.class);
            if (pistonCrystal != null && pistonCrystal.isOwnPistonBlock(pos)) {
               return false;
            }
         }

         return state.method_26214(mc.field_1687, pos) >= 0.0F;
      } else {
         return false;
      }
   }

   private static class_2350 cornerDirection(class_2350 dir) {
      return switch (dir) {
         case field_11043 -> class_2350.field_11034;
         case field_11035 -> class_2350.field_11039;
         case field_11034 -> class_2350.field_11043;
         default -> class_2350.field_11035;
      };
   }

   private static Iterable<class_2338> iterate(class_238 box) {
      return class_2338.method_10094(
         class_3532.method_15357(box.field_1323),
         class_3532.method_15357(box.field_1322),
         class_3532.method_15357(box.field_1321),
         class_3532.method_15357(box.field_1320),
         class_3532.method_15357(box.field_1325),
         class_3532.method_15357(box.field_1324)
      );
   }

   public enum AntiSurround {
      NONE,
      INNER,
      OUTER,
      AUTO;
   }

   private record AntiSurroundPlacement(CrystalPlacerModule crystal, class_2338 airPos, boolean outer) {
   }

   public enum AntiSwim {
      NONE,
      ALWAYS,
      MINE,
      MINE_OR_SWIM;
   }

   private record CheckPos(class_2338 pos, AutoMineModule.CheckType type) {
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

   private record CityBlock(class_2338 pos, double score, AutoMineModule.CheckType type) {
   }

   private record CrawlPair(class_2338 delayed, class_2338 rebreak, double delayedDamage, double rebreakDamage) {
   }

   public enum Extend {
      NONE,
      LONG,
      CORNER;
   }

   private record GlassCompletion(long generation, class_2338 pos, boolean queued) {
   }

   public enum Priority {
      ANGLE,
      DISTANCE;
   }

   private record ScoredPos(class_2338 pos, double score) {
   }
}
