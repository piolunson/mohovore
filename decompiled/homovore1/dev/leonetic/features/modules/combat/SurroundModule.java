package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.render.BreakIndicatorsModule;
import dev.leonetic.features.modules.world.SpeedMineModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.player.EatUtil;
import dev.leonetic.util.render.RenderUtil;
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
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1671;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3532;

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
   private final Map<class_2338, Long> renderMap = new HashMap<>();
   private final Set<class_2338> wantedPoses = ConcurrentHashMap.newKeySet();
   private final Set<class_2338> ownedQueued = new HashSet<>();
   private int cachedObsSlot = -1;
   private int cachedFireworkSlot = -1;
   private final Set<class_2338> fireworkPoses = ConcurrentHashMap.newKeySet();
   private final Map<class_2338, Long> fireworkDeployedAt = new HashMap<>();
   private static final long FIREWORK_REDEPLOY_COOLDOWN_MS = 500L;
   private final Set<class_2338> extendPoses = ConcurrentHashMap.newKeySet();
   private final Set<class_2338> opponentSurroundPoses = ConcurrentHashMap.newKeySet();
   private final Set<class_2338> helpBlockedPoses = ConcurrentHashMap.newKeySet();
   private final Map<class_2338, Integer> breakCounts = new ConcurrentHashMap<>();
   private static final int SAFE_REBREAK_THRESHOLD = 3;
   private final PlacementManager.PlacementListener airRefillListener = (pos, nowAir) -> {
      boolean wanted = this.wantedPoses.contains(pos);
      if (wanted || this.extendPoses.contains(pos)) {
         boolean wasOwned = this.ownedQueued.remove(pos);
         if (!nowAir) {
            if (wasOwned) {
               this.breakCounts.merge(pos.method_10062(), 1, Integer::sum);
            }
         } else if (!this.fireworkPoses.contains(pos)) {
            if (!this.speedMineClaims(pos)) {
               if (this.cachedFireworkSlot >= 0 && this.canRocket(pos, System.currentTimeMillis())) {
                  this.fireworkPoses.add(pos.method_10062());
               } else if (wanted) {
                  if (!this.helpBlockedPoses.contains(pos)) {
                     if (this.cachedObsSlot >= 0) {
                        if (PlaceUtil.canPlace(pos)) {
                           if (Homovore.placementManager.enqueue(pos, class_1802.field_8281)) {
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
   private class_2338 lastExtendOffset = null;
   private long lastExtendThreat = 0L;
   private static final long EXTEND_HOLD_MS = 1000L;
   private static final class_2350[] HORIZONTALS = new class_2350[]{
      class_2350.field_11043, class_2350.field_11035, class_2350.field_11034, class_2350.field_11039
   };

   public boolean isSurroundPos(class_2338 pos) {
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
      if (!nullCheck() && mc.field_1755 == null) {
         Result obs = InventoryUtil.find(class_1802.field_8281, InventoryUtil.PLACE_SCOPE);
         if (obs.found() && obs.type() != ResultType.OFFHAND) {
            int obsSlot = obs.slot();
            this.cachedObsSlot = obsSlot;
            this.cachedFireworkSlot = -1;
            if (this.fireworks.getValue()) {
               Result fw = InventoryUtil.find(class_1802.field_8639, InventoryUtil.PLACE_SCOPE);
               if (fw.found() && fw.type() != ResultType.OFFHAND) {
                  this.cachedFireworkSlot = fw.slot();
               }
            }

            List<class_2338> placePoses = new ArrayList<>();
            List<class_2338> rebreakPlacePoses = new ArrayList<>();
            List<class_2338> rebreakSides = new ArrayList<>();
            List<class_2338> fireworkPlacePoses = new ArrayList<>();
            List<class_2338> feetPositions = new ArrayList<>();
            this.wantedPoses.clear();
            this.fireworkPoses.clear();
            this.extendPoses.clear();
            long now = System.currentTimeMillis();
            class_238 bounds = mc.field_1724.method_5829();
            int feetY = mc.field_1724.method_24515().method_10264();
            int minX = PlaceUtil.minCell(bounds.field_1323);
            int maxX = PlaceUtil.maxCell(bounds.field_1320);
            int minZ = PlaceUtil.minCell(bounds.field_1321);
            int maxZ = PlaceUtil.maxCell(bounds.field_1324);

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  class_2338 feetPos = new class_2338(x, feetY, z);
                  feetPositions.add(feetPos);

                  for (class_2350 dir : HORIZONTALS) {
                     class_2338 adjacent = feetPos.method_10093(dir);
                     if (adjacent.method_10263() < minX || adjacent.method_10263() > maxX || adjacent.method_10260() < minZ || adjacent.method_10260() > maxZ) {
                        class_2680 state = mc.field_1687.method_8320(adjacent);
                        this.wantedPoses.add(adjacent.method_10062());
                        if (!this.tryFirework(adjacent, now, fireworkPlacePoses) && (state.method_26215() || state.method_45474())) {
                           placePoses.add(adjacent);
                        }

                        if (this.isHot(adjacent, now)) {
                           rebreakSides.add(adjacent.method_10062());
                        }

                        if (this.selfTrap.getValue() && this.selfTrapMode.getValue() != SurroundModule.SelfTrapMode.None) {
                           this.checkSelfTrap(adjacent, now, placePoses);
                        }

                        if (this.cachedFireworkSlot >= 0) {
                           class_2338 extendPos = feetPos.method_10079(dir, 2);
                           this.extendPoses.add(extendPos.method_10062());
                           this.tryFirework(extendPos, now, fireworkPlacePoses);
                        }
                     }
                  }

                  class_2338 below = feetPos.method_10074();
                  class_2680 belowState = mc.field_1687.method_8320(below);
                  this.wantedPoses.add(below.method_10062());
                  if (belowState.method_26215() || belowState.method_45474()) {
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
               for (class_2338 rebreak : rebreakSides) {
                  for (class_2350 dir : HORIZONTALS) {
                     class_2338 neighbor = rebreak.method_10093(dir);
                     if ((
                           neighbor.method_10264() != feetY
                              || neighbor.method_10263() < minX
                              || neighbor.method_10263() > maxX
                              || neighbor.method_10260() < minZ
                              || neighbor.method_10260() > maxZ
                        )
                        && !this.wantedPoses.contains(neighbor)) {
                        this.wantedPoses.add(neighbor.method_10062());
                        class_2680 state = mc.field_1687.method_8320(neighbor);
                        if (state.method_26215() || state.method_45474()) {
                           rebreakPlacePoses.add(neighbor);
                        }
                     }
                  }
               }
            }

            if (this.selfTrap.getValue() && this.selfTrapHead.getValue()) {
               boolean prone = this.crawlTrap.getValue() && (mc.field_1724.method_20448() || mc.field_1724.method_6128());
               class_2338 head = mc.field_1724.method_24515().method_10086(prone ? 1 : 2);
               this.wantedPoses.add(head);
               placePoses.add(head);
            }

            if (!this.ownedQueued.isEmpty()) {
               Homovore.placementManager.removeQueuedFor(p -> this.ownedQueued.contains(p) && !this.wantedPoses.contains(p));
               this.ownedQueued.removeIf(p -> !this.wantedPoses.contains(p));
            }

            List<class_2338> fireworkUsePoses = new ArrayList<>();

            for (class_2338 pos : fireworkPlacePoses) {
               if (PlaceUtil.canPlace(pos)) {
                  fireworkUsePoses.add(pos);
               }
            }

            if (Homovore.placementManager.useFireworks(fireworkUsePoses, class_2350.field_11033)) {
               for (class_2338 pos : fireworkUsePoses) {
                  this.fireworkDeployedAt.put(pos.method_10062(), now);
                  this.breakCounts.remove(pos);
               }
            }

            if (!EatUtil.shouldDefer()) {
               class_243 predicted = mc.field_1724.method_73189().method_1019(mc.field_1724.method_18798().method_1021(0.5));
               placePoses.sort(Comparator.comparingDouble(p -> class_243.method_24953(p).method_1025(predicted)));
               if (this.fireworks.getValue()) {
                  rebreakPlacePoses.sort(Comparator.comparingDouble(p -> class_243.method_24953(p).method_1025(predicted)));
                  placePoses.addAll(rebreakPlacePoses);
               }

               if (this.attack.getValue() && now - this.lastAttackTime >= 50L) {
                  class_1511 crystal = this.findThreateningCrystal();
                  if (crystal != null) {
                     class_2338 crystalCell = crystal.method_24515();
                     this.breakCrystal(crystal);
                     this.lastAttackTime = now;

                     for (class_2338 p : this.wantedPoses) {
                        if (Math.abs(p.method_10263() - crystalCell.method_10263()) <= 1
                           && Math.abs(p.method_10260() - crystalCell.method_10260()) <= 1
                           && Math.abs(p.method_10264() - crystalCell.method_10264()) <= 2) {
                           Homovore.placementManager.forceResetPlaceCooldown(p);
                        }
                     }
                  }
               }

               this.computeHelpBlocked(minX, maxX, minZ, maxZ, feetY);

               for (class_2338 pos : placePoses) {
                  if (!this.helpBlockedPoses.contains(pos)
                     && !this.speedMineClaims(pos)
                     && PlaceUtil.canPlace(pos)
                     && Homovore.placementManager.enqueue(pos, class_1802.field_8281)) {
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

         for (Entry<class_2338, Long> entry : this.renderMap.entrySet()) {
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

   private boolean speedMineClaims(class_2338 pos) {
      SpeedMineModule mine = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
      return mine != null && mine.isEnabled() && mine.isMining(pos);
   }

   private boolean intersectsCrystal(class_2338 pos) {
      class_243 center = class_243.method_24953(pos);
      class_238 box = new class_238(
         center.field_1352 - 0.05,
         center.field_1351 - 0.05,
         center.field_1350 - 0.05,
         center.field_1352 + 0.05,
         center.field_1351 + 0.05,
         center.field_1350 + 0.05
      );
      return !mc.field_1687.method_18467(class_1511.class, box).isEmpty();
   }

   private class_1511 findThreateningCrystal() {
      class_243 eye = mc.field_1724.method_5836(1.0F);
      class_238 search = mc.field_1724.method_5829().method_1014(6.0);
      class_1511 best = null;
      double bestSq = Double.MAX_VALUE;

      for (class_1511 c : mc.field_1687.method_18467(class_1511.class, search)) {
         if (this.threatensSurround(c)) {
            class_238 box = c.method_5829();
            double d = distSqToBox(eye, box);
            if (!(d >= bestSq) && !(d > 36.0)) {
               bestSq = d;
               best = c;
            }
         }
      }

      return best;
   }

   private boolean threatensSurround(class_1511 crystal) {
      class_2338 cell = crystal.method_24515();
      return this.nearSurroundCell(cell)
         || this.nearSurroundCell(cell.method_10074())
         || this.nearFootprintPerimeter(cell)
         || this.nearFootprintPerimeter(cell.method_10074());
   }

   private boolean nearSurroundCell(class_2338 p) {
      if (!this.wantedPoses.contains(p) && !this.extendPoses.contains(p)) {
         for (class_2350 dir : HORIZONTALS) {
            class_2338 n = p.method_10093(dir);
            if (this.wantedPoses.contains(n) || this.extendPoses.contains(n)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private boolean nearFootprintPerimeter(class_2338 p) {
      class_238 bounds = mc.field_1724.method_5829();
      int feetY = mc.field_1724.method_24515().method_10264();
      if (Math.abs(p.method_10264() - feetY) > 2) {
         return false;
      }

      int minX = PlaceUtil.minCell(bounds.field_1323);
      int maxX = PlaceUtil.maxCell(bounds.field_1320);
      int minZ = PlaceUtil.minCell(bounds.field_1321);
      int maxZ = PlaceUtil.maxCell(bounds.field_1324);
      return p.method_10263() >= minX - 2
         && p.method_10263() <= maxX + 2
         && p.method_10260() >= minZ - 2
         && p.method_10260() <= maxZ + 2
         && (p.method_10263() < minX || p.method_10263() > maxX || p.method_10260() < minZ || p.method_10260() > maxZ);
   }

   private void breakCrystal(class_1511 crystal) {
      class_243 eye = mc.field_1724.method_5836(1.0F);
      class_243 hit = this.closestPointToEye(eye, crystal.method_5829());
      float[] angles = MathUtil.calcAngle(eye, hit);
      Homovore.rotationManager.submit(new RotationRequest("Surround", 100, angles[0], angles[1], RotationRequest.Mode.SILENT));
      mc.field_1761.method_2918(mc.field_1724, crystal);
   }

   private class_243 closestPointToEye(class_243 eye, class_238 box) {
      double x = eye.field_1352;
      double y = eye.field_1351;
      double z = eye.field_1350;
      double vec = 0.0625;
      double eps = 1.0E-9;
      if (eye.field_1352 < box.field_1323) {
         x = box.field_1323;
      } else if (eye.field_1352 > box.field_1320) {
         x = box.field_1320;
      }

      if (eye.field_1351 < box.field_1322) {
         y = box.field_1322;
      } else if (eye.field_1351 > box.field_1325) {
         y = box.field_1325;
      }

      if (eye.field_1350 < box.field_1321) {
         z = box.field_1321;
      } else if (eye.field_1350 > box.field_1324) {
         z = box.field_1324;
      }

      if (Math.abs(x - box.field_1323) < eps) {
         x = Math.min(box.field_1323 + vec, box.field_1320 - eps);
      } else if (Math.abs(x - box.field_1320) < eps) {
         x = Math.max(box.field_1320 - vec, box.field_1323 + eps);
      }

      if (Math.abs(z - box.field_1321) < eps) {
         z = Math.min(box.field_1321 + vec, box.field_1324 - eps);
      } else if (Math.abs(z - box.field_1324) < eps) {
         z = Math.max(box.field_1324 - vec, box.field_1321 + eps);
      }

      return new class_243(x, y, z);
   }

   private static double distSqToBox(class_243 p, class_238 box) {
      double dx = p.field_1352 < box.field_1323 ? box.field_1323 - p.field_1352 : (p.field_1352 > box.field_1320 ? p.field_1352 - box.field_1320 : 0.0);
      double dy = p.field_1351 < box.field_1322 ? box.field_1322 - p.field_1351 : (p.field_1351 > box.field_1325 ? p.field_1351 - box.field_1325 : 0.0);
      double dz = p.field_1350 < box.field_1321 ? box.field_1321 - p.field_1350 : (p.field_1350 > box.field_1324 ? p.field_1350 - box.field_1324 : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private void checkSelfTrap(class_2338 adjacent, long now, List<class_2338> placePoses) {
      class_2338 face = adjacent.method_10084();
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
         this.wantedPoses.add(face.method_10062());
         class_2680 state = mc.field_1687.method_8320(face);
         if (state.method_26215() || state.method_45474()) {
            placePoses.add(face);
         }
      }
   }

   private void computeExtend(List<class_2338> feetPositions, List<class_2338> placePoses, long now) {
      if (this.extendMode.getValue() == SurroundModule.ExtendMode.Always) {
         for (class_2338 feet : feetPositions) {
            for (class_2350 dir : HORIZONTALS) {
               this.placeExtendBlocks(placePoses, feet, new class_2338(dir.method_10148(), 0, dir.method_10165()));
            }
         }
      } else {
         class_1511 crystal = this.findExtendCrystal();
         if (crystal != null) {
            this.lastExtendOffset = crystal.method_24515().method_10059(mc.field_1724.method_24515());
            this.lastExtendThreat = now;
         }

         if (this.lastExtendOffset != null && now - this.lastExtendThreat < 1000L) {
            for (class_2338 feet : feetPositions) {
               this.placeExtendBlocks(placePoses, feet, this.lastExtendOffset);
            }
         }
      }
   }

   private class_1511 findExtendCrystal() {
      class_238 box = mc.field_1724.method_5829().method_1009(1.5, 0.5, 1.5).method_989(0.0, 1.0, 0.0);
      class_243 eye = mc.field_1724.method_33571();
      class_1511 closest = null;
      double bestSq = Double.MAX_VALUE;

      for (class_1511 c : mc.field_1687.method_18467(class_1511.class, box)) {
         double d = c.method_5707(eye);
         if (d < bestSq) {
            bestSq = d;
            closest = c;
         }
      }

      return closest;
   }

   private void placeExtendBlocks(List<class_2338> out, class_2338 feetPos, class_2338 crystalOffset) {
      if (crystalOffset != null) {
         int normDx = Integer.signum(crystalOffset.method_10263());
         int normDz = Integer.signum(crystalOffset.method_10260());
         boolean isDiagonal = normDx != 0 && normDz != 0;
         boolean isCardinal = normDx != 0 ^ normDz != 0;
         if (isDiagonal) {
            this.addExtendIfReplaceable(out, feetPos.method_10069(normDx, 0, normDz));
            this.addExtendIfReplaceable(out, feetPos.method_10069(normDx * 2, 0, 0));
            this.addExtendIfReplaceable(out, feetPos.method_10069(0, 0, normDz * 2));
         } else if (isCardinal) {
            class_2338 diagonal1;
            class_2338 diagonal2;
            class_2338 straightBlock;
            if (normDx != 0) {
               diagonal1 = feetPos.method_10069(normDx, 0, 1);
               diagonal2 = feetPos.method_10069(normDx, 0, -1);
               straightBlock = feetPos.method_10069(normDx * 2, 0, 0);
            } else {
               diagonal1 = feetPos.method_10069(1, 0, normDz);
               diagonal2 = feetPos.method_10069(-1, 0, normDz);
               straightBlock = feetPos.method_10069(0, 0, normDz * 2);
            }

            this.addExtendIfReplaceable(out, diagonal1);
            this.addExtendIfReplaceable(out, diagonal2);
            this.addExtendIfReplaceable(out, straightBlock);
         }
      }
   }

   private void applyMineProtect(List<class_2338> feetPositions, List<class_2338> placePoses) {
      BreakIndicatorsModule indicators = Homovore.moduleManager.getModuleByClass(BreakIndicatorsModule.class);
      if (indicators != null && indicators.isEnabled()) {
         for (BreakIndicatorsModule.BreakInfo info : indicators.getActiveBreaksSnapshot().values()) {
            class_1657 player = info.player();
            if (player != null && player != mc.field_1724 && !Homovore.friendManager.isFriend(player)) {
               this.applyMineProtectBlock(feetPositions, placePoses, info.pos());
            }
         }
      }
   }

   private void applyMineProtectBlock(List<class_2338> feetPositions, List<class_2338> placePoses, class_2338 mined) {
      if (mined != null) {
         for (class_2338 feet : feetPositions) {
            if (mined.method_10264() == feet.method_10264()) {
               int dx = mined.method_10263() - feet.method_10263();
               int dz = mined.method_10260() - feet.method_10260();
               if (Math.abs(dx) + Math.abs(dz) == 1) {
                  this.addMineProtectIfReplaceable(placePoses, mined.method_10084());
                  this.addMineProtectIfReplaceable(placePoses, mined.method_10074());
                  if (dx != 0) {
                     this.addMineProtectIfReplaceable(placePoses, feet.method_10069(dx, 0, 1));
                     this.addMineProtectIfReplaceable(placePoses, feet.method_10069(dx, 0, -1));
                     this.addMineProtectIfReplaceable(placePoses, feet.method_10069(dx * 2, 0, 0));
                  } else {
                     this.addMineProtectIfReplaceable(placePoses, feet.method_10069(1, 0, dz));
                     this.addMineProtectIfReplaceable(placePoses, feet.method_10069(-1, 0, dz));
                     this.addMineProtectIfReplaceable(placePoses, feet.method_10069(0, 0, dz * 2));
                  }
               }
            }
         }
      }
   }

   private void addMineProtectIfReplaceable(List<class_2338> out, class_2338 pos) {
      class_2680 state = mc.field_1687.method_8320(pos);
      if (state.method_26215() || state.method_45474()) {
         this.wantedPoses.add(pos.method_10062());
         this.extendPoses.add(pos.method_10062());
         out.add(pos);
      }
   }

   private void addExtendIfReplaceable(List<class_2338> out, class_2338 pos) {
      class_2680 below = mc.field_1687.method_8320(pos.method_10074());
      if (below.method_27852(class_2246.field_10540) || below.method_27852(class_2246.field_9987)) {
         this.wantedPoses.add(pos.method_10062());
         this.extendPoses.add(pos.method_10062());
         if (!this.fireworkPoses.contains(pos)) {
            class_2680 state = mc.field_1687.method_8320(pos);
            if (state.method_26215() || state.method_45474()) {
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
            boolean lowHealth = mc.field_1724.method_6032() < 10.0F;
            if (!singleFootCell && !lowHealth) {
               Set<class_2338> myFootCells = new HashSet<>();

               for (int x = minX; x <= maxX; x++) {
                  for (int z = minZ; z <= maxZ; z++) {
                     myFootCells.add(new class_2338(x, feetY, z));
                  }
               }

               for (class_2338 pos : this.opponentSurroundPoses) {
                  if (!this.isNearMyPerimeter(pos, myFootCells)) {
                     this.helpBlockedPoses.add(pos.method_10062());
                  }
               }
            }
         }
      }
   }

   private void buildOpponentSurroundPoses() {
      class_2338 self = mc.field_1724.method_24515();

      for (class_1657 p : mc.field_1687.method_18456()) {
         if (p != mc.field_1724 && !Homovore.friendManager.isFriend(p)) {
            class_2338 oFeet = p.method_24515();
            double dx = oFeet.method_10263() - self.method_10263();
            double dz = oFeet.method_10260() - self.method_10260();
            if (!(Math.sqrt(dx * dx + dz * dz) > 5.0) && !this.isEntityPhased(p)) {
               class_238 pb = p.method_5829();
               int oy = oFeet.method_10264();
               int oMinX = PlaceUtil.minCell(pb.field_1323);
               int oMaxX = PlaceUtil.maxCell(pb.field_1320);
               int oMinZ = PlaceUtil.minCell(pb.field_1321);
               int oMaxZ = PlaceUtil.maxCell(pb.field_1324);

               for (int x = oMinX; x <= oMaxX; x++) {
                  for (int z = oMinZ; z <= oMaxZ; z++) {
                     class_2338 cell = new class_2338(x, oy, z);

                     for (class_2350 dir : HORIZONTALS) {
                        class_2338 adj = cell.method_10093(dir);
                        if (adj.method_10263() < oMinX || adj.method_10263() > oMaxX || adj.method_10260() < oMinZ || adj.method_10260() > oMaxZ) {
                           this.opponentSurroundPoses.add(adj.method_10062());
                        }
                     }

                     this.opponentSurroundPoses.add(cell.method_10074().method_10062());
                     this.opponentSurroundPoses.add(cell.method_10062());
                  }
               }
            }
         }
      }
   }

   private boolean isNearMyPerimeter(class_2338 pos, Set<class_2338> myFootCells) {
      for (class_2338 cell : myFootCells) {
         if (pos.equals(cell)
            || pos.equals(cell.method_10084())
            || pos.equals(cell.method_10074())
            || pos.equals(cell.method_10095())
            || pos.equals(cell.method_10072())
            || pos.equals(cell.method_10078())
            || pos.equals(cell.method_10067())) {
            return true;
         }
      }

      return false;
   }

   private boolean isEntityPhased(class_1657 p) {
      class_2680 state = mc.field_1687.method_8320(p.method_24515());
      return !state.method_26215() && !state.method_45474();
   }

   private boolean tryFirework(class_2338 pos, long now, List<class_2338> out) {
      if (this.cachedFireworkSlot < 0) {
         return false;
      }

      if (this.hasLiveFireworkAt(pos)) {
         this.fireworkPoses.add(pos.method_10062());
         return true;
      }

      Long last = this.fireworkDeployedAt.get(pos);
      if (last != null && now - last < 500L) {
         this.fireworkPoses.add(pos.method_10062());
         return true;
      }

      if (!this.canRocket(pos, now)) {
         return false;
      }

      if (this.speedMineClaims(pos)) {
         return false;
      }

      this.fireworkPoses.add(pos.method_10062());
      out.add(pos);
      return true;
   }

   private boolean canRocket(class_2338 pos, long now) {
      return (this.isHot(pos, now) || this.crystalProtect.getValue() && this.hasCrystalAt(pos))
         && this.isFullBlock(pos.method_10084())
         && this.isCrystalBase(pos.method_10074())
         && this.hasFireworkCornerSupport(pos);
   }

   private boolean isCrystalBase(class_2338 pos) {
      class_2680 state = mc.field_1687.method_8320(pos);
      return state.method_27852(class_2246.field_10540) || state.method_27852(class_2246.field_9987);
   }

   private boolean hasCrystalAt(class_2338 pos) {
      class_238 box = new class_238(pos).method_1009(0.5, 1.0, 0.5);
      return !mc.field_1687.method_18467(class_1511.class, box).isEmpty();
   }

   private boolean hasFireworkCornerSupport(class_2338 pos) {
      class_238 bounds = mc.field_1724.method_5829();
      int minX = PlaceUtil.minCell(bounds.field_1323);
      int maxX = PlaceUtil.maxCell(bounds.field_1320);
      int minZ = PlaceUtil.minCell(bounds.field_1321);
      int maxZ = PlaceUtil.maxCell(bounds.field_1324);
      int x = pos.method_10263();
      int z = pos.method_10260();
      if (z < minZ && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.method_10067()) && this.isFullBlock(pos.method_10078());
      } else if (z > maxZ && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.method_10067()) && this.isFullBlock(pos.method_10078());
      } else if (x < minX && z >= minZ && z <= maxZ) {
         return this.isFullBlock(pos.method_10095()) && this.isFullBlock(pos.method_10072());
      } else {
         return x > maxX && z >= minZ && z <= maxZ ? this.isFullBlock(pos.method_10095()) && this.isFullBlock(pos.method_10072()) : false;
      }
   }

   private boolean isHot(class_2338 pos, long now) {
      return this.breakCounts.getOrDefault(pos, 0) >= 3;
   }

   private boolean isFullBlock(class_2338 pos) {
      return class_2248.method_9614(mc.field_1687.method_8320(pos).method_26220(mc.field_1687, pos));
   }

   private boolean hasLiveFireworkAt(class_2338 pos) {
      class_238 box = new class_238(
         pos.method_10263() + 0.2, pos.method_10264(), pos.method_10260() + 0.2, pos.method_10263() + 0.8, pos.method_10264() + 4.0, pos.method_10260() + 0.8
      );

      for (class_1671 fw : mc.field_1687.method_18467(class_1671.class, box)) {
         if (fw.method_5805()) {
            return true;
         }
      }

      return false;
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), class_3532.method_15340(a, 0, 255));
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
