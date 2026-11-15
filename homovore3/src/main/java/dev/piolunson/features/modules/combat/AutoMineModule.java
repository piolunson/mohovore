package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.impl.network.PacketEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.TargetsModule;
import dev.piolunson.features.modules.world.SpeedMineModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.InteractionUtil;
import dev.piolunson.util.MathUtil;
import dev.piolunson.util.PlaceUtil;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.render.RenderUtil;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

public class AutoMineModule extends Module {
   private static final double INVALID_SCORE = -1000.0;
   private static final Direction[] HORIZONTAL = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
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
   private PlayerEntity targetPlayer = null;
   private AutoMineModule.CityBlock target1 = null;
   private AutoMineModule.CityBlock target2 = null;
   private BlockPos ignorePos = null;
   private long lastOuterPlaceTime = 0L;
   private boolean isTerrainFight = false;
   private BlockPos glassTargetPos = null;
   private int glassUsedAttempts = 0;
   private BlockPos glassRenderPos = null;
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
      if (event.getPacket() instanceof BlockBreakingProgressS2CPacket pkt) {
         if (mc.player == null || pkt.getEntityId() != mc.player.getId()) {
            int progress = pkt.getProgress();
            if (progress >= 0 && progress <= 9) {
               this.enemyBreaking.put(pkt.getPos().asLong(), System.currentTimeMillis());
            } else {
               this.enemyBreaking.remove(pkt.getPos().asLong());
            }
         }
      }
   }

   private boolean isBlockBeingBroken(BlockPos pos) {
      long ts = this.enemyBreaking.get(pos.asLong());
      return ts != 0L && System.currentTimeMillis() - ts < 1000L;
   }

   private void onMineFinish(BlockPos pos) {
      if (!nullCheck() && this.targetPlayer != null) {
         AutoMineModule.AntiSurroundMode swMode = this.antiSurroundMode.getValue();
         if (swMode != AutoMineModule.AntiSurroundMode.None) {
            AutoCrystalModule crystal = Homovore.moduleManager.getModuleByClass(AutoCrystalModule.class);
            if (crystal != null && crystal.isEnabled()) {
               if (swMode == AutoMineModule.AntiSurroundMode.Auto || swMode == AutoMineModule.AntiSurroundMode.Outer) {
                  for (Direction dir : HORIZONTAL) {
                     BlockPos playerSurroundBlock = this.targetPlayer.getBlockPos().offset(dir);
                     if (pos.equals(playerSurroundBlock)) {
                        boolean outerReady = System.currentTimeMillis() - this.lastOuterPlaceTime > this.antiSurroundOuterCooldown.getValue() * 1000.0;
                        if (!outerReady) {
                           return;
                        }

                        Box checkBox = Box.of(Vec3d.ofCenter(playerSurroundBlock), 2.5, 3.0, 2.5);
                        Box blockHitbox = new Box(playerSurroundBlock);

                        for (BlockPos bp : iterate(checkBox)) {
                           if (mc.world.getBlockState(bp).isAir()) {
                              BlockState downState = mc.world.getBlockState(bp.down());
                              if (downState.isOf(Blocks.OBSIDIAN) || downState.isOf(Blocks.BEDROCK)) {
                                 Box crystalPlaceHitbox = new Box(bp.getX(), bp.getY(), bp.getZ(), bp.getX() + 1, bp.getY() + 2, bp.getZ() + 1);
                                 if (!this.intersectsEntity(crystalPlaceHitbox, e -> !e.isSpectator())) {
                                    Vec3d crystalPos = new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                                    Box crystalHitbox = new Box(
                                       crystalPos.x - 1.0, crystalPos.y, crystalPos.z - 1.0, crystalPos.x + 1.0, crystalPos.y + 2.0, crystalPos.z + 1.0
                                    );
                                    if (crystalHitbox.intersects(blockHitbox)) {
                                       if (crystal.preplaceCrystal(bp.toImmutable(), this.antiSurroundOuterSnap.getValue())) {
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
                  for (Direction dir : HORIZONTAL) {
                     BlockPos playerSurroundBlock = this.targetPlayer.getBlockPos().offset(dir);
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
            BlockState selfFeetBlock = mc.world.getBlockState(mc.player.getBlockPos());
            BlockState selfHeadBlock = mc.world.getBlockState(mc.player.getBlockPos().up());
            BlockPos selfHeadPos = mc.player.getBlockPos().up();
            boolean shouldBreakSelfHeadBlock = this.canBreak(selfHeadPos, selfHeadBlock)
               && (selfHeadBlock.isOf(Blocks.OBSIDIAN) || selfHeadBlock.isOf(Blocks.CRYING_OBSIDIAN));
            boolean prioHead = false;
            if (this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.Always && shouldBreakSelfHeadBlock) {
               mine.requestBreak(selfHeadPos);
               prioHead = true;
            }

            if (this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.MineOrSwim && mc.player.isCrawling() && shouldBreakSelfHeadBlock) {
               mine.requestBreak(selfHeadPos);
               prioHead = true;
            }

            if ((this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.Mine || this.antiSwim.getValue() == AutoMineModule.AntiSwimMode.MineOrSwim)
               && this.isBlockBeingBroken(mc.player.getBlockPos())
               && shouldBreakSelfHeadBlock) {
               mine.requestBreak(selfHeadPos);
               prioHead = true;
            }

            this.targetPlayer = this.selectTarget();
            if (this.targetPlayer != null) {
               this.handleGlassPush(mine);
               if (mine.getSecondaryPos() == null
                  || !selfHeadBlock.isOf(Blocks.OBSIDIAN)
                  || !selfFeetBlock.isAir()
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
                        Deque<BlockPos> wanted = new ArrayDeque<>();
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
      } else if (mc.player.isInSneakingPose()) {
         return false;
      } else {
         BlockPos feetPos = mc.player.getBlockPos();
         BlockPos headPos = feetPos.up();
         BlockState feetBlock = mc.world.getBlockState(feetPos);
         BlockState headBlock = mc.world.getBlockState(headPos);
         if (!this.isBlocked(feetPos, feetBlock) || !this.isBlocked(headPos, headBlock)) {
            return false;
         } else {
            return !InteractionUtil.canBreak(feetPos, feetBlock) ? false : mine.requestBreak(feetPos);
         }
      }
   }

   private boolean isBlocked(BlockPos pos, BlockState state) {
      return !state.isReplaceable() && state.getOutlineShape(mc.world, pos) != VoxelShapes.empty();
   }

   private PlayerEntity selectTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      double rangeV = this.range.getValue();
      PlayerEntity best = null;
      double bestMetric = Double.MAX_VALUE;
      Box area = mc.player.getBoundingBox().expand(rangeV);

      for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
         if (e instanceof PlayerEntity player
            && player != mc.player
            && player.isAlive()
            && !player.isDead()
            && !player.isSpectator()
            && (targets == null || targets.isValidTarget(player))
            && !(player.getEntityPos().distanceTo(mc.player.getEyePos()) > rangeV)
            && (!this.ignoreNakeds.getValue() || !this.isNaked(player))) {
            double metric = switch ((AutoMineModule.SortPriority)this.targetPriority.getValue()) {
               case Angle -> this.angleTo(player);
               case Distance -> mc.player.squaredDistanceTo(player);
            };
            if (metric < bestMetric) {
               bestMetric = metric;
               best = player;
            }
         }
      }

      return best;
   }

   private double angleTo(PlayerEntity player) {
      float[] need = MathUtil.calcAngle(mc.player.getEyePos(), player.getEyePos());
      double dYaw = MathUtil.wrapDegrees(need[0] - mc.player.getYaw());
      double dPitch = need[1] - mc.player.getPitch();
      return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
   }

   private boolean isNaked(PlayerEntity p) {
      return p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
         && p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
         && p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
         && p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
   }

   private void handleGlassPush(SpeedMineModule mine) {
      if (!this.glassPush.getValue()) {
         this.resetGlass();
      } else {
         AutoCrystalModule crystal = Homovore.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (crystal != null && crystal.isEnabled()) {
            BlockPos pos = mine.getRebreakPos();
            if (pos == null) {
               this.resetGlass();
            } else if (this.isGoodRebreak(pos, this.inBedrockCase()) && crystal.isDesirablePlacement(pos)) {
               if (!pos.equals(this.glassTargetPos)) {
                  this.glassTargetPos = pos.toImmutable();
                  this.glassUsedAttempts = 0;
               }

               if (!this.itemInCrystalSpot(pos)) {
                  if (this.glassUsedAttempts > 0 && mc.world.getBlockState(pos).isAir()) {
                     crystal.preplaceCrystal(pos, true);
                  }

                  this.glassUsedAttempts = 0;
               } else if (this.glassUsedAttempts < this.glassAttempts.getValue()) {
                  if (mc.world.getBlockState(pos).isAir()) {
                     if (this.placeGlass(pos)) {
                        this.glassUsedAttempts++;
                        mine.holdRebreak(pos, 5);
                        this.glassRenderPos = pos.toImmutable();
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

   private boolean itemInCrystalSpot(BlockPos airPos) {
      Box box = new Box(airPos).stretch(0.0, 1.0, 0.0);

      for (Entity e : mc.world.getOtherEntities((Entity)null, box)) {
         if (e instanceof ItemEntity) {
            return true;
         }
      }

      return false;
   }

   private boolean placeGlass(BlockPos pos) {
      Result glass = InventoryUtil.find(Items.GLASS, InventoryUtil.PLACE_SCOPE);
      if (!glass.found()) {
         return false;
      }

      if (!PlaceUtil.canPlace(pos)) {
         return false;
      }

      if (!Homovore.placementManager.enqueue(pos, Items.GLASS)) {
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
            above.blockPos = best.blockPos.up();
            above.score = best.score - 10.0;
            above.type = AutoMineModule.CheckPosType.Surround;
            this.target2 = above;
         } else {
            this.target2 = this.findCityBlock(this.target1.blockPos);
         }
      }
   }

   private AutoMineModule.CityBlock findCityBlock(BlockPos exclude) {
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

      BlockPos blockPos = pos.blockPos;
      BlockState block = mc.world.getBlockState(blockPos);
      boolean goodRebreak = this.isGoodRebreak(blockPos, inBedrock);
      if (block.isAir() && !goodRebreak) {
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
         score += inBedrock && block.isAir() ? 10.0 : 40.0;
      }

      return score;
   }

   private boolean isGoodRebreak(BlockPos blockPos, boolean inBedrock) {
      SpeedMineModule mine = this.speedMine();
      if (mine == null) {
         return false;
      }

      BlockPos rebreak = mine.getRebreakPos();
      if (rebreak == null || !blockPos.equals(rebreak)) {
         return false;
      }

      if (!inBedrock) {
         if (!blockPos.equals(this.targetPlayer.getBlockPos()) && !this.isBlockInFeet(blockPos)) {
            for (Direction dir : HORIZONTAL) {
               if (this.targetPlayer.getBlockPos().offset(dir).equals(blockPos) && this.isCrystalBlock(this.targetPlayer.getBlockPos().offset(dir).down())) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         boolean isSelfTrapBlock = false;

         for (Direction dir : HORIZONTAL) {
            if (this.targetPlayer.getBlockPos().up().offset(dir).equals(blockPos)) {
               isSelfTrapBlock = true;
               break;
            }
         }

         boolean canFacePlace = mc.world.getBlockState(this.targetPlayer.getBlockPos().up()).isAir();
         return BlockPos.stream(this.targetFeetBox()).count() == 1L
            && (blockPos.equals(this.targetPlayer.getBlockPos().up(2)) || isSelfTrapBlock && canFacePlace);
      }
   }

   private void addNormalCaseCheckPositions(Set<AutoMineModule.CheckPos> checkPos) {
      Box feetBox = this.targetFeetBox();

      for (BlockPos raw : iterate(feetBox)) {
         checkPos.add(new AutoMineModule.CheckPos(raw.toImmutable(), AutoMineModule.CheckPosType.Feet));
      }

      for (BlockPos raw : iterate(feetBox)) {
         BlockPos feet = raw.toImmutable();

         for (Direction dir : HORIZONTAL) {
            BlockPos surround = feet.offset(dir);
            if (!this.isBlockInFeet(surround)) {
               checkPos.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckPosType.Surround));
               if (this.underMine.getValue()) {
                  BlockPos base = surround.down();
                  if (!this.isCrystalBlock(base) && this.canBreak(base, mc.world.getBlockState(base))) {
                     checkPos.add(new AutoMineModule.CheckPos(base, AutoMineModule.CheckPosType.TerrainBase));
                  }
               }

               switch ((AutoMineModule.ExtendBreakMode)this.extendBreakMode.getValue()) {
                  case None:
                  default:
                     break;
                  case Long:
                     checkPos.add(new AutoMineModule.CheckPos(surround.offset(dir), AutoMineModule.CheckPosType.Extend));
                     break;
                  case Corner:
                     Direction perpDir = this.getCornerPerpDir(dir);
                     if (perpDir != null) {
                        checkPos.add(new AutoMineModule.CheckPos(surround.offset(perpDir), AutoMineModule.CheckPosType.Extend));
                     }
               }
            }
         }
      }
   }

   private boolean computeTerrainFight() {
      Box feetBox = this.targetFeetBox();

      for (BlockPos feet : iterate(feetBox)) {
         for (Direction dir : HORIZONTAL) {
            BlockPos neighbor = feet.offset(dir);
            if (!this.isBlockInFeet(neighbor) && this.isCrystalBlock(neighbor.down())) {
               return false;
            }
         }
      }

      return true;
   }

   private void addBedrockCaseCheckPositions(Set<AutoMineModule.CheckPos> checkPos) {
      boolean isCrawling = this.targetPlayer.isCrawling();

      for (BlockPos raw : iterate(this.targetFeetBox())) {
         BlockPos pos = raw.toImmutable();
         BlockPos headPos = isCrawling ? pos : pos.up();
         BlockPos aboveHeadPos = headPos.up();
         if (this.canBreak(headPos, mc.world.getBlockState(headPos))) {
            checkPos.add(new AutoMineModule.CheckPos(headPos, AutoMineModule.CheckPosType.FacePlace));
         } else if (this.canBreak(aboveHeadPos, mc.world.getBlockState(aboveHeadPos))) {
            checkPos.add(new AutoMineModule.CheckPos(aboveHeadPos, AutoMineModule.CheckPosType.Head));
         }

         if (!mc.world.getBlockState(pos.down()).isOf(Blocks.BEDROCK)) {
            BlockPos center = pos.down();
            boolean plusNotFinished = false;

            for (Direction dir : HORIZONTAL) {
               BlockPos surround = center.offset(dir);
               if (this.canBreak(surround, mc.world.getBlockState(surround))
                  && (!this.isCrystalBlock(surround) || !mc.world.getBlockState(surround.up()).isAir())) {
                  checkPos.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckPosType.Below));
                  plusNotFinished = true;
               }
            }

            if (!plusNotFinished && this.canBreak(center, mc.world.getBlockState(center))) {
               checkPos.add(new AutoMineModule.CheckPos(center, AutoMineModule.CheckPosType.Below));
            }
         }

         checkPos.add(new AutoMineModule.CheckPos(pos, AutoMineModule.CheckPosType.Surround));

         for (Direction dir : HORIZONTAL) {
            BlockPos surround = pos.offset(dir);
            if (!this.isBlockInFeet(surround)) {
               checkPos.add(new AutoMineModule.CheckPos(surround, AutoMineModule.CheckPosType.Surround));
            }
         }
      }
   }

   private double scoreNormal(AutoMineModule.CheckPos pos) {
      BlockPos blockPos = pos.blockPos;
      double score = 0.0;
      BlockState block = mc.world.getBlockState(blockPos);
      if (pos.type == AutoMineModule.CheckPosType.Feet) {
         BlockState headBlock = mc.world.getBlockState(blockPos.up());
         if (headBlock.isOf(Blocks.OBSIDIAN)) {
            score += 100.0;
         } else {
            if (block.isOf(Blocks.COBWEB)) {
               return -1000.0;
            }

            score += 50.0;
         }
      } else {
         BlockState selfHeadState = mc.world.getBlockState(mc.player.getBlockPos().up());
         if (blockPos.equals(mc.player.getBlockPos()) && (selfHeadState.isOf(Blocks.OBSIDIAN) || selfHeadState.isOf(Blocks.BEDROCK))) {
            return -1000.0;
         }

         if (pos.type == AutoMineModule.CheckPosType.Surround) {
            BlockState down = mc.world.getBlockState(blockPos.down());
            if (down.isAir()) {
               score += 35.0;
            } else if (!down.isOf(Blocks.OBSIDIAN) && !down.isOf(Blocks.BEDROCK)) {
               score += 10.0;
            } else {
               score += 55.0;
               boolean isPosAntiSurround = false;

               for (Direction dir : HORIZONTAL) {
                  BlockPos antiSurroundBlockPos = blockPos.offset(dir);
                  if (this.targetPlayer.squaredDistanceTo(Vec3d.ofCenter(blockPos)) < 4.0
                     && this.getBlockStateIgnore(antiSurroundBlockPos).isAir()
                     && this.isCrystalBlock(antiSurroundBlockPos.down())) {
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

      double d = this.targetPlayer.getEntityPos().distanceTo(Vec3d.ofCenter(blockPos));
      score += 10.0 / d;
      Vec3d velocity = this.targetPlayer.getVelocity();
      if (velocity.horizontalLengthSquared() > 0.001) {
         Vec3d moveDir = new Vec3d(velocity.x, 0.0, velocity.z).normalize();
         Vec3d toBlock = Vec3d.ofCenter(blockPos).subtract(this.targetPlayer.getEntityPos()).normalize();
         double dot = moveDir.dotProduct(toBlock);
         if (dot > 0.0) {
            score += dot * 5.0;
         }
      }

      return score;
   }

   private double scoreBedrock(AutoMineModule.CheckPos pos) {
      BlockPos blockPos = pos.blockPos;
      double score = 0.0;
      if (blockPos.getY() == this.targetPlayer.getBlockY() + 2 || blockPos.getY() == this.targetPlayer.getBlockY() - 1) {
         score += 10.0;
      }

      if (BlockPos.stream(this.targetFeetBox()).count() == 1L) {
         boolean canMineFaceBlock = !mc.world.getBlockState(this.targetPlayer.getBlockPos().up()).isOf(Blocks.BEDROCK);
         if (canMineFaceBlock) {
            if (blockPos.equals(this.targetPlayer.getBlockPos().up())) {
               score += 20.0;
            } else {
               boolean isSelfTrapBlock = false;

               for (Direction dir : HORIZONTAL) {
                  if (this.targetPlayer.getBlockPos().up().offset(dir).equals(blockPos)) {
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
         score += blockPos.equals(this.targetPlayer.getBlockPos().down()) ? 25.0 : 30.0;
      }

      double d = this.targetPlayer.getEntityPos().distanceTo(Vec3d.ofCenter(blockPos));
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

   private Box targetFeetBox() {
      Box bb = this.targetPlayer.getBoundingBox().contract(0.01, 0.1, 0.01);
      double feetY = this.targetPlayer.getY();
      return new Box(bb.minX, feetY, bb.minZ, bb.maxX, feetY + 0.1, bb.maxZ);
   }

   private boolean inBedrockCase() {
      return BlockPos.stream(this.targetFeetBox()).anyMatch(bp -> mc.world.getBlockState(bp).isOf(Blocks.BEDROCK));
   }

   private boolean isBlockInFeet(BlockPos blockPos) {
      Box feetBox = this.targetFeetBox();

      for (BlockPos pos : BlockPos.iterate(
         MathHelper.floor(feetBox.minX),
         MathHelper.floor(feetBox.minY),
         MathHelper.floor(feetBox.minZ),
         MathHelper.floor(feetBox.maxX),
         MathHelper.floor(feetBox.maxY),
         MathHelper.floor(feetBox.maxZ)
      )) {
         if (blockPos.equals(pos)) {
            return true;
         }
      }

      return false;
   }

   private boolean isCrystalBlock(BlockPos blockPos) {
      BlockState s = mc.world.getBlockState(blockPos);
      return s.isOf(Blocks.OBSIDIAN) || s.isOf(Blocks.BEDROCK);
   }

   private BlockState getBlockStateIgnore(BlockPos blockPos) {
      if (blockPos == null) {
         return Blocks.AIR.getDefaultState();
      } else {
         return blockPos.equals(this.ignorePos) ? Blocks.AIR.getDefaultState() : mc.world.getBlockState(blockPos);
      }
   }

   private boolean canBreak(BlockPos pos, BlockState state) {
      if (state.isAir()) {
         return false;
      } else {
         return !state.isOf(Blocks.FIRE) && !state.isOf(Blocks.SOUL_FIRE) ? state.getHardness(mc.world, pos) >= 0.0F : false;
      }
   }

   private boolean intersectsEntity(Box box, Predicate<Entity> pred) {
      for (Entity e : mc.world.getOtherEntities((Entity)null, box)) {
         if (pred.test(e)) {
            return true;
         }
      }

      return false;
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

   private Direction getCornerPerpDir(Direction dir) {
      return switch (dir) {
         case NORTH -> Direction.EAST;
         case SOUTH -> Direction.WEST;
         case EAST -> Direction.NORTH;
         case WEST -> Direction.SOUTH;
         default -> null;
      };
   }

   @Override
   public String getDisplayInfo() {
      return this.targetPlayer != null ? this.targetPlayer.getName().getString() : null;
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
      final BlockPos blockPos;
      final AutoMineModule.CheckPosType type;

      CheckPos(BlockPos blockPos, AutoMineModule.CheckPosType type) {
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
      BlockPos blockPos;
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
