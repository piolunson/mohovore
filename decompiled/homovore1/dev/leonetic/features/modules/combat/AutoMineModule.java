package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.TargetsModule;
import dev.leonetic.features.modules.world.SpeedMineModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.InteractionUtil;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.render.RenderUtil;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1542;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_259;
import net.minecraft.class_2620;
import net.minecraft.class_2680;
import net.minecraft.class_3532;

public class AutoMineModule extends Module {
   private static final double INVALID_SCORE = -1000.0;
   private static final class_2350[] HORIZONTAL = new class_2350[]{
      class_2350.field_11043, class_2350.field_11035, class_2350.field_11034, class_2350.field_11039
   };
   private final Setting<Double> range = this.num("Range", 6.5, 0.0, 7.0);
   private final Setting<AutoMineModule.SortPriority> targetPriority = this.mode("Priority", AutoMineModule.SortPriority.Angle);
   private final Setting<Boolean> ignoreNakeds = this.bool("IgnoreNakeds", true);
   private final Setting<AutoMineModule.ExtendBreakMode> extendBreakMode = this.mode("Extend", AutoMineModule.ExtendBreakMode.Long);
   private final Setting<Boolean> underMine = this.bool("UnderMine", true);
   private final Setting<Boolean> crawlMine = this.bool("CrawlMine", false);
   private final Setting<AutoMineModule.AntiSwimMode> antiSwim = this.mode("AntiSwim", AutoMineModule.AntiSwimMode.None);
   private final Setting<AutoMineModule.AntiSurroundMode> antiSurroundMode = this.mode("AntiSurround", AutoMineModule.AntiSurroundMode.Auto);
   private final Setting<Boolean> antiSurroundInnerSnap = this.bool("InnerSnap", true);
   private final Setting<Boolean> antiSurroundOuterSnap = this.bool("OuterSnap", true);
   private final Setting<Double> antiSurroundOuterCooldown = this.num("OuterCooldown", 0.1, 0.0, 1.0);
   private final Setting<Boolean> glassPush = this.bool("GlassPush", false);
   private final Setting<Integer> glassAttempts = this.num("GlassAttempts", 2, 1, 5);
   private final Setting<Boolean> glassRender = this.bool("GlassRender", true).setPage("Render");
   private final Setting<Float> glassFadeTime = this.num("GlassFadeTime", 0.5F, 0.05F, 2.0F).setPage("Render").setVisibility(v -> this.glassRender.getValue());
   private final Setting<Color> glassFillColor = this.color("GlassFillColor", 255, 255, 255, 50)
      .setPage("Render")
      .setVisibility(v -> this.glassRender.getValue());
   private final Setting<Color> glassOutlineColor = this.color("GlassOutlineColor", 255, 255, 255, 255)
      .setPage("Render")
      .setVisibility(v -> this.glassRender.getValue());
   private final Setting<Boolean> renderDebugScores = this.bool("DebugScores", false).setPage("Render");
   private class_1657 targetPlayer = null;
   private AutoMineModule.CityBlock target1 = null;
   private AutoMineModule.CityBlock target2 = null;
   private class_2338 ignorePos = null;
   private long lastOuterPlaceTime = 0L;
   private boolean isTerrainFight = false;
   private class_2338 glassTargetPos = null;
   private int glassUsedAttempts = 0;
   private class_2338 glassRenderPos = null;
   private long glassRenderStart = 0L;
   private final Long2LongOpenHashMap enemyBreaking = new Long2LongOpenHashMap();
   private static final long ENEMY_BREAK_TTL_MS = 1000L;
   private final SpeedMineModule.MineFinishListener finishListener = this::onMineFinish;

   public AutoMineModule() {
      super("AutoMine", "Automatically mines blocks around a target. Requires SpeedMine.", Module.Category.COMBAT);
      this.antiSurroundInnerSnap
         .setVisibility(
            v -> this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Auto
               || this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Inner
         );
      this.antiSurroundOuterSnap
         .setVisibility(
            v -> this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Auto
               || this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Outer
         );
      this.antiSurroundOuterCooldown
         .setVisibility(
            v -> this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Auto
               || this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Outer
         );
      this.glassAttempts.setVisibility(v -> this.glassPush.getValue());
   }

   @Override
   public void onEnable() {
      SpeedMineModule mine = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
      if (mine != null) {
         mine.addFinishListener(this.finishListener);
      }

      this.enemyBreaking.clear();
      this.lastOuterPlaceTime = 0L;
   }

   @Override
   public void onDisable() {
      SpeedMineModule mine = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
      if (mine != null) {
         mine.removeFinishListener(this.finishListener);
      }

      this.targetPlayer = null;
      this.target1 = this.target2 = null;
      this.ignorePos = null;
      this.enemyBreaking.clear();
      this.resetGlass();
      this.glassRenderPos = null;
   }

   @Subscribe
   private void onPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof class_2620 pkt) {
         if (mc.field_1724 == null || pkt.method_11280() != mc.field_1724.method_5628()) {
            int progress = pkt.method_11278();
            if (progress >= 0 && progress <= 9) {
               this.enemyBreaking.put(pkt.method_11277().method_10063(), System.currentTimeMillis());
            } else {
               this.enemyBreaking.remove(pkt.method_11277().method_10063());
            }
         }
      }
   }

   private boolean isBlockBeingBroken(class_2338 pos) {
      long ts = this.enemyBreaking.get(pos.method_10063());
      return ts != 0L && System.currentTimeMillis() - ts < 1000L;
   }

   private void onMineFinish(class_2338 pos) {
      if (!nullCheck() && this.targetPlayer != null) {
         AutoMineModule.AntiSurroundMode swMode = this.antiSurroundMode.getValue();
         if (swMode != AutoMineModule.AntiSurroundMode.None) {
            AutoCrystalModule crystal = Homovore.moduleManager.getModuleByClass(AutoCrystalModule.class);
            if (crystal != null && crystal.isEnabled()) {
               if (swMode == AutoMineModule.AntiSurroundMode.Auto || swMode == AutoMineModule.AntiSurroundMode.Outer) {
                  for (class_2350 dir : HORIZONTAL) {
                     class_2338 playerSurroundBlock = this.targetPlayer.method_24515().method_10093(dir);
                     if (pos.equals(playerSurroundBlock)) {
                        boolean outerReady = System.currentTimeMillis() - this.lastOuterPlaceTime > this.antiSurroundOuterCooldown.getValue() * 1000.0;
                        if (!outerReady) {
                           return;
                        }

                        class_238 checkBox = class_238.method_30048(class_243.method_24953(playerSurroundBlock), 2.5, 3.0, 2.5);
                        class_238 blockHitbox = new class_238(playerSurroundBlock);

                        for (class_2338 bp : iterate(checkBox)) {
                           if (mc.field_1687.method_8320(bp).method_26215()) {
                              class_2680 downState = mc.field_1687.method_8320(bp.method_10074());
                              if (downState.method_27852(class_2246.field_10540) || downState.method_27852(class_2246.field_9987)) {
                                 class_238 crystalPlaceHitbox = new class_238(
                                    bp.method_10263(),
                                    bp.method_10264(),
                                    bp.method_10260(),
                                    bp.method_10263() + 1,
                                    bp.method_10264() + 2,
                                    bp.method_10260() + 1
                                 );
                                 if (!this.intersectsEntity(crystalPlaceHitbox, e -> !e.method_7325())) {
                                    class_243 crystalPos = new class_243(bp.method_10263() + 0.5, bp.method_10264(), bp.method_10260() + 0.5);
                                    class_238 crystalHitbox = new class_238(
                                       crystalPos.field_1352 - 1.0,
                                       crystalPos.field_1351,
                                       crystalPos.field_1350 - 1.0,
                                       crystalPos.field_1352 + 1.0,
                                       crystalPos.field_1351 + 2.0,
                                       crystalPos.field_1350 + 1.0
                                    );
                                    if (crystalHitbox.method_994(blockHitbox)) {
                                       if (crystal.preplaceCrystal(bp.method_10062(), this.antiSurroundOuterSnap.getValue())) {
                                          this.lastOuterPlaceTime = System.currentTimeMillis();
                                       }

                                       return;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               if (swMode == AutoMineModule.AntiSurroundMode.Auto || swMode == AutoMineModule.AntiSurroundMode.Inner) {
                  for (class_2350 dir : HORIZONTAL) {
                     class_2338 playerSurroundBlock = this.targetPlayer.method_24515().method_10093(dir);
                     if (playerSurroundBlock.equals(pos)) {
                        crystal.preplaceCrystal(playerSurroundBlock, this.antiSurroundInnerSnap.getValue());
                     }
                  }
               }
            }
         }
      }
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         this.update();
      }
   }

   private void update() {
      SpeedMineModule mine = this.speedMine();
      if (mine != null) {
         if (!this.tryCrawlMine(mine)) {
            class_2680 selfFeetBlock = mc.field_1687.method_8320(mc.field_1724.method_24515());
            class_2680 selfHeadBlock = mc.field_1687.method_8320(mc.field_1724.method_24515().method_10084());
            class_2338 selfHeadPos = mc.field_1724.method_24515().method_10084();
            boolean shouldBreakSelfHeadBlock = this.canBreak(selfHeadPos, selfHeadBlock)
               && (selfHeadBlock.method_27852(class_2246.field_10540) || selfHeadBlock.method_27852(class_2246.field_22423));
            boolean prioHead = false;
            if (this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.Always && shouldBreakSelfHeadBlock) {
               mine.requestBreak(selfHeadPos);
               prioHead = true;
            }

            if (this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.MineOrSwim && mc.field_1724.method_20448() && shouldBreakSelfHeadBlock) {
               mine.requestBreak(selfHeadPos);
               prioHead = true;
            }

            if ((this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.Mine || this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.MineOrSwim)
               && this.isBlockBeingBroken(mc.field_1724.method_24515())
               && shouldBreakSelfHeadBlock) {
               mine.requestBreak(selfHeadPos);
               prioHead = true;
            }

            this.targetPlayer = this.selectTarget();
            if (this.targetPlayer != null) {
               this.handleGlassPush(mine);
               if (mine.getSecondaryPos() == null
                  || !selfHeadBlock.method_27852(class_2246.field_10540)
                  || !selfFeetBlock.method_26215()
                  || !selfHeadPos.equals(mine.getRebreakPos())) {
                  if (!prioHead) {
                     this.findTargetBlocks();
                     int free = 0;
                     if (mine.hasFreePrimary()) {
                        free++;
                     }

                     if (mine.hasFreeSecondary()) {
                        free++;
                     }

                     if (free != 0) {
                        Deque<class_2338> wanted = new ArrayDeque<>();
                        if (this.target1 != null && !mine.isMining(this.target1.blockPos)) {
                           wanted.add(this.target1.blockPos);
                        }

                        if (this.target2 != null && !mine.isMining(this.target2.blockPos)) {
                           wanted.add(this.target2.blockPos);
                        }

                        for (int sent = 0; !wanted.isEmpty() && sent < free; sent++) {
                           mine.requestBreak(wanted.remove());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean tryCrawlMine(SpeedMineModule mine) {
      if (!this.crawlMine.getValue()) {
         return false;
      } else if (mc.field_1724.method_18276()) {
         return false;
      } else {
         class_2338 feetPos = mc.field_1724.method_24515();
         class_2338 headPos = feetPos.method_10084();
         class_2680 feetBlock = mc.field_1687.method_8320(feetPos);
         class_2680 headBlock = mc.field_1687.method_8320(headPos);
         if (!this.isBlocked(feetPos, feetBlock) || !this.isBlocked(headPos, headBlock)) {
            return false;
         } else {
            return !InteractionUtil.canBreak(feetPos, feetBlock) ? false : mine.requestBreak(feetPos);
         }
      }
   }

   private boolean isBlocked(class_2338 pos, class_2680 state) {
      return !state.method_45474() && state.method_26218(mc.field_1687, pos) != class_259.method_1073();
   }

   private class_1657 selectTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      double rangeV = this.range.getValue();
      class_1657 best = null;
      double bestMetric = Double.MAX_VALUE;
      class_238 area = mc.field_1724.method_5829().method_1014(rangeV);

      for (class_1297 e : mc.field_1687.method_8335(mc.field_1724, area)) {
         if (e instanceof class_1657 player
            && player != mc.field_1724
            && player.method_5805()
            && !player.method_29504()
            && !player.method_7325()
            && (targets == null || targets.isValidTarget(player))
            && !(player.method_73189().method_1022(mc.field_1724.method_33571()) > rangeV)
            && (!this.ignoreNakeds.getValue() || !this.isNaked(player))) {
            double metric = switch ((AutoMineModule.SortPriority)this.targetPriority.getValue()) {
               case Angle -> this.angleTo(player);
               case Distance -> mc.field_1724.method_5858(player);
            };
            if (metric < bestMetric) {
               bestMetric = metric;
               best = player;
            }
         }
      }

      return best;
   }

   private double angleTo(class_1657 player) {
      float[] need = MathUtil.calcAngle(mc.field_1724.method_33571(), player.method_33571());
      double dYaw = MathUtil.wrapDegrees(need[0] - mc.field_1724.method_36454());
      double dPitch = need[1] - mc.field_1724.method_36455();
      return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
   }

   private boolean isNaked(class_1657 p) {
      return p.method_6118(class_1304.field_6169).method_7960()
         && p.method_6118(class_1304.field_6174).method_7960()
         && p.method_6118(class_1304.field_6172).method_7960()
         && p.method_6118(class_1304.field_6166).method_7960();
   }

   private void handleGlassPush(SpeedMineModule mine) {
      if (!this.glassPush.getValue()) {
         this.resetGlass();
      } else {
         AutoCrystalModule crystal = Homovore.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (crystal != null && crystal.isEnabled()) {
            class_2338 pos = mine.getRebreakPos();
            if (pos == null) {
               this.resetGlass();
            } else if (this.isGoodRebreak(pos, this.inBedrockCase()) && crystal.isDesirablePlacement(pos)) {
               if (!pos.equals(this.glassTargetPos)) {
                  this.glassTargetPos = pos.method_10062();
                  this.glassUsedAttempts = 0;
               }

               if (!this.itemInCrystalSpot(pos)) {
                  if (this.glassUsedAttempts > 0 && mc.field_1687.method_8320(pos).method_26215()) {
                     crystal.preplaceCrystal(pos, true);
                  }

                  this.glassUsedAttempts = 0;
               } else if (this.glassUsedAttempts < this.glassAttempts.getValue()) {
                  if (mc.field_1687.method_8320(pos).method_26215()) {
                     if (this.placeGlass(pos)) {
                        this.glassUsedAttempts++;
                        mine.holdRebreak(pos, 5);
                        this.glassRenderPos = pos.method_10062();
                        this.glassRenderStart = System.currentTimeMillis();
                     }
                  }
               }
            } else {
               this.resetGlass();
            }
         } else {
            this.resetGlass();
         }
      }
   }

   private boolean itemInCrystalSpot(class_2338 airPos) {
      class_238 box = new class_238(airPos).method_1012(0.0, 1.0, 0.0);

      for (class_1297 e : mc.field_1687.method_8335((class_1297)null, box)) {
         if (e instanceof class_1542) {
            return true;
         }
      }

      return false;
   }

   private boolean placeGlass(class_2338 pos) {
      Result glass = InventoryUtil.find(class_1802.field_8280, InventoryUtil.PLACE_SCOPE);
      if (!glass.found()) {
         return false;
      }

      if (!PlaceUtil.canPlace(pos)) {
         return false;
      }

      if (!Homovore.placementManager.enqueue(pos, class_1802.field_8280)) {
         return false;
      }

      Homovore.placementManager.flushQueue();
      return true;
   }

   private void resetGlass() {
      this.glassTargetPos = null;
      this.glassUsedAttempts = 0;
   }

   private void findTargetBlocks() {
      this.target1 = null;
      this.target2 = null;
      this.ignorePos = null;
      AutoMineModule.CityBlock best = this.findCityBlock(null);
      if (best != null) {
         this.target1 = best;
         this.ignorePos = this.target1.blockPos;
         if (best.type == AutoMineModule.CheckPosType.TerrainBase) {
            AutoMineModule.CityBlock above = new AutoMineModule.CityBlock();
            above.blockPos = best.blockPos.method_10084();
            above.score = best.score - 10.0;
            above.type = AutoMineModule.CheckPosType.Surround;
            this.target2 = above;
         } else {
            this.target2 = this.findCityBlock(this.target1.blockPos);
         }
      }
   }

   private AutoMineModule.CityBlock findCityBlock(class_2338 exclude) {
      if (this.targetPlayer == null) {
         return null;
      }

      boolean inBedrock = this.inBedrockCase();
      this.isTerrainFight = !inBedrock && this.computeTerrainFight();
      Set<AutoMineModule.CheckPos> checkPositions = new HashSet<>();
      if (inBedrock) {
         this.addBedrockCaseCheckPositions(checkPositions);
      } else {
         this.addNormalCaseCheckPositions(checkPositions);
      }

      AutoMineModule.CityBlock bestBlock = new AutoMineModule.CityBlock();
      boolean set = false;

      for (AutoMineModule.CheckPos pos : checkPositions) {
         if (!pos.blockPos.equals(exclude)) {
            double score = this.evalCheckPos(pos, inBedrock);
            if (score != -1000.0 && score > bestBlock.score) {
               bestBlock.score = score;
               bestBlock.blockPos = pos.blockPos;
               bestBlock.type = pos.type;
               set = true;
            }
         }
      }

      return set ? bestBlock : null;
   }

   private double evalCheckPos(AutoMineModule.CheckPos pos, boolean inBedrock) {
      SpeedMineModule mine = this.speedMine();
      if (mine == null) {
         return -1000.0;
      }

      class_2338 blockPos = pos.blockPos;
      class_2680 block = mc.field_1687.method_8320(blockPos);
      boolean goodRebreak = this.isGoodRebreak(blockPos, inBedrock);
      if (block.method_26215() && !goodRebreak) {
         return -1000.0;
      }

      if (!this.canBreak(blockPos, block) && !goodRebreak) {
         return -1000.0;
      }

      if (!mine.inMineRange(blockPos)) {
         return -1000.0;
      }

      double score = inBedrock ? this.scoreBedrock(pos) : this.scoreNormal(pos);
      if (score == -1000.0) {
         return -1000.0;
      }

      if (goodRebreak) {
         score += inBedrock && block.method_26215() ? 10.0 : 40.0;
      }

      return score;
   }

   private boolean isGoodRebreak(class_2338 blockPos, boolean inBedrock) {
      SpeedMineModule mine = this.speedMine();
      if (mine == null) {
         return false;
      }

      class_2338 rebreak = mine.getRebreakPos();
      if (rebreak == null || !blockPos.equals(rebreak)) {
         return false;
      }

      if (!inBedrock) {
         if (!blockPos.equals(this.targetPlayer.method_24515()) && !this.isBlockInFeet(blockPos)) {
            for (class_2350 dir : HORIZONTAL) {
               if (this.targetPlayer.method_24515().method_10093(dir).equals(blockPos)
                  && this.isCrystalBlock(this.targetPlayer.method_24515().method_10093(dir).method_10074())) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         boolean isSelfTrapBlock = false;

         for (class_2350 dir : HORIZONTAL) {
            if (this.targetPlayer.method_24515().method_10084().method_10093(dir).equals(blockPos)) {
               isSelfTrapBlock = true;
               break;
            }
         }

         boolean canFacePlace = mc.field_1687.method_8320(this.targetPlayer.method_24515().method_10084()).method_26215();
         return class_2338.method_29715(this.targetFeetBox()).count() == 1L
            && (blockPos.equals(this.targetPlayer.method_24515().method_10086(2)) || isSelfTrapBlock && canFacePlace);
      }
   }

   private void addNormalCaseCheckPositions(Set<AutoMineModule.CheckPos> checkPos) {
      class_238 feetBox = this.targetFeetBox();

      for (class_2338 raw : iterate(feetBox)) {
         checkPos.add(new AutoMineModule.CheckPos(raw.method_10062(), AutoMineModule.CheckPosType.Feet));
      }

      for (class_2338 raw : iterate(feetBox)) {
         class_2338 feet = raw.method_10062();

         for (class_2350 dir : HORIZONTAL) {
            class_2338 surround = feet.method_10093(dir);
            if (!this.isBlockInFeet(surround)) {
               checkPos.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckPosType.Surround));
               if (this.underMine.getValue()) {
                  class_2338 base = surround.method_10074();
                  if (!this.isCrystalBlock(base) && this.canBreak(base, mc.field_1687.method_8320(base))) {
                     checkPos.add(new AutoMineModule.CheckPos(base, AutoMineModule.CheckPosType.TerrainBase));
                  }
               }

               switch ((AutoMineModule.ExtendBreakMode)this.extendBreakMode.getValue()) {
                  case None:
                  default:
                     break;
                  case Long:
                     checkPos.add(new AutoMineModule.CheckPos(surround.method_10093(dir), AutoMineModule.CheckPosType.Extend));
                     break;
                  case Corner:
                     class_2350 perpDir = this.getCornerPerpDir(dir);
                     if (perpDir != null) {
                        checkPos.add(new AutoMineModule.CheckPos(surround.method_10093(perpDir), AutoMineModule.CheckPosType.Extend));
                     }
               }
            }
         }
      }
   }

   private boolean computeTerrainFight() {
      class_238 feetBox = this.targetFeetBox();

      for (class_2338 feet : iterate(feetBox)) {
         for (class_2350 dir : HORIZONTAL) {
            class_2338 neighbor = feet.method_10093(dir);
            if (!this.isBlockInFeet(neighbor) && this.isCrystalBlock(neighbor.method_10074())) {
               return false;
            }
         }
      }

      return true;
   }

   private void addBedrockCaseCheckPositions(Set<AutoMineModule.CheckPos> checkPos) {
      boolean isCrawling = this.targetPlayer.method_20448();

      for (class_2338 raw : iterate(this.targetFeetBox())) {
         class_2338 pos = raw.method_10062();
         class_2338 headPos = isCrawling ? pos : pos.method_10084();
         class_2338 aboveHeadPos = headPos.method_10084();
         if (this.canBreak(headPos, mc.field_1687.method_8320(headPos))) {
            checkPos.add(new AutoMineModule.CheckPos(headPos, AutoMineModule.CheckPosType.FacePlace));
         } else if (this.canBreak(aboveHeadPos, mc.field_1687.method_8320(aboveHeadPos))) {
            checkPos.add(new AutoMineModule.CheckPos(aboveHeadPos, AutoMineModule.CheckPosType.Head));
         }

         if (!mc.field_1687.method_8320(pos.method_10074()).method_27852(class_2246.field_9987)) {
            class_2338 center = pos.method_10074();
            boolean plusNotFinished = false;

            for (class_2350 dir : HORIZONTAL) {
               class_2338 surround = center.method_10093(dir);
               if (this.canBreak(surround, mc.field_1687.method_8320(surround))
                  && (!this.isCrystalBlock(surround) || !mc.field_1687.method_8320(surround.method_10084()).method_26215())) {
                  checkPos.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckPosType.Below));
                  plusNotFinished = true;
               }
            }

            if (!plusNotFinished && this.canBreak(center, mc.field_1687.method_8320(center))) {
               checkPos.add(new AutoMineModule.CheckPos(center, AutoMineModule.CheckPosType.Below));
            }
         }

         checkPos.add(new AutoMineModule.CheckPos(pos, AutoMineModule.CheckPosType.Surround));

         for (class_2350 dir : HORIZONTAL) {
            class_2338 surround = pos.method_10093(dir);
            if (!this.isBlockInFeet(surround)) {
               checkPos.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckPosType.Surround));
            }
         }
      }
   }

   private double scoreNormal(AutoMineModule.CheckPos pos) {
      class_2338 blockPos = pos.blockPos;
      double score = 0.0;
      class_2680 block = mc.field_1687.method_8320(blockPos);
      if (pos.type == AutoMineModule.CheckPosType.Feet) {
         class_2680 headBlock = mc.field_1687.method_8320(blockPos.method_10084());
         if (headBlock.method_27852(class_2246.field_10540)) {
            score += 100.0;
         } else {
            if (block.method_27852(class_2246.field_10343)) {
               return -1000.0;
            }

            score += 50.0;
         }
      } else {
         class_2680 selfHeadState = mc.field_1687.method_8320(mc.field_1724.method_24515().method_10084());
         if (blockPos.equals(mc.field_1724.method_24515())
            && (selfHeadState.method_27852(class_2246.field_10540) || selfHeadState.method_27852(class_2246.field_9987))) {
            return -1000.0;
         }

         if (pos.type == AutoMineModule.CheckPosType.Surround) {
            class_2680 down = mc.field_1687.method_8320(blockPos.method_10074());
            if (down.method_26215()) {
               score += 35.0;
            } else if (!down.method_27852(class_2246.field_10540) && !down.method_27852(class_2246.field_9987)) {
               score += 10.0;
            } else {
               score += 55.0;
               boolean isPosAntiSurround = false;

               for (class_2350 dir : HORIZONTAL) {
                  class_2338 antiSurroundBlockPos = blockPos.method_10093(dir);
                  if (this.targetPlayer.method_5707(class_243.method_24953(blockPos)) < 4.0
                     && this.getBlockStateIgnore(antiSurroundBlockPos).method_26215()
                     && this.isCrystalBlock(antiSurroundBlockPos.method_10074())) {
                     isPosAntiSurround = true;
                     break;
                  }
               }

               if (isPosAntiSurround) {
                  score += 25.0;
               }
            }
         }

         if (pos.type == AutoMineModule.CheckPosType.Extend) {
            score += 20.0;
         }

         if (pos.type == AutoMineModule.CheckPosType.TerrainBase) {
            score += this.isTerrainFight ? 30.0 : 1.0;
         }
      }

      double d = this.targetPlayer.method_73189().method_1022(class_243.method_24953(blockPos));
      score += 10.0 / d;
      class_243 velocity = this.targetPlayer.method_18798();
      if (velocity.method_37268() > 0.001) {
         class_243 moveDir = new class_243(velocity.field_1352, 0.0, velocity.field_1350).method_1029();
         class_243 toBlock = class_243.method_24953(blockPos).method_1020(this.targetPlayer.method_73189()).method_1029();
         double dot = moveDir.method_1026(toBlock);
         if (dot > 0.0) {
            score += dot * 5.0;
         }
      }

      return score;
   }

   private double scoreBedrock(AutoMineModule.CheckPos pos) {
      class_2338 blockPos = pos.blockPos;
      double score = 0.0;
      if (blockPos.method_10264() == this.targetPlayer.method_31478() + 2 || blockPos.method_10264() == this.targetPlayer.method_31478() - 1) {
         score += 10.0;
      }

      if (class_2338.method_29715(this.targetFeetBox()).count() == 1L) {
         boolean canMineFaceBlock = !mc.field_1687.method_8320(this.targetPlayer.method_24515().method_10084()).method_27852(class_2246.field_9987);
         if (canMineFaceBlock) {
            if (blockPos.equals(this.targetPlayer.method_24515().method_10084())) {
               score += 20.0;
            } else {
               boolean isSelfTrapBlock = false;

               for (class_2350 dir : HORIZONTAL) {
                  if (this.targetPlayer.method_24515().method_10084().method_10093(dir).equals(blockPos)) {
                     isSelfTrapBlock = true;
                     break;
                  }
               }

               if (isSelfTrapBlock) {
                  score += 7.5;
               }
            }
         }
      }

      if (pos.type == AutoMineModule.CheckPosType.Below) {
         score += blockPos.equals(this.targetPlayer.method_24515().method_10074()) ? 25.0 : 30.0;
      }

      double d = this.targetPlayer.method_73189().method_1022(class_243.method_24953(blockPos));
      return score + 10.0 / d;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck() && this.glassRender.getValue() && this.glassRenderPos != null) {
         long age = System.currentTimeMillis() - this.glassRenderStart;
         double fadeMs = this.glassFadeTime.getValue().floatValue() * 1000.0;
         if (age > fadeMs) {
            this.glassRenderPos = null;
         } else {
            double t = age / fadeMs;
            Color fc = this.glassFillColor.getValue();
            Color oc = this.glassOutlineColor.getValue();
            RenderUtil.drawBoxFilled(
               event.getMatrix(), this.glassRenderPos, new Color(fc.getRed(), fc.getGreen(), fc.getBlue(), (int)(fc.getAlpha() * (1.0 - t)))
            );
            RenderUtil.drawBox(
               event.getMatrix(), this.glassRenderPos, new Color(oc.getRed(), oc.getGreen(), oc.getBlue(), (int)(oc.getAlpha() * (1.0 - t))), 1.5F
            );
         }
      }
   }

   @Subscribe
   private void onRenderDebug(Render3DEvent event) {
      if (!nullCheck() && this.renderDebugScores.getValue() && this.targetPlayer != null) {
         if (this.speedMine() != null) {
            boolean inBedrock = this.inBedrockCase();
            this.isTerrainFight = !inBedrock && this.computeTerrainFight();
            Set<AutoMineModule.CheckPos> checkPos = new HashSet<>();
            if (inBedrock) {
               this.addBedrockCaseCheckPositions(checkPos);
            } else {
               this.addNormalCaseCheckPositions(checkPos);
            }

            double bestScore = 0.0;

            for (AutoMineModule.CheckPos cp : checkPos) {
               double s = this.evalCheckPos(cp, inBedrock);
               if (s != -1000.0 && s > bestScore) {
                  bestScore = s;
               }
            }

            if (!(bestScore <= 0.0)) {
               for (AutoMineModule.CheckPos cp : checkPos) {
                  double s = this.evalCheckPos(cp, inBedrock);
                  if (s != -1000.0) {
                     int alpha = (int)Math.min(200.0, Math.max(20.0, 200.0 * (s / bestScore)));
                     RenderUtil.drawBox(event.getMatrix(), cp.blockPos, new Color(255, 40, 40, alpha), 1.0F);
                  }
               }
            }
         }
      }
   }

   private SpeedMineModule speedMine() {
      SpeedMineModule m = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
      return m != null && m.isEnabled() ? m : null;
   }

   private class_238 targetFeetBox() {
      class_238 bb = this.targetPlayer.method_5829().method_35580(0.01, 0.1, 0.01);
      double feetY = this.targetPlayer.method_23318();
      return new class_238(bb.field_1323, feetY, bb.field_1321, bb.field_1320, feetY + 0.1, bb.field_1324);
   }

   private boolean inBedrockCase() {
      return class_2338.method_29715(this.targetFeetBox()).anyMatch(bp -> mc.field_1687.method_8320(bp).method_27852(class_2246.field_9987));
   }

   private boolean isBlockInFeet(class_2338 blockPos) {
      class_238 feetBox = this.targetFeetBox();

      for (class_2338 pos : class_2338.method_10094(
         class_3532.method_15357(feetBox.field_1323),
         class_3532.method_15357(feetBox.field_1322),
         class_3532.method_15357(feetBox.field_1321),
         class_3532.method_15357(feetBox.field_1320),
         class_3532.method_15357(feetBox.field_1325),
         class_3532.method_15357(feetBox.field_1324)
      )) {
         if (blockPos.equals(pos)) {
            return true;
         }
      }

      return false;
   }

   private boolean isCrystalBlock(class_2338 blockPos) {
      class_2680 s = mc.field_1687.method_8320(blockPos);
      return s.method_27852(class_2246.field_10540) || s.method_27852(class_2246.field_9987);
   }

   private class_2680 getBlockStateIgnore(class_2338 blockPos) {
      if (blockPos == null) {
         return class_2246.field_10124.method_9564();
      } else {
         return blockPos.equals(this.ignorePos) ? class_2246.field_10124.method_9564() : mc.field_1687.method_8320(blockPos);
      }
   }

   private boolean canBreak(class_2338 pos, class_2680 state) {
      if (state.method_26215()) {
         return false;
      } else {
         return !state.method_27852(class_2246.field_10036) && !state.method_27852(class_2246.field_22089)
            ? state.method_26214(mc.field_1687, pos) >= 0.0F
            : false;
      }
   }

   private boolean intersectsEntity(class_238 box, Predicate<class_1297> pred) {
      for (class_1297 e : mc.field_1687.method_8335((class_1297)null, box)) {
         if (pred.test(e)) {
            return true;
         }
      }

      return false;
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

   private class_2350 getCornerPerpDir(class_2350 dir) {
      return switch (dir) {
         case field_11043 -> class_2350.field_11034;
         case field_11035 -> class_2350.field_11039;
         case field_11034 -> class_2350.field_11043;
         case field_11039 -> class_2350.field_11035;
         default -> null;
      };
   }

   @Override
   public String getDisplayInfo() {
      return this.targetPlayer != null ? this.targetPlayer.method_5477().getString() : null;
   }

   private enum AntiSurroundMode {
      None,
      Inner,
      Outer,
      Auto;
   }

   private enum AntiSwimMode {
      None,
      Always,
      Mine,
      MineOrSwim;
   }

   private static final class CheckPos {
      final class_2338 blockPos;
      final AutoMineModule.CheckPosType type;

      CheckPos(class_2338 blockPos, AutoMineModule.CheckPosType type) {
         this.blockPos = blockPos;
         this.type = type;
      }

      @Override
      public int hashCode() {
         return this.blockPos.hashCode();
      }

      @Override
      public boolean equals(Object o) {
         return o instanceof AutoMineModule.CheckPos other && other.blockPos.equals(this.blockPos);
      }
   }

   private enum CheckPosType {
      Feet,
      Surround,
      Extend,
      FacePlace,
      Head,
      Below,
      TerrainBase;
   }

   private static class CityBlock {
      class_2338 blockPos;
      double score;
      AutoMineModule.CheckPosType type;
   }

   private enum ExtendBreakMode {
      None,
      Long,
      Corner;
   }

   public enum SortPriority {
      Angle,
      Distance;
   }
}
