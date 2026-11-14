package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.render.BlockRender;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.world.ExplosionMath;
import dev.leonetic.util.world.PlacementWorldSnapshot;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1675;
import net.minecraft.class_1684;
import net.minecraft.class_1802;
import net.minecraft.class_1922;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_3486;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_4184;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class PearlBlockerModule extends Module {
   private static final double AIR_DRAG = 0.99;
   private static final double WATER_DRAG = 0.8;
   private static final double WATER_STALL_SPEED = 0.015;
   private static final int HORIZON_TICKS = 60;
   private static final int MIN_LEAD_TICKS = 3;
   private static final int MIN_REMAINING_TICKS = 2;
   private static final int MIN_CRYSTAL_LEAD_TICKS = 2;
   private static final long CRYSTAL_GRACE_MILLIS = 400L;
   private static final long POP_TTL_MILLIS = 1000L;
   private static final double STRAIGHT_UP_PUSH = 0.5;
   private static final double SURVIVE_MARGIN = 1.0;
   private static final PlacementManager.Options OPTIONS = new PlacementManager.Options(true, false, false, false);
   public final Setting<Boolean> crystal = this.bool("Crystal", true)
      .describe("Pops a crystal under a passing pearl so the blast throws it up and away; obsidian is placed whenever the path has nothing to stand one on.");
   public final Setting<Double> maxSelfDamage = this.number("MaxSelfDamage", (double)3.0, (double)0.0, (double)36.0)
      .describe("Never sets off a crystal that would take more than this off us.")
      .visibleWhen(() -> this.crystal.getValue());
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.5, (double)0.05, (double)2.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Color> sideColor = this.color("SideColor", 255, 0, 0, 44).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 0).visibleWhen(() -> this.render.getValue()).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)1.5, (double)0.1, (double)8.0)
      .visibleWhen(() -> this.render.getValue())
      .page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(() -> this.render.getValue()).page("Render");
   private final Set<Integer> handled = ConcurrentHashMap.newKeySet();
   private final Map<Integer, Long> crystalClaims = new ConcurrentHashMap<>();
   private final Map<Integer, Long> popped = new ConcurrentHashMap<>();
   private final Map<class_2338, Long> renderMap = new ConcurrentHashMap<>();

   public PearlBlockerModule() {
      super("PearlBlocker", Category.COMBAT, "Catches enemy pearls by placing obsidian in their flight path.");
   }

   @Override
   public void onEnable() {
      this.clearState();
   }

   @Override
   public void onDisable() {
      this.clearState();
   }

   private void clearState() {
      this.handled.clear();
      this.crystalClaims.clear();
      this.popped.clear();
      this.renderMap.clear();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && NetworkThreadExecutor.isConnected()) {
         long now = System.currentTimeMillis();
         this.expireRenders(now);
         this.expirePops(now);
         this.expireCrystalClaims(now);
         this.forgetGonePearls();
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available() && !inventory.deadOrDying()) {
            List<class_1684> pearls = this.enemyPearls();
            if (!pearls.isEmpty()) {
               if (this.crystal.getValue()) {
                  this.popCrystals(pearls, now);
               }

               boolean useObsidian = InventoryUtil.find(class_1802.field_8281, InventoryUtil.HOTBAR_SCOPE).found();
               boolean useCrystal = this.crystal.getValue() && InventoryUtil.find(class_1802.field_8301, InventoryUtil.HOTBAR_SCOPE).found();
               if (useObsidian || useCrystal) {
                  List<PearlBlockerModule.Interception> interceptions = new ArrayList<>();

                  for (class_1684 pearl : pearls) {
                     if (!this.handled.contains(pearl.method_5628())) {
                        PearlBlockerModule.Interception interception = this.findInterception(pearl, useCrystal, useObsidian);
                        if (interception != null) {
                           interceptions.add(interception);
                        }
                     }
                  }

                  if (!interceptions.isEmpty()) {
                     List<PearlBlockerModule.Interception> crystalReady = interceptions.stream()
                        .filter(interceptionx -> interceptionx.crystal() != null)
                        .toList();
                     if (!crystalReady.isEmpty()) {
                        this.answerWithCrystal(
                           crystalReady.stream().min(Comparator.comparingInt(interceptionx -> interceptionx.crystal().arrivalTicks())).orElseThrow(), now
                        );
                     } else {
                        List<PearlBlockerModule.Interception> blocks = new ArrayList<>(
                           interceptions.stream().filter(interceptionx -> interceptionx.pos() != null).toList()
                        );
                        if (!blocks.isEmpty()) {
                           blocks.sort(
                              Comparator.comparingInt(PearlBlockerModule.Interception::remainingTicks)
                                 .reversed()
                                 .thenComparingInt(PearlBlockerModule.Interception::arrivalTicks)
                                 .thenComparingInt(interceptionx -> interceptionx.pearl().field_6012)
                           );
                           this.placeBlock(blocks.getFirst(), now);
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.handled.clear();
         this.crystalClaims.clear();
         this.popped.clear();
      }
   }

   private void placeBlock(PearlBlockerModule.Interception best, long now) {
      int pearlId = best.pearl().method_5628();
      if (this.handled.add(pearlId)) {
         class_2338 pos = best.pos();
         this.renderMap.put(pos, now);
         Homovore2Client.placements().place(pos, null, class_1802.field_8281, OPTIONS).thenAccept(sent -> {
            if (!Boolean.TRUE.equals(sent)) {
               this.handled.remove(pearlId);
               this.renderMap.remove(pos);
            }
         });
      }
   }

   private void answerWithCrystal(PearlBlockerModule.Interception best, long now) {
      int pearlId = best.pearl().method_5628();
      if (this.handled.add(pearlId)) {
         PearlBlockerModule.CrystalPlan plan = best.crystal();
         class_2338 cell = plan.cell();
         this.renderMap.put(cell, now);
         this.crystalClaims.put(pearlId, now + plan.arrivalTicks() * 50L + 400L);
         class_3965 hit = new class_3965(crystalSource(cell), class_2350.field_11036, cell.method_10074(), false);
         Homovore2Client.placements().useItemOn(List.of(hit), class_1802.field_8301, true).thenAccept(sent -> {
            if (sent == null || sent <= 0) {
               this.handled.remove(pearlId);
               this.crystalClaims.remove(pearlId);
               this.renderMap.remove(cell);
            }
         });
      }
   }

   private void popCrystals(List<class_1684> pearls, long now) {
      class_243 eye = mc.field_1724.method_33571();
      double breakReach = 3.0;
      List<class_243[]> paths = pearls.stream().map(pearl -> path(pearl, 2)).toList();

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 && !entity.method_31481()) {
            int id = entity.method_5628();
            if (!this.popped.containsKey(id)) {
               class_238 box = entity.method_5829();
               if (!(distanceToBoxSqr(eye, box) > breakReach * breakReach)) {
                  class_238 window = upperHalf(box);
                  class_243 source = new class_243(entity.method_23317(), box.field_1322, entity.method_23321());

                  for (class_243[] path : paths) {
                     class_243 hit = entryPoint(window, path[0], path[1]);
                     if (hit == null) {
                        hit = entryPoint(window, path[1], path[2]);
                     }

                     if (hit != null && inBlastRange(source, hit) && this.blastsAway(source, hit)) {
                        if (this.survivable(source)) {
                           this.popped.put(id, now);
                           this.renderMap.put(class_2338.method_49638(source).method_10074(), now);
                           NetworkThreadExecutor.<Boolean>submit(context -> {
                              if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
                                 return false;
                              }

                              if (!context.attack(id)) {
                                 return false;
                              }

                              context.swing(class_1268.field_5808);
                              return true;
                           }).thenAccept(sent -> {
                              if (!Boolean.TRUE.equals(sent)) {
                                 this.popped.remove(id);
                              }
                           });
                        }
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private List<class_1684> enemyPearls() {
      List<class_1684> pearls = new ArrayList<>();

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1684 pearl
            && !pearl.method_31481()
            && this.isEnemyPearl(pearl)
            && (!pearl.method_5799() || !(pearl.method_18798().method_1033() < 0.015))) {
            pearls.add(pearl);
         }
      }

      return pearls;
   }

   private boolean isEnemyPearl(class_1684 pearl) {
      return pearl.method_24921() instanceof class_1657 owner && owner != mc.field_1724 && !Homovore2Client.friends().isFriend(owner);
   }

   private PearlBlockerModule.Interception findInterception(class_1684 pearl, boolean wantCrystal, boolean wantObsidian) {
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      if (!world.available()) {
         return null;
      }

      class_243 pos = pearl.method_73189();
      class_243 velocity = pearl.method_18798();
      double gravity = pearl.method_56989();
      boolean inWater = pearl.method_5799();
      class_2338 candidate = null;
      int candidateTick = 0;
      PearlBlockerModule.CrystalPlan crystalPlan = null;

      for (int tick = 0; tick < 60; tick++) {
         velocity = step(velocity, gravity, inWater);
         class_243 next = pos.method_1019(velocity);
         class_239 collision = this.findCollision(pearl, pos, next, tick == 0);
         class_243 pathEnd = collision.method_17783() == class_240.field_1333 ? next : collision.method_17784();
         int arrivalTicks = tick + 1;
         if (wantCrystal && crystalPlan == null && arrivalTicks >= 2 && collision.method_17783() == class_240.field_1333) {
            class_2338 cell = this.findCrystalCell(world, pos, next);
            if (cell != null) {
               crystalPlan = new PearlBlockerModule.CrystalPlan(cell, arrivalTicks);
            }
         }

         if (wantObsidian && candidate == null && arrivalTicks >= 3) {
            candidate = this.findPlacementCell(world, pos, pathEnd);
            if (candidate != null) {
               candidateTick = arrivalTicks;
            }
         }

         if (collision.method_17783() != class_240.field_1333) {
            int remainingTicks = arrivalTicks - candidateTick;
            if (candidate != null && remainingTicks >= 2) {
               return new PearlBlockerModule.Interception(pearl, candidate, candidateTick, remainingTicks, crystalPlan);
            }

            return crystalOnly(pearl, crystalPlan);
         }

         pos = next;
         inWater = mc.field_1687.method_8316(class_2338.method_49638(pos)).method_15767(class_3486.field_15517);
         if (pos.field_1351 < mc.field_1687.method_31607() - 5) {
            return crystalOnly(pearl, crystalPlan);
         }
      }

      return crystalOnly(pearl, crystalPlan);
   }

   private static PearlBlockerModule.Interception crystalOnly(class_1684 pearl, PearlBlockerModule.CrystalPlan crystalPlan) {
      return crystalPlan == null ? null : new PearlBlockerModule.Interception(pearl, null, 0, 0, crystalPlan);
   }

   private static class_243 step(class_243 velocity, double gravity, boolean inWater) {
      double drag = inWater ? 0.8 : 0.99;
      return new class_243(velocity.field_1352 * drag, (velocity.field_1351 - gravity) * drag, velocity.field_1350 * drag);
   }

   private static class_243[] path(class_1684 pearl, int ticks) {
      class_243[] points = new class_243[ticks + 1];
      class_243 pos = pearl.method_73189();
      class_243 velocity = pearl.method_18798();
      double gravity = pearl.method_56989();
      boolean inWater = pearl.method_5799();
      points[0] = pos;

      for (int tick = 1; tick <= ticks; tick++) {
         velocity = step(velocity, gravity, inWater);
         pos = pos.method_1019(velocity);
         points[tick] = pos;
      }

      return points;
   }

   private class_239 findCollision(class_1684 pearl, class_243 from, class_243 to, boolean includeEntities) {
      class_3965 blockHit = mc.field_1687.method_17742(new class_3959(from, to, class_3960.field_17558, class_242.field_1348, pearl));
      if (!includeEntities) {
         return blockHit;
      } else {
         class_238 box = pearl.method_5829().method_997(from.method_1020(pearl.method_73189()));
         class_3966 entityHit = class_1675.method_18077(
            mc.field_1687,
            pearl,
            from,
            to,
            box.method_18804(to.method_1020(from)).method_1014(1.0),
            entity -> entity != pearl.method_24921() && entity.method_5805() && !entity.method_7325() && entity.method_5863()
         );
         if (entityHit == null) {
            return blockHit;
         } else {
            return (class_239)(blockHit.method_17783() != class_240.field_1333
                  && !(from.method_1025(entityHit.method_17784()) < from.method_1025(blockHit.method_17784()))
               ? blockHit
               : entityHit);
         }
      }
   }

   private class_2338 findPlacementCell(PlacementWorldSnapshot.Snapshot world, class_243 from, class_243 to) {
      return (class_2338)class_1922.method_17744(from, to, this, (module, pos) -> {
         if (!this.fillable(world, pos)) {
            return null;
         } else {
            return !mc.field_1687.method_8320(pos.method_10074()).method_26220(mc.field_1687, pos.method_10074()).method_1110() ? null : pos.method_10062();
         }
      }, module -> null);
   }

   private boolean free(PlacementWorldSnapshot.Snapshot world, class_2338 pos) {
      return world.known(pos) && world.replaceable(pos) && !world.selfCollision(pos) && !world.entityCollision(pos);
   }

   private boolean fillable(PlacementWorldSnapshot.Snapshot world, class_2338 pos) {
      return !this.free(world, pos) ? false : world.withinPlaceReach(pos);
   }

   private class_2338 findCrystalCell(PlacementWorldSnapshot.Snapshot world, class_243 from, class_243 to) {
      return (class_2338)class_1922.method_17744(from, to, this, (module, pos) -> {
         class_2338 cell = pos.method_10074();
         if (this.hasSupport(cell.method_10074()) && this.canHostCrystal(world, cell)) {
            class_243 source = crystalSource(cell);
            class_243 hit = entryPoint(upperHalf(crystalBox(cell)), from, to);
            if (hit != null && inBlastRange(source, hit) && this.blastsAway(source, hit)) {
               return !this.survivable(source) ? null : cell.method_10062();
            } else {
               return null;
            }
         } else {
            return null;
         }
      }, module -> null);
   }

   private boolean hasSupport(class_2338 base) {
      return mc.field_1687.method_8320(base).method_27852(class_2246.field_10540) || mc.field_1687.method_8320(base).method_27852(class_2246.field_9987);
   }

   private boolean canHostCrystal(PlacementWorldSnapshot.Snapshot world, class_2338 cell) {
      if (this.free(world, cell) && mc.field_1687.method_8320(cell).method_26215()) {
         if (!mc.field_1687
            .method_8335(
               (class_1297)null,
               new class_238(
                  cell.method_10263(),
                  cell.method_10264(),
                  cell.method_10260(),
                  cell.method_10263() + 1.0,
                  cell.method_10264() + 2.0,
                  cell.method_10260() + 1.0
               )
            )
            .isEmpty()) {
            return false;
         }

         class_243 eye = mc.field_1724.method_33571();
         class_2338 support = cell.method_10074();
         double placeReach = 4.5;
         if (distanceToBoxSqr(
               eye,
               new class_238(
                  support.method_10263(),
                  support.method_10264(),
                  support.method_10260(),
                  support.method_10263() + 1.0,
                  support.method_10264() + 1.0,
                  support.method_10260() + 1.0
               )
            )
            > placeReach * placeReach) {
            return false;
         }

         double breakReach = 3.0;
         return distanceToBoxSqr(eye, crystalBox(cell)) <= breakReach * breakReach;
      } else {
         return false;
      }
   }

   private static class_238 crystalBox(class_2338 cell) {
      return new class_238(
         cell.method_10263() - 0.5,
         cell.method_10264(),
         cell.method_10260() - 0.5,
         cell.method_10263() + 1.5,
         cell.method_10264() + 2.0,
         cell.method_10260() + 1.5
      );
   }

   private static class_243 crystalSource(class_2338 cell) {
      return new class_243(cell.method_10263() + 0.5, cell.method_10264(), cell.method_10260() + 0.5);
   }

   private static class_238 upperHalf(class_238 box) {
      return new class_238(box.field_1323, (box.field_1322 + box.field_1325) * 0.5, box.field_1321, box.field_1320, box.field_1325, box.field_1324);
   }

   private static class_243 entryPoint(class_238 box, class_243 from, class_243 to) {
      return box.method_1006(from) ? from : (class_243)box.method_992(from, to).orElse(null);
   }

   private static boolean inBlastRange(class_243 source, class_243 point) {
      return source.method_1025(point) <= 144.0;
   }

   private boolean blastsAway(class_243 source, class_243 pearl) {
      double pushX = pearl.field_1352 - source.field_1352;
      double pushZ = pearl.field_1350 - source.field_1350;
      if (pushX * pushX + pushZ * pushZ < 0.25) {
         return true;
      }

      class_243 self = mc.field_1724.method_73189();
      return pushX * (pearl.field_1352 - self.field_1352) + pushZ * (pearl.field_1350 - self.field_1350) >= 0.0;
   }

   private boolean survivable(class_243 source) {
      double damage = ExplosionMath.selfDamage(mc.field_1687, mc.field_1724, source);
      return damage <= this.maxSelfDamage.getValue() && damage + 1.0 < mc.field_1724.method_6032() + mc.field_1724.method_6067();
   }

   private static double distanceToBoxSqr(class_243 point, class_238 box) {
      double dx = Math.max(Math.max(box.field_1323 - point.field_1352, 0.0), point.field_1352 - box.field_1320);
      double dy = Math.max(Math.max(box.field_1322 - point.field_1351, 0.0), point.field_1351 - box.field_1325);
      double dz = Math.max(Math.max(box.field_1321 - point.field_1350, 0.0), point.field_1350 - box.field_1324);
      return dx * dx + dy * dy + dz * dz;
   }

   private void forgetGonePearls() {
      this.handled.removeIf(this::gone);
      this.crystalClaims.keySet().removeIf(this::gone);
   }

   private boolean gone(int id) {
      class_1297 entity = mc.field_1687.method_8469(id);
      return entity == null || entity.method_31481();
   }

   private void expireCrystalClaims(long now) {
      this.crystalClaims.entrySet().removeIf(entry -> {
         if (now < entry.getValue()) {
            return false;
         }

         this.handled.remove(entry.getKey());
         return true;
      });
   }

   private void expirePops(long now) {
      this.popped.entrySet().removeIf(entry -> now - entry.getValue() > 1000L || this.gone(entry.getKey()));
   }

   private void expireRenders(long now) {
      this.renderMap.values().removeIf(placed -> BlockRender.expired(placed, now, this.fadeTime.getValue()));
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (this.render.getValue() && !this.renderMap.isEmpty()) {
         long now = System.currentTimeMillis();

         for (Entry<class_2338, Long> entry : this.renderMap.entrySet()) {
            if (!BlockRender.expired(entry.getValue(), now, this.fadeTime.getValue())) {
               BlockRender.cuboid(
                  entry.getKey(),
                  this.lineColor.getValue(),
                  this.sideColor.getValue(),
                  this.lineWidth.getValue().floatValue(),
                  BlockRender.alpha(entry.getValue(), now, this.fadeTime.getValue()),
                  this.throughWalls.getValue()
               );
            }
         }
      }
   }

   private record CrystalPlan(class_2338 cell, int arrivalTicks) {
   }

   private record Interception(class_1684 pearl, class_2338 pos, int arrivalTicks, int remainingTicks, PearlBlockerModule.CrystalPlan crystal) {
   }
}
