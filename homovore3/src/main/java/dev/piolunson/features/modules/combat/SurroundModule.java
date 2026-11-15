package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.TickEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.render.BreakIndicatorsModule;
import dev.piolunson.features.modules.world.SpeedMineModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.PlacementManager;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.util.MathUtil;
import dev.piolunson.util.PlaceUtil;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.ResultType;
import dev.piolunson.util.player.EatUtil;
import dev.piolunson.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SurroundModule extends Module {
   private final Setting<Boolean> attack = this.bool("Attack", true).setPage("General");
   private final Setting<Boolean> mineProtect = this.bool("MineProtect", true).setPage("General");
   private final Setting<Boolean> fireworks = this.bool("Fireworks", true).setPage("General");
   private final Setting<Boolean> crystalProtect = this.bool("CrystalProtect", true).setPage("General");
   private final Setting<Boolean> avoidHelping = this.bool("AvoidHelpingOpponents", true).setPage("General");
   private final Setting<Boolean> selfTrap = this.bool("SelfTrap", true).setPage("SelfTrap");
   private final Setting<SurroundModule.SelfTrapMode> selfTrapMode = this.mode("SelfTrapMode", SurroundModule.SelfTrapMode.Smart).setPage("SelfTrap");
   private final Setting<Boolean> selfTrapHead = this.bool("SelfTrapHead", true).setPage("SelfTrap");
   private final Setting<Boolean> crawlTrap = this.bool("CrawlTrap", true).setPage("SelfTrap");
   private final Setting<Boolean> extend = this.bool("Extend", true).setPage("Extend");
   private final Setting<SurroundModule.ExtendMode> extendMode = this.mode("ExtendMode", SurroundModule.ExtendMode.Smart).setPage("Extend");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.2F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 0, 62, 122, 148).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 0, 62, 122, 148).setPage("Render");
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private final Set<BlockPos> wantedPoses = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> ownedQueued = new HashSet<>();
   private int cachedObsSlot = -1;
   private int cachedFireworkSlot = -1;
   private final Set<BlockPos> fireworkPoses = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, Long> fireworkDeployedAt = new HashMap<>();
   private static final long FIREWORK_REDEPLOY_COOLDOWN_MS = 500L;
   private final Set<BlockPos> extendPoses = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> opponentSurroundPoses = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> helpBlockedPoses = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, Integer> breakCounts = new ConcurrentHashMap<>();
   private static final int SAFE_REBREAK_THRESHOLD = 3;
   private final PlacementManager.PlacementListener airRefillListener = (pos, nowAir) -> {
      boolean wanted = this.wantedPoses.contains(pos);
      if (wanted || this.extendPoses.contains(pos)) {
         boolean wasOwned = this.ownedQueued.remove(pos);
         if (!nowAir) {
            if (wasOwned) {
               this.breakCounts.merge(pos.toImmutable(), 1, Integer::sum);
            }
         } else if (!this.fireworkPoses.contains(pos)) {
            if (!this.speedMineClaims(pos)) {
               if (this.cachedFireworkSlot >= 0 && this.canRocket(pos, System.currentTimeMillis())) {
                  this.fireworkPoses.add(pos.toImmutable());
               } else if (wanted) {
                  if (!this.helpBlockedPoses.contains(pos)) {
                     if (this.cachedObsSlot >= 0) {
                        if (PlaceUtil.canPlace(pos)) {
                           if (Homovore.placementManager.enqueue(pos, Items.OBSIDIAN)) {
                              this.ownedQueued.add(pos);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   };
   private long lastCrystalNearHead = 0L;
   private long lastAttackTime = 0L;
   private BlockPos lastExtendOffset = null;
   private long lastExtendThreat = 0L;
   private static final long EXTEND_HOLD_MS = 1000L;
   private static final Direction[] HORIZONTALS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

   public boolean isSurroundPos(BlockPos pos) {
      return this.wantedPoses.contains(pos);
   }

   public SurroundModule() {
      super("Surround", "Surrounds you in obsidian to prevent crystal damage.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      Homovore.placementManager.addListener(this.airRefillListener);
   }

   @Override
   public void onDisable() {
      Homovore.placementManager.removeListener(this.airRefillListener);
      Homovore.placementManager.removeQueuedFor(this.ownedQueued::contains);
      this.ownedQueued.clear();
      this.wantedPoses.clear();
      this.fireworkPoses.clear();
      this.extendPoses.clear();
      this.opponentSurroundPoses.clear();
      this.helpBlockedPoses.clear();
      this.fireworkDeployedAt.clear();
      this.breakCounts.clear();
      this.cachedObsSlot = -1;
      this.cachedFireworkSlot = -1;
      this.renderMap.clear();
      this.lastExtendOffset = null;
      this.lastExtendThreat = 0L;
      this.lastCrystalNearHead = 0L;
      this.lastAttackTime = 0L;
   }

   @Subscribe(priority = 1)
   private void onTick(TickEvent event) {
      if (!nullCheck() && mc.currentScreen == null) {
         Result obs = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
         if (obs.found() && obs.type() != ResultType.OFFHAND) {
            int obsSlot = obs.slot();
            this.cachedObsSlot = obsSlot;
            this.cachedFireworkSlot = -1;
            if (this.fireworks.getValue()) {
               Result fw = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.PLACE_SCOPE);
               if (fw.found() && fw.type() != ResultType.OFFHAND) {
                  this.cachedFireworkSlot = fw.slot();
               }
            }

            List<BlockPos> placePoses = new ArrayList<>();
            List<BlockPos> rebreakPlacePoses = new ArrayList<>();
            List<BlockPos> rebreakSides = new ArrayList<>();
            List<BlockPos> fireworkPlacePoses = new ArrayList<>();
            List<BlockPos> feetPositions = new ArrayList<>();
            this.wantedPoses.clear();
            this.fireworkPoses.clear();
            this.extendPoses.clear();
            long now = System.currentTimeMillis();
            Box bounds = mc.player.getBoundingBox();
            int feetY = mc.player.getBlockPos().getY();
            int minX = PlaceUtil.minCell(bounds.minX);
            int maxX = PlaceUtil.maxCell(bounds.maxX);
            int minZ = PlaceUtil.minCell(bounds.minZ);
            int maxZ = PlaceUtil.maxCell(bounds.maxZ);

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  BlockPos feetPos = new BlockPos(x, feetY, z);
                  feetPositions.add(feetPos);

                  for (Direction dir : HORIZONTALS) {
                     BlockPos adjacent = feetPos.offset(dir);
                     if (adjacent.getX() < minX || adjacent.getX() > maxX || adjacent.getZ() < minZ || adjacent.getZ() > maxZ) {
                        BlockState state = mc.world.getBlockState(adjacent);
                        this.wantedPoses.add(adjacent.toImmutable());
                        if (!this.tryFirework(adjacent, now, fireworkPlacePoses) && (state.isAir() || state.isReplaceable())) {
                           placePoses.add(adjacent);
                        }

                        if (this.isHot(adjacent, now)) {
                           rebreakSides.add(adjacent.toImmutable());
                        }

                        if (this.selfTrap.getValue() && this.selfTrapMode.getValue() != SurroundModule.SelfTrapMode.None) {
                           this.checkSelfTrap(adjacent, now, placePoses);
                        }

                        if (this.cachedFireworkSlot >= 0) {
                           BlockPos extendPos = feetPos.offset(dir, 2);
                           this.extendPoses.add(extendPos.toImmutable());
                           this.tryFirework(extendPos, now, fireworkPlacePoses);
                        }
                     }
                  }

                  BlockPos below = feetPos.down();
                  BlockState belowState = mc.world.getBlockState(below);
                  this.wantedPoses.add(below.toImmutable());
                  if (belowState.isAir() || belowState.isReplaceable()) {
                     placePoses.add(below);
                  }
               }
            }

            if (this.extend.getValue() && this.extendMode.getValue() != SurroundModule.ExtendMode.None) {
               this.computeExtend(feetPositions, placePoses, now);
            }

            if (this.mineProtect.getValue()) {
               this.applyMineProtect(feetPositions, placePoses);
            }

            if (this.fireworks.getValue()) {
               for (BlockPos rebreak : rebreakSides) {
                  for (Direction dir : HORIZONTALS) {
                     BlockPos neighbor = rebreak.offset(dir);
                     if ((neighbor.getY() != feetY || neighbor.getX() < minX || neighbor.getX() > maxX || neighbor.getZ() < minZ || neighbor.getZ() > maxZ)
                        && !this.wantedPoses.contains(neighbor)) {
                        this.wantedPoses.add(neighbor.toImmutable());
                        BlockState state = mc.world.getBlockState(neighbor);
                        if (state.isAir() || state.isReplaceable()) {
                           rebreakPlacePoses.add(neighbor);
                        }
                     }
                  }
               }
            }

            if (this.selfTrap.getValue() && this.selfTrapHead.getValue()) {
               boolean prone = this.crawlTrap.getValue() && (mc.player.isCrawling() || mc.player.isGliding());
               BlockPos head = mc.player.getBlockPos().up(prone ? 1 : 2);
               this.wantedPoses.add(head);
               placePoses.add(head);
            }

            if (!this.ownedQueued.isEmpty()) {
               Homovore.placementManager.removeQueuedFor(p -> this.ownedQueued.contains(p) && !this.wantedPoses.contains(p));
               this.ownedQueued.removeIf(p -> !this.wantedPoses.contains(p));
            }

            List<BlockPos> fireworkUsePoses = new ArrayList<>();

            for (BlockPos pos : fireworkPlacePoses) {
               if (PlaceUtil.canPlace(pos)) {
                  fireworkUsePoses.add(pos);
               }
            }

            if (Homovore.placementManager.useFireworks(fireworkUsePoses, Direction.DOWN)) {
               for (BlockPos pos : fireworkUsePoses) {
                  this.fireworkDeployedAt.put(pos.toImmutable(), now);
                  this.breakCounts.remove(pos);
               }
            }

            if (!EatUtil.shouldDefer()) {
               Vec3d predicted = mc.player.getEntityPos().add(mc.player.getVelocity().multiply(0.5));
               placePoses.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(predicted)));
               if (this.fireworks.getValue()) {
                  rebreakPlacePoses.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(predicted)));
                  placePoses.addAll(rebreakPlacePoses);
               }

               if (this.attack.getValue() && now - this.lastAttackTime >= 50L) {
                  EndCrystalEntity crystal = this.findThreateningCrystal();
                  if (crystal != null) {
                     BlockPos crystalCell = crystal.getBlockPos();
                     this.breakCrystal(crystal);
                     this.lastAttackTime = now;

                     for (BlockPos p : this.wantedPoses) {
                        if (Math.abs(p.getX() - crystalCell.getX()) <= 1
                           && Math.abs(p.getZ() - crystalCell.getZ()) <= 1
                           && Math.abs(p.getY() - crystalCell.getY()) <= 2) {
                           Homovore.placementManager.forceResetPlaceCooldown(p);
                        }
                     }
                  }
               }

               this.computeHelpBlocked(minX, maxX, minZ, maxZ, feetY);

               for (BlockPos pos : placePoses) {
                  if (!this.helpBlockedPoses.contains(pos)
                     && !this.speedMineClaims(pos)
                     && PlaceUtil.canPlace(pos)
                     && Homovore.placementManager.enqueue(pos, Items.OBSIDIAN)) {
                     this.ownedQueued.add(pos);
                     this.renderMap.put(pos, now);
                  }
               }

               long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
               this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
               this.fireworkDeployedAt.entrySet().removeIf(e -> now - e.getValue() > 500L);
            }
         } else {
            this.cachedObsSlot = -1;
            this.wantedPoses.clear();
            this.helpBlockedPoses.clear();
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               double t = age / fadeMs;
               Color fc = this.fillColor.getValue();
               Color oc = this.outlineColor.getValue();
               RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
               RenderUtil.drawBox(event.getMatrix(), entry.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
            }
         }
      }
   }

   private boolean speedMineClaims(BlockPos pos) {
      SpeedMineModule mine = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
      return mine != null && mine.isEnabled() && mine.isMining(pos);
   }

   private boolean intersectsCrystal(BlockPos pos) {
      Vec3d center = Vec3d.ofCenter(pos);
      Box box = new Box(center.x - 0.05, center.y - 0.05, center.z - 0.05, center.x + 0.05, center.y + 0.05, center.z + 0.05);
      return !mc.world.getNonSpectatingEntities(EndCrystalEntity.class, box).isEmpty();
   }

   private EndCrystalEntity findThreateningCrystal() {
      Vec3d eye = mc.player.getCameraPosVec(1.0F);
      Box search = mc.player.getBoundingBox().expand(6.0);
      EndCrystalEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (EndCrystalEntity c : mc.world.getNonSpectatingEntities(EndCrystalEntity.class, search)) {
         if (this.threatensSurround(c)) {
            Box box = c.getBoundingBox();
            double d = distSqToBox(eye, box);
            if (!(d >= bestSq) && !(d > 36.0)) {
               bestSq = d;
               best = c;
            }
         }
      }

      return best;
   }

   private boolean threatensSurround(EndCrystalEntity crystal) {
      BlockPos cell = crystal.getBlockPos();
      return this.nearSurroundCell(cell) || this.nearSurroundCell(cell.down()) || this.nearFootprintPerimeter(cell) || this.nearFootprintPerimeter(cell.down());
   }

   private boolean nearSurroundCell(BlockPos p) {
      if (!this.wantedPoses.contains(p) && !this.extendPoses.contains(p)) {
         for (Direction dir : HORIZONTALS) {
            BlockPos n = p.offset(dir);
            if (this.wantedPoses.contains(n) || this.extendPoses.contains(n)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private boolean nearFootprintPerimeter(BlockPos p) {
      Box bounds = mc.player.getBoundingBox();
      int feetY = mc.player.getBlockPos().getY();
      if (Math.abs(p.getY() - feetY) > 2) {
         return false;
      }

      int minX = PlaceUtil.minCell(bounds.minX);
      int maxX = PlaceUtil.maxCell(bounds.maxX);
      int minZ = PlaceUtil.minCell(bounds.minZ);
      int maxZ = PlaceUtil.maxCell(bounds.maxZ);
      return p.getX() >= minX - 2
         && p.getX() <= maxX + 2
         && p.getZ() >= minZ - 2
         && p.getZ() <= maxZ + 2
         && (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ);
   }

   private void breakCrystal(EndCrystalEntity crystal) {
      Vec3d eye = mc.player.getCameraPosVec(1.0F);
      Vec3d hit = this.closestPointToEye(eye, crystal.getBoundingBox());
      float[] angles = MathUtil.calcAngle(eye, hit);
      Homovore.rotationManager.submit(new RotationRequest("Surround", 100, angles[0], angles[1], RotationRequest.Mode.SILENT));
      mc.interactionManager.attackEntity(mc.player, crystal);
   }

   private Vec3d closestPointToEye(Vec3d eye, Box box) {
      double x = eye.x;
      double y = eye.y;
      double z = eye.z;
      double vec = 0.0625;
      double eps = 1.0E-9;
      if (eye.x < box.minX) {
         x = box.minX;
      } else if (eye.x > box.maxX) {
         x = box.maxX;
      }

      if (eye.y < box.minY) {
         y = box.minY;
      } else if (eye.y > box.maxY) {
         y = box.maxY;
      }

      if (eye.z < box.minZ) {
         z = box.minZ;
      } else if (eye.z > box.maxZ) {
         z = box.maxZ;
      }

      if (Math.abs(x - box.minX) < eps) {
         x = Math.min(box.minX + vec, box.maxX - eps);
      } else if (Math.abs(x - box.maxX) < eps) {
         x = Math.max(box.maxX - vec, box.minX + eps);
      }

      if (Math.abs(z - box.minZ) < eps) {
         z = Math.min(box.minZ + vec, box.maxZ - eps);
      } else if (Math.abs(z - box.maxZ) < eps) {
         z = Math.max(box.maxZ - vec, box.minZ + eps);
      }

      return new Vec3d(x, y, z);
   }

   private static double distSqToBox(Vec3d p, Box box) {
      double dx = p.x < box.minX ? box.minX - p.x : (p.x > box.maxX ? p.x - box.maxX : 0.0);
      double dy = p.y < box.minY ? box.minY - p.y : (p.y > box.maxY ? p.y - box.maxY : 0.0);
      double dz = p.z < box.minZ ? box.minZ - p.z : (p.z > box.maxZ ? p.z - box.maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private void checkSelfTrap(BlockPos adjacent, long now, List<BlockPos> placePoses) {
      BlockPos face = adjacent.up();
      boolean should = this.selfTrapMode.getValue() == SurroundModule.SelfTrapMode.Always;
      if (this.selfTrapMode.getValue() == SurroundModule.SelfTrapMode.Smart) {
         if (this.intersectsCrystal(face)) {
            this.lastCrystalNearHead = now;
         }

         if (now - this.lastCrystalNearHead < 1000L) {
            should = true;
         }
      }

      if (should) {
         this.wantedPoses.add(face.toImmutable());
         BlockState state = mc.world.getBlockState(face);
         if (state.isAir() || state.isReplaceable()) {
            placePoses.add(face);
         }
      }
   }

   private void computeExtend(List<BlockPos> feetPositions, List<BlockPos> placePoses, long now) {
      if (this.extendMode.getValue() == SurroundModule.ExtendMode.Always) {
         for (BlockPos feet : feetPositions) {
            for (Direction dir : HORIZONTALS) {
               this.placeExtendBlocks(placePoses, feet, new BlockPos(dir.getOffsetX(), 0, dir.getOffsetZ()));
            }
         }
      } else {
         EndCrystalEntity crystal = this.findExtendCrystal();
         if (crystal != null) {
            this.lastExtendOffset = crystal.getBlockPos().subtract(mc.player.getBlockPos());
            this.lastExtendThreat = now;
         }

         if (this.lastExtendOffset != null && now - this.lastExtendThreat < 1000L) {
            for (BlockPos feet : feetPositions) {
               this.placeExtendBlocks(placePoses, feet, this.lastExtendOffset);
            }
         }
      }
   }

   private EndCrystalEntity findExtendCrystal() {
      Box box = mc.player.getBoundingBox().expand(1.5, 0.5, 1.5).offset(0.0, 1.0, 0.0);
      Vec3d eye = mc.player.getEyePos();
      EndCrystalEntity closest = null;
      double bestSq = Double.MAX_VALUE;

      for (EndCrystalEntity c : mc.world.getNonSpectatingEntities(EndCrystalEntity.class, box)) {
         double d = c.squaredDistanceTo(eye);
         if (d < bestSq) {
            bestSq = d;
            closest = c;
         }
      }

      return closest;
   }

   private void placeExtendBlocks(List<BlockPos> out, BlockPos feetPos, BlockPos crystalOffset) {
      if (crystalOffset != null) {
         int normDx = Integer.signum(crystalOffset.getX());
         int normDz = Integer.signum(crystalOffset.getZ());
         boolean isDiagonal = normDx != 0 && normDz != 0;
         boolean isCardinal = normDx != 0 ^ normDz != 0;
         if (isDiagonal) {
            this.addExtendIfReplaceable(out, feetPos.add(normDx, 0, normDz));
            this.addExtendIfReplaceable(out, feetPos.add(normDx * 2, 0, 0));
            this.addExtendIfReplaceable(out, feetPos.add(0, 0, normDz * 2));
         } else if (isCardinal) {
            BlockPos diagonal1;
            BlockPos diagonal2;
            BlockPos straightBlock;
            if (normDx != 0) {
               diagonal1 = feetPos.add(normDx, 0, 1);
               diagonal2 = feetPos.add(normDx, 0, -1);
               straightBlock = feetPos.add(normDx * 2, 0, 0);
            } else {
               diagonal1 = feetPos.add(1, 0, normDz);
               diagonal2 = feetPos.add(-1, 0, normDz);
               straightBlock = feetPos.add(0, 0, normDz * 2);
            }

            this.addExtendIfReplaceable(out, diagonal1);
            this.addExtendIfReplaceable(out, diagonal2);
            this.addExtendIfReplaceable(out, straightBlock);
         }
      }
   }

   private void applyMineProtect(List<BlockPos> feetPositions, List<BlockPos> placePoses) {
      BreakIndicatorsModule indicators = Homovore.moduleManager.getModuleByClass(BreakIndicatorsModule.class);
      if (indicators != null && indicators.isEnabled()) {
         for (BreakIndicatorsModule.BreakInfo info : indicators.getActiveBreaksSnapshot().values()) {
            PlayerEntity player = info.player();
            if (player != null && player != mc.player && !Homovore.friendManager.isFriend(player)) {
               this.applyMineProtectBlock(feetPositions, placePoses, info.pos());
            }
         }
      }
   }

   private void applyMineProtectBlock(List<BlockPos> feetPositions, List<BlockPos> placePoses, BlockPos mined) {
      if (mined != null) {
         for (BlockPos feet : feetPositions) {
            if (mined.getY() == feet.getY()) {
               int dx = mined.getX() - feet.getX();
               int dz = mined.getZ() - feet.getZ();
               if (Math.abs(dx) + Math.abs(dz) == 1) {
                  this.addMineProtectIfReplaceable(placePoses, mined.up());
                  this.addMineProtectIfReplaceable(placePoses, mined.down());
                  if (dx != 0) {
                     this.addMineProtectIfReplaceable(placePoses, feet.add(dx, 0, 1));
                     this.addMineProtectIfReplaceable(placePoses, feet.add(dx, 0, -1));
                     this.addMineProtectIfReplaceable(placePoses, feet.add(dx * 2, 0, 0));
                  } else {
                     this.addMineProtectIfReplaceable(placePoses, feet.add(1, 0, dz));
                     this.addMineProtectIfReplaceable(placePoses, feet.add(-1, 0, dz));
                     this.addMineProtectIfReplaceable(placePoses, feet.add(0, 0, dz * 2));
                  }
               }
            }
         }
      }
   }

   private void addMineProtectIfReplaceable(List<BlockPos> out, BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      if (state.isAir() || state.isReplaceable()) {
         this.wantedPoses.add(pos.toImmutable());
         this.extendPoses.add(pos.toImmutable());
         out.add(pos);
      }
   }

   private void addExtendIfReplaceable(List<BlockPos> out, BlockPos pos) {
      BlockState below = mc.world.getBlockState(pos.down());
      if (below.isOf(Blocks.OBSIDIAN) || below.isOf(Blocks.BEDROCK)) {
         this.wantedPoses.add(pos.toImmutable());
         this.extendPoses.add(pos.toImmutable());
         if (!this.fireworkPoses.contains(pos)) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir() || state.isReplaceable()) {
               out.add(pos);
            }
         }
      }
   }

   private void computeHelpBlocked(int minX, int maxX, int minZ, int maxZ, int feetY) {
      this.opponentSurroundPoses.clear();
      this.helpBlockedPoses.clear();
      if (this.avoidHelping.getValue()) {
         this.buildOpponentSurroundPoses();
         if (!this.opponentSurroundPoses.isEmpty()) {
            boolean singleFootCell = minX == maxX && minZ == maxZ;
            boolean lowHealth = mc.player.getHealth() < 10.0F;
            if (!singleFootCell && !lowHealth) {
               Set<BlockPos> myFootCells = new HashSet<>();

               for (int x = minX; x <= maxX; x++) {
                  for (int z = minZ; z <= maxZ; z++) {
                     myFootCells.add(new BlockPos(x, feetY, z));
                  }
               }

               for (BlockPos pos : this.opponentSurroundPoses) {
                  if (!this.isNearMyPerimeter(pos, myFootCells)) {
                     this.helpBlockedPoses.add(pos.toImmutable());
                  }
               }
            }
         }
      }
   }

   private void buildOpponentSurroundPoses() {
      BlockPos self = mc.player.getBlockPos();

      for (PlayerEntity p : mc.world.getPlayers()) {
         if (p != mc.player && !Homovore.friendManager.isFriend(p)) {
            BlockPos oFeet = p.getBlockPos();
            double dx = oFeet.getX() - self.getX();
            double dz = oFeet.getZ() - self.getZ();
            if (!(Math.sqrt(dx * dx + dz * dz) > 5.0) && !this.isEntityPhased(p)) {
               Box pb = p.getBoundingBox();
               int oy = oFeet.getY();
               int oMinX = PlaceUtil.minCell(pb.minX);
               int oMaxX = PlaceUtil.maxCell(pb.maxX);
               int oMinZ = PlaceUtil.minCell(pb.minZ);
               int oMaxZ = PlaceUtil.maxCell(pb.maxZ);

               for (int x = oMinX; x <= oMaxX; x++) {
                  for (int z = oMinZ; z <= oMaxZ; z++) {
                     BlockPos cell = new BlockPos(x, oy, z);

                     for (Direction dir : HORIZONTALS) {
                        BlockPos adj = cell.offset(dir);
                        if (adj.getX() < oMinX || adj.getX() > oMaxX || adj.getZ() < oMinZ || adj.getZ() > oMaxZ) {
                           this.opponentSurroundPoses.add(adj.toImmutable());
                        }
                     }

                     this.opponentSurroundPoses.add(cell.down().toImmutable());
                     this.opponentSurroundPoses.add(cell.toImmutable());
                  }
               }
            }
         }
      }
   }

   private boolean isNearMyPerimeter(BlockPos pos, Set<BlockPos> myFootCells) {
      for (BlockPos cell : myFootCells) {
         if (pos.equals(cell)
            || pos.equals(cell.up())
            || pos.equals(cell.down())
            || pos.equals(cell.north())
            || pos.equals(cell.south())
            || pos.equals(cell.east())
            || pos.equals(cell.west())) {
            return true;
         }
      }

      return false;
   }

   private boolean isEntityPhased(PlayerEntity p) {
      BlockState state = mc.world.getBlockState(p.getBlockPos());
      return !state.isAir() && !state.isReplaceable();
   }

   private boolean tryFirework(BlockPos pos, long now, List<BlockPos> out) {
      if (this.cachedFireworkSlot < 0) {
         return false;
      }

      if (this.hasLiveFireworkAt(pos)) {
         this.fireworkPoses.add(pos.toImmutable());
         return true;
      }

      Long last = this.fireworkDeployedAt.get(pos);
      if (last != null && now - last < 500L) {
         this.fireworkPoses.add(pos.toImmutable());
         return true;
      }

      if (!this.canRocket(pos, now)) {
         return false;
      }

      if (this.speedMineClaims(pos)) {
         return false;
      }

      this.fireworkPoses.add(pos.toImmutable());
      out.add(pos);
      return true;
   }

   private boolean canRocket(BlockPos pos, long now) {
      return (this.isHot(pos, now) || this.crystalProtect.getValue() && this.hasCrystalAt(pos))
         && this.isFullBlock(pos.up())
         && this.isCrystalBase(pos.down())
         && this.hasFireworkCornerSupport(pos);
   }

   private boolean isCrystalBase(BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
   }

   private boolean hasCrystalAt(BlockPos pos) {
      Box box = new Box(pos).expand(0.5, 1.0, 0.5);
      return !mc.world.getNonSpectatingEntities(EndCrystalEntity.class, box).isEmpty();
   }

   private boolean hasFireworkCornerSupport(BlockPos pos) {
      Box bounds = mc.player.getBoundingBox();
      int minX = PlaceUtil.minCell(bounds.minX);
      int maxX = PlaceUtil.maxCell(bounds.maxX);
      int minZ = PlaceUtil.minCell(bounds.minZ);
      int maxZ = PlaceUtil.maxCell(bounds.maxZ);
      int x = pos.getX();
      int z = pos.getZ();
      if (z < minZ && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.west()) && this.isFullBlock(pos.east());
      } else if (z > maxZ && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.west()) && this.isFullBlock(pos.east());
      } else if (x < minX && z >= minZ && z <= maxZ) {
         return this.isFullBlock(pos.north()) && this.isFullBlock(pos.south());
      } else {
         return x > maxX && z >= minZ && z <= maxZ ? this.isFullBlock(pos.north()) && this.isFullBlock(pos.south()) : false;
      }
   }

   private boolean isHot(BlockPos pos, long now) {
      return this.breakCounts.getOrDefault(pos, 0) >= 3;
   }

   private boolean isFullBlock(BlockPos pos) {
      return Block.isShapeFullCube(mc.world.getBlockState(pos).getCollisionShape(mc.world, pos));
   }

   private boolean hasLiveFireworkAt(BlockPos pos) {
      Box box = new Box(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 4.0, pos.getZ() + 0.8);

      for (FireworkRocketEntity fw : mc.world.getNonSpectatingEntities(FireworkRocketEntity.class, box)) {
         if (fw.isAlive()) {
            return true;
         }
      }

      return false;
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
   }

   public enum ExtendMode {
      None,
      Smart,
      Always;
   }

   public enum SelfTrapMode {
      None,
      Smart,
      Always;
   }
}
