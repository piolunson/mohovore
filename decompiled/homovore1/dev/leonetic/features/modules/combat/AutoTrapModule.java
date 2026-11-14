package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.TargetsModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.PlacementManager;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2767;
import net.minecraft.class_3417;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_2350.class_2353;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public class AutoTrapModule extends Module {
   private static final int BOOST_DURATION_TICKS = 40;
   private static final double BOOST_DETECT_RANGE_SQ = 9.0;
   private static final long INTERCEPT_TIMEOUT_MS = 500L;
   private final Setting<AutoTrapModule.BlockType> block = this.mode("Block", AutoTrapModule.BlockType.OBSIDIAN).setPage("General");
   private final Setting<Double> range = this.num("TargetRange", 8.2, 1.0, 16.0).setPage("General");
   private final Setting<Boolean> pauseEat = this.bool("PauseEat", true).setPage("General");
   private final Setting<Boolean> selfToggle = this.bool("SelfToggle", true).setPage("General");
   private final Setting<Boolean> skipCrystalSpots = this.bool("SkipCrystalSpots", false).setPage("General");
   private final Setting<Boolean> prediction = this.bool("Prediction", true).setPage("Prediction");
   private final Setting<Integer> predictionTicks = this.num("PredictionTicks", 1, 0, 20).setPage("Prediction");
   private final Setting<Integer> elytraTicks = this.num("ElytraPredictionTicks", 3, 0, 20).setPage("Prediction");
   private final Setting<Double> lookBlend = this.num("LookBlend", 0.6, 0.0, 1.0).setPage("Prediction");
   private final Setting<Boolean> faceFirst = this.bool("FaceFirst", true).setPage("Prediction");
   private final Setting<Integer> faceLeadBlocks = this.num("FaceLeadBlocks", 2, 0, 6).setPage("Prediction");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 50, 50, 40).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 50, 50, 40).setPage("Render");
   private final Set<class_2338> wantedPoses = ConcurrentHashMap.newKeySet();
   private final Set<class_2338> ownedQueued = new HashSet<>();
   private final Map<class_2338, Long> renderMap = new HashMap<>();
   private class_1309 target;
   private int cachedSlot = -1;
   private class_1792 cachedItem = null;
   private boolean buildInterceptNext = true;
   private long lastPlaceTime = 0L;
   private class_2338 lockedCenter;
   private class_2351 lockedPrimary;
   private class_2351 lockedSecondary;
   private class_2351 lockedTertiary;
   private int lockedPrimaryDir;
   private int lockedSecondaryDir;
   private int lockedTertiaryDir;
   private UUID lockedTargetUUID;
   private final Map<UUID, Integer> boostingTicks = new HashMap<>();
   private final PlacementManager.PlacementListener airRefillListener = (pos, nowAir) -> {
      this.ownedQueued.remove(pos);
      if (nowAir && this.wantedPoses.contains(pos)) {
         if (this.cachedSlot >= 0 && PlaceUtil.canPlace(pos)) {
            if (Homovore.placementManager.enqueue(pos, this.cachedItem)) {
               this.ownedQueued.add(pos);
            }
         }
      }
   };

   public AutoTrapModule() {
      super("AutoTrap", "Traps the closest target in obsidian, cobweb, or both to lock down their movement.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      Homovore.placementManager.addListener(this.airRefillListener);
      this.target = null;
      this.resetInterceptLock();
      this.boostingTicks.clear();
      this.lastPlaceTime = System.currentTimeMillis();
   }

   @Override
   public void onDisable() {
      Homovore.placementManager.removeListener(this.airRefillListener);
      Homovore.placementManager.removeQueuedFor(this.ownedQueued::contains);
      this.ownedQueued.clear();
      this.wantedPoses.clear();
      this.renderMap.clear();
      this.boostingTicks.clear();
      this.resetInterceptLock();
      this.target = null;
      this.cachedSlot = -1;
      this.cachedItem = null;
   }

   private void resetInterceptLock() {
      this.buildInterceptNext = true;
      this.lockedCenter = null;
      this.lockedPrimary = this.lockedSecondary = this.lockedTertiary = null;
      this.lockedPrimaryDir = this.lockedSecondaryDir = this.lockedTertiaryDir = 0;
      this.lockedTargetUUID = null;
   }

   @Subscribe(priority = 1)
   private void onPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof class_2767 pkt) {
         if (pkt.method_11894().comp_349() == class_3417.field_14702) {
            double sx = pkt.method_11890();
            double sy = pkt.method_11889();
            double sz = pkt.method_11893();
            mc.execute(() -> {
               if (!nullCheck()) {
                  for (class_1657 p : mc.field_1687.method_18456()) {
                     double dx = p.method_23317() - sx;
                     double dy = p.method_23318() - sy;
                     double dz = p.method_23321() - sz;
                     if (dx * dx + dy * dy + dz * dz < 9.0) {
                        this.boostingTicks.put(p.method_5667(), 40);
                        break;
                     }
                  }
               }
            });
         }
      }
   }

   @Subscribe(priority = 1)
   private void onTick(TickEvent event) {
      if (!nullCheck() && mc.field_1755 == null) {
         this.boostingTicks.entrySet().removeIf(e -> {
            int next = e.getValue() - 1;
            e.setValue(next);
            return next <= 0;
         });
         int slot = this.resolveSlot();
         if (slot < 0) {
            this.cachedSlot = -1;
            this.cachedItem = null;
            this.wantedPoses.clear();
         } else {
            this.cachedSlot = slot;
            this.cachedItem = mc.field_1724.method_31548().method_5438(slot).method_7909();
            class_1309 sel = this.findTarget();
            if (this.target == null || !this.isValidStillTarget(this.target)) {
               this.target = sel;
               if (this.target == null) {
                  this.pruneAll();
                  this.resetInterceptLock();
                  if (this.selfToggle.getValue()) {
                     this.disable();
                  }

                  return;
               }
            }

            if (this.lockedTargetUUID != null && !this.lockedTargetUUID.equals(this.target.method_5667())) {
               this.resetInterceptLock();
            }

            if (this.lockedTargetUUID != null && !this.target.method_6128()) {
               this.resetInterceptLock();
            }

            if (!this.pauseEat.getValue() || !mc.field_1724.method_6115()) {
               if (!EatUtil.shouldDefer()) {
                  if (this.target.method_6128() && !this.buildInterceptNext && System.currentTimeMillis() - this.lastPlaceTime > 500L) {
                     this.resetInterceptLock();
                  }

                  class_243 predicted = this.predictPosition(this.target);
                  Set<class_2338> placePoses;
                  if (this.target.method_6128()) {
                     placePoses = this.buildInterceptNext ? this.elytraInterceptWedge(predicted, this.target) : this.elytraCloseShell(this.target);
                  } else if (isCubeHitbox(this.target)) {
                     placePoses = this.cubeTrap(predicted, this.target);
                  } else {
                     placePoses = this.standingTrap(predicted, this.target);
                  }

                  this.wantedPoses.clear();
                  this.wantedPoses.addAll(placePoses);
                  List<class_2338> reachable = new ArrayList<>();
                  class_243 eye = mc.field_1724.method_33571();

                  for (class_2338 pos : placePoses) {
                     if (eye.method_1022(class_243.method_24953(pos)) <= 5.1) {
                        reachable.add(pos.method_10062());
                     }
                  }

                  if (reachable.isEmpty()) {
                     this.pruneOwned();
                  } else {
                     class_243 sortPoint = new class_243(predicted.field_1352, this.target.method_23318(), predicted.field_1350);
                     reachable.sort(Comparator.comparingDouble(p -> class_243.method_24953(p).method_1025(sortPoint)));
                     if (this.faceFirst.getValue()) {
                        class_2350 lookDir = this.target.method_5735();
                        class_2338 anchor = this.target.method_6128() ? class_2338.method_49638(predicted) : this.target.method_24515();
                        class_2338 leadBase = isCubeHitbox(this.target) ? anchor : anchor.method_10084();
                        class_2338 prio = leadBase.method_10079(lookDir, this.faceLeadBlocks.getValue());
                        if (reachable.remove(prio)) {
                           reachable.add(0, prio);
                        } else if (this.wantedPoses.contains(prio) && eye.method_1022(class_243.method_24953(prio)) <= 5.1) {
                           reachable.add(0, prio);
                        }
                     }

                     if (this.target.method_6128() && eye.method_1022(predicted) > 3.5) {
                        if (this.buildInterceptNext) {
                           this.resetInterceptLock();
                        }
                     } else {
                        this.pruneOwned();
                        long now = System.currentTimeMillis();
                        boolean placedAny = false;

                        for (class_2338 pos : reachable) {
                           class_2680 state = mc.field_1687.method_8320(pos);
                           if (state.method_45474() && PlaceUtil.canPlace(pos) && Homovore.placementManager.enqueue(pos, this.cachedItem)) {
                              this.ownedQueued.add(pos);
                              this.renderMap.put(pos, now);
                              placedAny = true;
                           }
                        }

                        if (placedAny) {
                           this.lastPlaceTime = now;
                        }

                        if (this.target.method_6128() && placedAny && this.buildInterceptNext) {
                           this.buildInterceptNext = false;
                        }

                        long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
                        this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isCubeHitbox(class_1309 e) {
      return e.method_6128() || e.method_20448() || e.method_5681();
   }

   private Set<class_2338> standingTrap(class_243 predicted, class_1309 entity) {
      LinkedHashSet<class_2338> poses = new LinkedHashSet<>();
      double half = entity.method_17681() * 0.5;
      int x0 = PlaceUtil.minCell(predicted.field_1352 - half);
      int x1 = PlaceUtil.maxCell(predicted.field_1352 + half);
      int z0 = PlaceUtil.minCell(predicted.field_1350 - half);
      int z1 = PlaceUtil.maxCell(predicted.field_1350 + half);
      int feetY = entity.method_24515().method_10264();
      int headY = feetY + 1;
      int capY = feetY + 2;

      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            poses.add(new class_2338(x, capY, z));

            for (class_2350 d : class_2353.field_11062) {
               int nx = x + d.method_10148();
               int nz = z + d.method_10165();
               if (nx < x0 || nx > x1 || nz < z0 || nz > z1) {
                  poses.add(new class_2338(nx, headY, nz));
               }
            }
         }
      }

      if (!entity.method_24828()) {
         for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
               poses.add(new class_2338(x, feetY - 1, z));
            }
         }
      }

      return poses;
   }

   private Set<class_2338> cubeTrap(class_243 predicted, class_1309 entity) {
      LinkedHashSet<class_2338> poses = new LinkedHashSet<>();
      double half = entity.method_17681() * 0.5;
      double height = entity.method_17682();
      int x0 = PlaceUtil.minCell(predicted.field_1352 - half);
      int x1 = PlaceUtil.maxCell(predicted.field_1352 + half);
      int z0 = PlaceUtil.minCell(predicted.field_1350 - half);
      int z1 = PlaceUtil.maxCell(predicted.field_1350 + half);
      int y0 = PlaceUtil.minCell(entity.method_23318());
      int y1 = PlaceUtil.maxCell(entity.method_23318() + height);
      class_2338 feetBlock = entity.method_24515();
      Set<class_2338> crystalSpots = null;
      if (this.skipCrystalSpots.getValue()) {
         crystalSpots = new HashSet<>(4);

         for (class_2350 d : class_2353.field_11062) {
            crystalSpots.add(feetBlock.method_10093(d));
         }
      }

      for (int x = x0 - 1; x <= x1 + 1; x++) {
         for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int y = y0 - 1; y <= y1 + 1; y++) {
               int outsideX = x >= x0 && x <= x1 ? 0 : 1;
               int outsideZ = z >= z0 && z <= z1 ? 0 : 1;
               int outsideY = y >= y0 && y <= y1 ? 0 : 1;
               if (outsideX + outsideZ + outsideY == 1) {
                  class_2338 pos = new class_2338(x, y, z);
                  if (crystalSpots == null || !crystalSpots.contains(pos)) {
                     poses.add(pos);
                  }
               }
            }
         }
      }

      return poses;
   }

   private Set<class_2338> elytraInterceptWedge(class_243 predicted, class_1309 entity) {
      LinkedHashSet<class_2338> poses = new LinkedHashSet<>();
      this.lockedCenter = class_2338.method_49638(predicted);
      this.lockedTargetUUID = entity.method_5667();
      class_243 vel = entity.method_18798();
      if (vel.method_1027() < 1.0E-6) {
         class_243 look = entity.method_5720();
         vel = look.method_1027() < 1.0E-6 ? new class_243(1.0, 0.0, 0.0) : look;
      }

      class_2351[] axes = class_2351.values();
      double[] mag = new double[]{Math.abs(vel.field_1352), Math.abs(vel.field_1351), Math.abs(vel.field_1350)};
      int[] order = new int[]{0, 1, 2};

      for (int i = 0; i < 2; i++) {
         int max = i;

         for (int j = i + 1; j < 3; j++) {
            if (mag[order[j]] > mag[order[max]]) {
               max = j;
            }
         }

         if (max != i) {
            int t = order[i];
            order[i] = order[max];
            order[max] = t;
         }
      }

      this.lockedPrimary = axes[order[0]];
      this.lockedSecondary = axes[order[1]];
      this.lockedTertiary = axes[order[2]];
      this.lockedPrimaryDir = signOr1(componentAlong(vel, this.lockedPrimary));
      this.lockedSecondaryDir = signOr1(componentAlong(vel, this.lockedSecondary));
      this.lockedTertiaryDir = signOr1(componentAlong(vel, this.lockedTertiary));

      for (int i = 0; i < 2; i++) {
         for (int j = 0; j < 2; j++) {
            poses.add(
               offset(
                  offset(offset(this.lockedCenter, this.lockedPrimary, this.lockedPrimaryDir), this.lockedSecondary, i * this.lockedSecondaryDir),
                  this.lockedTertiary,
                  j * this.lockedTertiaryDir
               )
            );
         }
      }

      for (int i = 0; i < 2; i++) {
         poses.add(offset(offset(this.lockedCenter, this.lockedSecondary, 2 * this.lockedSecondaryDir), this.lockedTertiary, i * this.lockedTertiaryDir));
         poses.add(offset(offset(this.lockedCenter, this.lockedTertiary, 2 * this.lockedTertiaryDir), this.lockedSecondary, i * this.lockedSecondaryDir));
      }

      return poses;
   }

   private Set<class_2338> elytraCloseShell(class_1309 entity) {
      LinkedHashSet<class_2338> poses = new LinkedHashSet<>();
      class_238 bb = entity.method_5829();
      int feetY = entity.method_24515().method_10264();
      int minX = PlaceUtil.minCell(bb.field_1323);
      int maxX = PlaceUtil.maxCell(bb.field_1320);
      int minZ = PlaceUtil.minCell(bb.field_1321);
      int maxZ = PlaceUtil.maxCell(bb.field_1324);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            class_2338 feetPos = new class_2338(x, feetY, z);

            for (int ox = -1; ox <= 1; ox++) {
               for (int oz = -1; oz <= 1; oz++) {
                  if (Math.abs(ox) + Math.abs(oz) == 1) {
                     class_2338 adj = feetPos.method_10069(ox, 0, oz);
                     if (this.canFill(adj)) {
                        poses.add(adj);
                     }

                     class_2338 head = adj.method_10084();
                     if (this.canFill(head)) {
                        poses.add(head);
                     }
                  }
               }
            }

            class_2338 below = feetPos.method_10074();
            if (this.canFill(below)) {
               poses.add(below);
            }

            class_2338 top = feetPos.method_10086(2);
            if (this.canFill(top)) {
               poses.add(top);
            }
         }
      }

      return poses;
   }

   private boolean canFill(class_2338 pos) {
      class_2680 s = mc.field_1687.method_8320(pos);
      return s.method_26215() || s.method_45474();
   }

   private static double componentAlong(class_243 v, class_2351 axis) {
      return switch (axis) {
         case field_11048 -> v.field_1352;
         case field_11052 -> v.field_1351;
         case field_11051 -> v.field_1350;
         default -> throw new MatchException(null, null);
      };
   }

   private static class_2338 offset(class_2338 pos, class_2351 axis, int n) {
      return switch (axis) {
         case field_11048 -> pos.method_10069(n, 0, 0);
         case field_11052 -> pos.method_10069(0, n, 0);
         case field_11051 -> pos.method_10069(0, 0, n);
         default -> throw new MatchException(null, null);
      };
   }

   private static int signOr1(double v) {
      int s = (int)Math.signum(v);
      return s == 0 ? 1 : s;
   }

   private void pruneOwned() {
      if (!this.ownedQueued.isEmpty()) {
         Homovore.placementManager.removeQueuedFor(p -> this.ownedQueued.contains(p) && !this.wantedPoses.contains(p));
         this.ownedQueued.removeIf(p -> !this.wantedPoses.contains(p));
      }
   }

   private void pruneAll() {
      if (!this.ownedQueued.isEmpty()) {
         Homovore.placementManager.removeQueuedFor(this.ownedQueued::contains);
         this.ownedQueued.clear();
      }

      this.wantedPoses.clear();
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

   @Override
   public String getDisplayInfo() {
      return this.wantedPoses.isEmpty() ? null : String.valueOf(this.wantedPoses.size());
   }

   private int resolveSlot() {
      if (this.block.getValue() == AutoTrapModule.BlockType.HYBRID) {
         int web = this.hotbarSlotOf(class_1802.field_8786);
         return web >= 0 ? web : this.hotbarSlotOf(class_1802.field_8281);
      } else {
         return this.hotbarSlotOf(this.block.getValue().item);
      }
   }

   private int hotbarSlotOf(class_1792 item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() && r.type() != ResultType.OFFHAND ? r.slot() : -1;
   }

   private class_1309 findTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      double maxSq = this.range.getValue() * this.range.getValue();
      class_1309 best = null;
      double bestSq = Double.MAX_VALUE;

      for (class_1297 e : mc.field_1687.method_18112()) {
         if (e instanceof class_1309 living && (targets == null || targets.isValidPlayerTarget(e))) {
            double dSq = mc.field_1724.method_5858(e);
            if (!(dSq > maxSq) && !(dSq >= bestSq)) {
               bestSq = dSq;
               best = living;
            }
         }
      }

      return best;
   }

   private boolean isValidStillTarget(class_1309 e) {
      if (e != null && e.method_5805()) {
         if (mc.field_1724.method_5858(e) > this.range.getValue() * this.range.getValue()) {
            return false;
         }

         TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
         return targets == null || targets.isValidPlayerTarget(e);
      } else {
         return false;
      }
   }

   private class_243 predictPosition(class_1309 entity) {
      if (!this.prediction.getValue()) {
         return entity.method_73189();
      } else if (this.isAlreadyTrapped(entity)) {
         return entity.method_73189();
      } else {
         int ticks = entity.method_6128() ? this.elytraTicks.getValue() : this.predictionTicks.getValue();
         if (ticks <= 0) {
            return entity.method_73189();
         } else if (entity.method_6128()) {
            return this.boostingTicks.containsKey(entity.method_5667()) ? this.simulateBoostedElytra(entity, ticks) : this.simulateElytra(entity, ticks);
         } else {
            return this.simulateGround(entity, ticks);
         }
      }
   }

   private boolean isAlreadyTrapped(class_1309 entity) {
      if (entity.method_5681()) {
         return false;
      }

      class_2338 pos = entity.method_24515();
      if (mc.field_1687.method_8320(pos.method_10074()).method_45474()) {
         return false;
      }

      for (class_2350 d : class_2353.field_11062) {
         if (mc.field_1687.method_8320(pos.method_10084().method_10093(d)).method_45474()) {
            return false;
         }
      }

      return !mc.field_1687.method_8320(pos.method_10086(2)).method_45474();
   }

   private class_243 hybridHorizVel(class_1309 entity) {
      double dx = entity.method_23317() - entity.field_6014;
      double dz = entity.method_23321() - entity.field_5969;
      double speed = Math.hypot(dx, dz);
      if (speed < 1.0E-4) {
         return class_243.field_1353;
      }

      float yawRad = (float)Math.toRadians(entity.method_36454());
      double lookX = -Math.sin(yawRad);
      double lookZ = Math.cos(yawRad);
      double bias = this.lookBlend.getValue() * Math.min(1.0, speed / 0.21);
      return new class_243(dx * (1.0 - bias) + lookX * speed * bias, 0.0, dz * (1.0 - bias) + lookZ * speed * bias);
   }

   private class_243 simulateGround(class_1309 entity, int ticks) {
      class_243 pos = entity.method_73189();
      class_243 horiz = this.hybridHorizVel(entity);
      double vy = entity.method_23318() - entity.field_6036;
      boolean onGround = entity.method_24828();
      if (onGround && vy <= 0.05) {
         vy = 0.0;
      }

      class_243 vel = new class_243(horiz.field_1352, vy, horiz.field_1350);

      for (int i = 0; i < ticks; i++) {
         class_243 nextPos = pos.method_1019(vel);
         if (vel.field_1351 != 0.0) {
            class_243 rayStart = pos.method_1031(0.0, 0.5, 0.0);
            class_243 rayEnd = nextPos.method_1031(0.0, 0.5, 0.0);
            class_3965 hit = mc.field_1687.method_17742(new class_3959(rayStart, rayEnd, class_3960.field_17558, class_242.field_1348, entity));
            if (hit.method_17783() == class_240.field_1332 && hit.method_17780() == class_2350.field_11036) {
               return new class_243(nextPos.field_1352, hit.method_17777().method_10264() + 1.0, nextPos.field_1350);
            }
         }

         pos = nextPos;
         if (!onGround) {
            vel = new class_243(vel.field_1352 * 0.98, (vel.field_1351 - 0.08) * 0.98, vel.field_1350 * 0.98);
         }

         if (vel.method_1027() < 1.0E-4) {
            break;
         }
      }

      return pos;
   }

   private class_243 simulateElytra(class_1309 entity, int ticks) {
      class_243 pos = entity.method_73189();
      class_243 tickVel = new class_243(
         entity.method_23317() - entity.field_6014, entity.method_23318() - entity.field_6036, entity.method_23321() - entity.field_5969
      );
      class_243 look = entity.method_5720();
      double speed = tickVel.method_1033();
      double bias = this.lookBlend.getValue();
      class_243 vel = new class_243(
         tickVel.field_1352 * (1.0 - bias) + look.field_1352 * speed * bias,
         tickVel.field_1351 * (1.0 - bias) + look.field_1351 * speed * bias,
         tickVel.field_1350 * (1.0 - bias) + look.field_1350 * speed * bias
      );
      float pitchRad = (float)Math.toRadians(entity.method_36455());
      double cos = Math.cos(pitchRad);

      for (int i = 0; i < ticks; i++) {
         pos = pos.method_1019(vel);
         double horizSpeed = Math.hypot(vel.field_1352, vel.field_1350);
         double len = vel.method_1033();
         double liftFactor = cos * cos * Math.min(1.0, len / 0.4);
         double vy = vel.field_1351 + (-0.04 + liftFactor * 0.06);
         if (vel.field_1351 < 0.0 && horizSpeed > 0.0) {
            vy += -0.1 * vel.field_1351 * liftFactor;
         }

         class_243 vAfterLift = new class_243(vel.field_1352, vy, vel.field_1350);
         class_243 align = new class_243(
            look.field_1352 * 0.1 + (look.field_1352 * 1.5 - vAfterLift.field_1352) * 0.01,
            look.field_1351 * 0.1 + (look.field_1351 * 1.5 - vAfterLift.field_1351) * 0.01,
            look.field_1350 * 0.1 + (look.field_1350 * 1.5 - vAfterLift.field_1350) * 0.01
         );
         class_243 vAligned = vAfterLift.method_1019(align);
         vel = new class_243(vAligned.field_1352 * 0.99, vAligned.field_1351 * 0.98, vAligned.field_1350 * 0.99);
      }

      return pos;
   }

   private class_243 simulateBoostedElytra(class_1309 entity, int ticks) {
      class_243 pos = entity.method_73189();
      class_243 vel = new class_243(
         entity.method_23317() - entity.field_6014, entity.method_23318() - entity.field_6036, entity.method_23321() - entity.field_5969
      );

      for (int i = 0; i < ticks; i++) {
         pos = pos.method_1019(vel);
         double vy = Math.max(vel.field_1351, -3.92);
         vel = new class_243(vel.field_1352 * 0.991, vy, vel.field_1350 * 0.991);
      }

      return pos;
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), class_3532.method_15340(a, 0, 255));
   }

   public enum BlockType {
      OBSIDIAN(class_1802.field_8281),
      COBWEB(class_1802.field_8786),
      HYBRID(null);

      final class_1792 item;

      BlockType(class_1792 item) {
         this.item = item;
      }
   }
}
