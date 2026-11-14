package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.TargetsModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.DamageSyncTracker;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.player.EatUtil;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.class_1267;
import net.minecraft.class_1268;
import net.minecraft.class_1280;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_3532;
import net.minecraft.class_5134;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_2350.class_2353;

public class PistonCrystalModule extends Module {
   private static final String ROTATION_ID = "PistonCrystal";
   private static final int ROTATION_PRIORITY = 70;
   private static final double TARGET_RANGE = 10.0;
   private static final double TARGET_RANGE_SQ = 100.0;
   private static final int EXTEND_TIMEOUT = 10;
   private static final double NUDGE = 0.0625;
   private static final double PLACE_RANGE = 6.0;
   private static final double BREAK_RANGE = 3.0;
   private final Setting<Double> minDamage = this.num("MinDamage", 6.0, 0.0, 36.0).setPage("General");
   private final Setting<Integer> delay = this.num("Delay", 10, 0, 20).setPage("General");
   private final Setting<Boolean> autoBase = this.bool("AutoBase", true).setPage("General");
   private final Setting<Boolean> swing = this.bool("Swing", true).setPage("General");
   private final Setting<Boolean> damageSync = this.bool("DamageSync", false).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.2F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 0, 62, 122, 148).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 0, 62, 122, 148).setPage("Render");
   private final Map<class_2338, Integer> renderMap = new HashMap<>();
   private PistonCrystalModule.Setup pending;
   private PistonCrystalModule.Setup active;
   private int delayTicks;
   private int rotateHeld;
   private int waitTicks;
   private float lastDamage;
   private PistonCrystalModule.ArmorProfile targetProfile;
   private class_1267 targetDifficulty;
   private final class_2339 rayCursor = new class_2339();

   public PistonCrystalModule() {
      super("PistonCrystal", "Pushes a crystal over a surrounded target with a piston and explodes it.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.resetState();
      this.delayTicks = this.delay.getValue();
   }

   @Override
   public void onDisable() {
      Homovore.rotationManager.cancel("PistonCrystal");
      this.resetState();
   }

   private void resetState() {
      this.pending = null;
      this.active = null;
      this.delayTicks = 0;
      this.rotateHeld = 0;
      this.waitTicks = 0;
      this.lastDamage = 0.0F;
      this.renderMap.clear();
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck() && !mc.field_1724.method_29504()) {
         if (!EatUtil.shouldDefer()) {
            int fadeTicks = (int)(this.fadeTime.getValue() * 20.0F);
            int now = mc.field_1724.field_6012;
            this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeTicks);
            if (this.active != null) {
               this.tickActive();
            } else if (this.delayTicks < this.delay.getValue()) {
               this.delayTicks++;
            } else {
               int pistonSlot = this.pistonSlot();
               int redstoneSlot = this.hotbarSlotOf(class_1802.field_8793);
               int crystalSlot = this.hotbarSlotOf(class_1802.field_8301);
               if (pistonSlot >= 0 && redstoneSlot >= 0 && crystalSlot >= 0) {
                  PistonCrystalModule.Setup setup = this.findSetup(pistonSlot, redstoneSlot, crystalSlot);
                  if (setup == null) {
                     this.clearPending();
                  } else if (!this.breakCrystalsAround(setup.head())) {
                     if (this.pending == null || this.pending.dir() != setup.dir()) {
                        this.rotateHeld = 0;
                     }

                     this.pending = setup;
                     Homovore.rotationManager
                        .submit(new RotationRequest("PistonCrystal", 70, setup.dir().method_10144(), 0.0F, RotationRequest.Mode.MOTION, true, false));
                     if (Homovore.rotationManager.isCompleted("PistonCrystal")) {
                        this.rotateHeld++;
                     } else {
                        this.rotateHeld = 0;
                     }

                     if (this.rotateHeld >= 2) {
                        this.place(setup, pistonSlot, redstoneSlot, crystalSlot);
                     }
                  }
               } else {
                  this.clearPending();
                  this.lastDamage = 0.0F;
               }
            }
         }
      }
   }

   private void clearPending() {
      if (this.pending != null) {
         Homovore.rotationManager.cancel("PistonCrystal");
         this.pending = null;
      }

      this.rotateHeld = 0;
      this.lastDamage = 0.0F;
   }

   private void place(PistonCrystalModule.Setup setup, int pistonSlot, int redstoneSlot, int crystalSlot) {
      if (!setup.placeBase() || this.hotbarSlotOf(class_1802.field_8281) >= 0) {
         if (!setup.placeBase() || Homovore.placementManager.placeDirect(setup.base(), null, class_1802.field_8281)) {
            class_1792 piston = mc.field_1724.method_31548().method_5438(pistonSlot).method_7909();
            if (Homovore.placementManager.placeDirect(setup.piston(), null, piston)) {
               if (setup.placeRedstone()) {
                  Homovore.placementManager.placeDirect(setup.redstone(), null, class_1802.field_8793);
               }

               Homovore.placementManager.placeCrystal(setup.base(), true);
               int tick = mc.field_1724.field_6012;
               if (setup.placeBase()) {
                  this.renderMap.put(setup.base(), tick);
               }

               this.renderMap.put(setup.piston(), tick);
               if (setup.placeRedstone()) {
                  this.renderMap.put(setup.redstone(), tick);
               }

               this.renderMap.put(setup.crystal(), tick);
               Homovore.rotationManager.cancel("PistonCrystal");
               this.pending = null;
               this.rotateHeld = 0;
               this.active = setup;
               this.waitTicks = 0;
               this.delayTicks = 0;
            }
         }
      }
   }

   private void tickActive() {
      this.waitTicks++;
      class_2680 pistonState = mc.field_1687.method_8320(this.active.piston());
      boolean extended = pistonState.method_28498(class_2741.field_12552) && (Boolean)pistonState.method_11654(class_2741.field_12552);
      if (!extended) {
         extended = mc.field_1687.method_8320(this.active.crystal()).method_27852(class_2246.field_10379)
            || mc.field_1687.method_8320(this.active.crystal()).method_27852(class_2246.field_10008);
      }

      if (extended) {
         this.breakCrystalsAround(this.active.head());
         this.active = null;
      } else {
         if (this.waitTicks > 10) {
            this.active = null;
         }
      }
   }

   private boolean breakCrystalsAround(class_2338 head) {
      class_243 eye = mc.field_1724.method_33571();
      double rangeSq = 9.0;
      class_238 area = new class_238(head).method_1014(1.0);
      boolean found = false;

      for (class_1297 e : mc.field_1687.method_8335(null, area)) {
         if (e instanceof class_1511 crystal) {
            found = true;
            if (!(distSqToBox(eye, crystal.method_5829()) > rangeSq)) {
               this.breakCrystal(crystal);
            }
         }
      }

      return found;
   }

   private void breakCrystal(class_1511 crystal) {
      class_243 eyePos = mc.field_1724.method_5836(1.0F);
      class_243 hit = this.closestPointOnBox(eyePos, crystal.method_5829());
      float[] angles = MathUtil.calcAngle(eyePos, hit);
      if (this.canHitCrystal(crystal, angles[0], angles[1])) {
         Homovore.rotationManager.submit(new RotationRequest("PistonCrystal_break", 60, angles[0], angles[1], RotationRequest.Mode.SILENT));
         mc.field_1761.method_2918(mc.field_1724, crystal);
         if (this.swing.getValue()) {
            mc.field_1724.method_6104(class_1268.field_5808);
         }
      }
   }

   private boolean canHitCrystal(class_1511 crystal, float yaw, float pitch) {
      class_238 bb = crystal.method_5829();
      class_243 eye = mc.field_1724.method_5836(1.0F);
      if (bb.method_1006(eye)) {
         return true;
      }

      class_243 look = this.getLookVector(yaw, pitch);
      class_243 reachEnd = eye.method_1019(look.method_1021(3.0));
      return bb.method_992(eye, reachEnd).isPresent();
   }

   private PistonCrystalModule.Setup findSetup(int pistonSlot, int redstoneSlot, int crystalSlot) {
      class_1309 target = this.findTarget();
      if (target == null) {
         this.lastDamage = 0.0F;
         return null;
      }

      if (this.damageSync.getValue() && DamageSyncTracker.shouldHold(target)) {
         this.lastDamage = 0.0F;
         return null;
      }

      this.targetProfile = this.profileOf(target);
      this.targetDifficulty = mc.field_1687.method_8407();
      class_243 eye = mc.field_1724.method_33571();
      double range = 6.0;
      class_238 bb = target.method_5829();
      int minX = class_3532.method_15357(bb.field_1323);
      int maxX = class_3532.method_15357(bb.field_1320 - 1.0E-7);
      int minY = class_3532.method_15357(bb.field_1322);
      int maxY = class_3532.method_15357(bb.field_1325 - 1.0E-7);
      int minZ = class_3532.method_15357(bb.field_1321);
      int maxZ = class_3532.method_15357(bb.field_1324 - 1.0E-7);
      PistonCrystalModule.Setup best = null;
      class_2339 cursor = new class_2339();
      boolean lowPose = target.method_17682() <= 1.0F;

      for (int bx = minX; bx <= maxX; bx++) {
         for (int by = minY; by <= maxY; by++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
               cursor.method_10103(bx, by, bz);
               class_2338 cell = cursor.method_10062();
               class_2338[] heads = lowPose ? new class_2338[]{cell, cell.method_10084()} : new class_2338[]{cell.method_10084()};

               for (class_2338 head : heads) {
                  for (class_2350 dir : class_2353.field_11062) {
                     PistonCrystalModule.Setup side = this.sideSetup(target, head, dir, eye, range);
                     if (side != null && (best == null || side.damage() > best.damage())) {
                        best = side;
                     }

                     PistonCrystalModule.Setup top = this.topSetup(target, head, dir, eye, range);
                     if (top != null && (best == null || top.damage() > best.damage())) {
                        best = top;
                     }
                  }
               }
            }
         }
      }

      this.lastDamage = best != null ? best.damage() : 0.0F;
      return best;
   }

   private PistonCrystalModule.Setup sideSetup(class_1309 target, class_2338 head, class_2350 dir, class_243 eye, double range) {
      class_2338 crystalPos = head.method_10093(dir);
      class_2338 base = crystalPos.method_10074();
      class_2338 pistonPos = head.method_10079(dir, 2);
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      }

      if (!mc.field_1687.method_8320(crystalPos).method_26215()) {
         return null;
      }

      if (!mc.field_1687.method_8320(head).method_26215()) {
         return null;
      }

      class_243 explosionPos = new class_243(head.method_10263() + 0.5, head.method_10264(), head.method_10260() + 0.5);
      return this.buildSetup(target, head, dir, crystalPos, base, pistonPos, explosionPos, eye, range, placeBase);
   }

   private PistonCrystalModule.Setup topSetup(class_1309 target, class_2338 head, class_2350 dir, class_243 eye, double range) {
      class_2338 base = head.method_10093(dir);
      class_2338 crystalPos = base.method_10084();
      class_2338 pistonPos = crystalPos.method_10093(dir);
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      }

      if (!mc.field_1687.method_8320(crystalPos).method_26215()) {
         return null;
      }

      class_2338 dest = head.method_10084();
      class_243 explosionPos = mc.field_1687.method_8320(dest).method_26215()
         ? new class_243(dest.method_10263() + 0.5, dest.method_10264(), dest.method_10260() + 0.5)
         : new class_243(
            crystalPos.method_10263() + 0.5 - dir.method_10148() * 0.5, crystalPos.method_10264(), crystalPos.method_10260() + 0.5 - dir.method_10165() * 0.5
         );
      return this.buildSetup(target, head, dir, crystalPos, base, pistonPos, explosionPos, eye, range, placeBase);
   }

   private boolean needsBase(class_2338 base) {
      class_2680 s = mc.field_1687.method_8320(base);
      return !s.method_27852(class_2246.field_10540) && !s.method_27852(class_2246.field_9987);
   }

   private boolean canAutoBase(class_2338 base) {
      return this.autoBase.getValue() && this.hotbarSlotOf(class_1802.field_8281) >= 0 && PlaceUtil.canPlace(base);
   }

   private PistonCrystalModule.Setup buildSetup(
      class_1309 target,
      class_2338 head,
      class_2350 dir,
      class_2338 crystalPos,
      class_2338 base,
      class_2338 pistonPos,
      class_243 explosionPos,
      class_243 eye,
      double range,
      boolean placeBase
   ) {
      if (!PlaceUtil.canPlace(pistonPos)) {
         return null;
      }

      double rangeSq = range * range;
      if (eye.method_1025(class_243.method_24953(pistonPos)) > rangeSq) {
         return null;
      }

      if (eye.method_1025(class_243.method_24953(base)) > rangeSq) {
         return null;
      }

      class_238 crystalBox = new class_238(
         explosionPos.field_1352 - 1.0,
         explosionPos.field_1351,
         explosionPos.field_1350 - 1.0,
         explosionPos.field_1352 + 1.0,
         explosionPos.field_1351 + 2.0,
         explosionPos.field_1350 + 1.0
      );
      double breakRangeSq = 9.0;
      if (distSqToBox(eye, crystalBox) > breakRangeSq) {
         return null;
      }

      float damage = this.calcDamage(target, explosionPos, placeBase ? base : null);
      if (damage < this.minDamage.getValue()) {
         return null;
      }

      PistonCrystalModule.RedstoneSpot redstone = this.findRedstoneSpot(pistonPos, dir, explosionPos, eye, rangeSq);
      return redstone == null
         ? null
         : new PistonCrystalModule.Setup(dir, pistonPos, redstone.pos(), crystalPos, base, head, redstone.place(), placeBase, damage);
   }

   private PistonCrystalModule.RedstoneSpot findRedstoneSpot(class_2338 pistonPos, class_2350 dir, class_243 explosionPos, class_243 eye, double rangeSq) {
      class_2338 above = pistonPos.method_10084();
      class_2338 inDir = pistonPos.method_10093(dir);
      class_2338 cw = pistonPos.method_10093(dir.method_10170());
      class_2338 ccw = pistonPos.method_10093(dir.method_10160());
      class_2338 below = pistonPos.method_10074();
      if (mc.field_1687.method_8320(above).method_27852(class_2246.field_10002)) {
         return new PistonCrystalModule.RedstoneSpot(above, false);
      }

      if (mc.field_1687.method_8320(inDir).method_27852(class_2246.field_10002)) {
         return new PistonCrystalModule.RedstoneSpot(inDir, false);
      }

      if (mc.field_1687.method_8320(cw).method_27852(class_2246.field_10002)) {
         return new PistonCrystalModule.RedstoneSpot(cw, false);
      }

      if (mc.field_1687.method_8320(ccw).method_27852(class_2246.field_10002)) {
         return new PistonCrystalModule.RedstoneSpot(ccw, false);
      }

      if (mc.field_1687.method_8320(below).method_27852(class_2246.field_10002)) {
         return new PistonCrystalModule.RedstoneSpot(below, false);
      }

      class_2338 fallback = null;

      for (class_2338 pos : new class_2338[]{above, inDir, cw, ccw, below}) {
         if (PlaceUtil.canPlace(pos) && !(eye.method_1025(class_243.method_24953(pos)) > rangeSq)) {
            if (this.redstoneSafe(pos, explosionPos)) {
               return new PistonCrystalModule.RedstoneSpot(pos, true);
            }

            if (fallback == null) {
               fallback = pos;
            }
         }
      }

      return fallback == null ? null : new PistonCrystalModule.RedstoneSpot(fallback, true);
   }

   private boolean redstoneSafe(class_2338 pos, class_243 explosionPos) {
      class_243 to = class_243.method_24953(pos);
      if (explosionPos.method_1025(to) > 36.0) {
         return true;
      }

      class_243 diff = to.method_1020(explosionPos);
      int steps = (int)Math.ceil(diff.method_1033() / 0.25);
      long last = Long.MIN_VALUE;

      for (int i = 1; i < steps; i++) {
         double s = (double)i / steps;
         this.rayCursor
            .method_10103(
               class_3532.method_15357(explosionPos.field_1352 + diff.field_1352 * s),
               class_3532.method_15357(explosionPos.field_1351 + diff.field_1351 * s),
               class_3532.method_15357(explosionPos.field_1350 + diff.field_1350 * s)
            );
         long key = this.rayCursor.method_10063();
         if (key != last) {
            last = key;
            if (this.rayCursor.equals(pos)) {
               break;
            }

            if (mc.field_1687.method_8320(this.rayCursor).method_26204().method_9520() >= 600.0F) {
               return true;
            }
         }
      }

      return false;
   }

   private double calcExposure(class_243 source, class_238 box, class_2338 phantomBase) {
      double dx = box.method_17939();
      double dy = box.method_17940();
      double dz = box.method_17941();
      int steps = 2;
      int total = 0;
      int unblocked = 0;

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               class_243 point = new class_243(box.field_1323 + dx * xi / steps, box.field_1322 + dy * yi / steps, box.field_1321 + dz * zi / steps);
               if (!this.explosionBlocked(point, source, phantomBase)) {
                  unblocked++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unblocked / total;
   }

   private boolean explosionBlocked(class_243 from, class_243 to, class_2338 phantomBase) {
      class_243 diff = to.method_1020(from);
      int steps = (int)Math.ceil(diff.method_1033() / 0.25);
      long last = Long.MIN_VALUE;

      for (int i = 1; i < steps; i++) {
         double s = (double)i / steps;
         this.rayCursor
            .method_10103(
               class_3532.method_15357(from.field_1352 + diff.field_1352 * s),
               class_3532.method_15357(from.field_1351 + diff.field_1351 * s),
               class_3532.method_15357(from.field_1350 + diff.field_1350 * s)
            );
         long key = this.rayCursor.method_10063();
         if (key != last) {
            last = key;
            if (phantomBase != null && this.rayCursor.equals(phantomBase)) {
               return true;
            }

            if (mc.field_1687.method_8320(this.rayCursor).method_26204().method_9520() >= 600.0F) {
               return true;
            }
         }
      }

      return false;
   }

   private class_1309 findTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      class_1309 best = null;
      double bestSq = Double.MAX_VALUE;

      for (class_1657 p : mc.field_1687.method_18456()) {
         if (p != mc.field_1724 && !p.method_29504() && (targets == null || targets.isValidPlayerTarget(p)) && !this.isPhased(p)) {
            double dSq = mc.field_1724.method_5858(p);
            if (!(dSq > 100.0) && !(dSq >= bestSq)) {
               bestSq = dSq;
               best = p;
            }
         }
      }

      return best;
   }

   private boolean isPhased(class_1309 target) {
      class_238 bb = target.method_5829().method_1011(0.001);
      int minX = class_3532.method_15357(bb.field_1323);
      int maxX = class_3532.method_15357(bb.field_1320);
      int minY = class_3532.method_15357(bb.field_1322);
      int maxY = class_3532.method_15357(bb.field_1325);
      int minZ = class_3532.method_15357(bb.field_1321);
      int maxZ = class_3532.method_15357(bb.field_1324);
      class_2339 cursor = new class_2339();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cursor.method_10103(x, y, z);
               class_2680 state = mc.field_1687.method_8320(cursor);
               if (!state.method_26215()) {
                  class_265 shape = state.method_26220(mc.field_1687, cursor);
                  if (!shape.method_1110() && shape.method_1107().method_989(x, y, z).method_994(bb)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private float calcDamage(class_1309 target, class_243 explosionPos, class_2338 phantomBase) {
      double distSq = target.method_73189().method_1025(explosionPos);
      if (distSq > 144.0) {
         return 0.0F;
      }

      double exposure = this.calcExposure(explosionPos, target.method_5829(), phantomBase);
      if (exposure <= 0.0) {
         return 0.0F;
      }

      double impact = (1.0 - Math.sqrt(distSq) / 12.0) * exposure;
      if (impact <= 0.0) {
         return 0.0F;
      }

      float damage = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (this.targetDifficulty) {
         case field_5805:
            damage = Math.min(damage / 2.0F + 1.0F, damage);
            break;
         case field_5807:
            damage *= 1.5F;
      }

      PistonCrystalModule.ArmorProfile p = this.targetProfile;
      float i = 2.0F + p.toughness() / 4.0F;
      float j = class_3532.method_15363(p.armor() - damage / i, p.armor() * 0.2F, 20.0F);
      damage *= 1.0F - j / 25.0F;
      damage *= p.resistanceMul();
      damage = class_1280.method_5497(damage, p.protPoints());
      return Math.max(damage, 0.0F);
   }

   private PistonCrystalModule.ArmorProfile profileOf(class_1309 target) {
      float armor = (float)target.method_45325(class_5134.field_23724);
      float tough = (float)target.method_45325(class_5134.field_23725);
      float resistanceMul = 1.0F;
      class_1293 res = target.method_6112(class_1294.field_5907);
      if (res != null) {
         resistanceMul = 1.0F - 0.2F * (res.method_5578() + 1);
      }

      int protPoints = 0;
      if (!target.method_6118(class_1304.field_6169).method_7960()) {
         protPoints += 4;
      }

      if (!target.method_6118(class_1304.field_6174).method_7960()) {
         protPoints += 4;
      }

      if (!target.method_6118(class_1304.field_6172).method_7960()) {
         protPoints += 8;
      }

      if (!target.method_6118(class_1304.field_6166).method_7960()) {
         protPoints += 4;
      }

      return new PistonCrystalModule.ArmorProfile(armor, tough, resistanceMul, protPoints);
   }

   private class_243 closestPointOnBox(class_243 eye, class_238 box) {
      boolean minX = eye.field_1352 < box.field_1323;
      boolean maxX = eye.field_1352 > box.field_1320;
      boolean minZ = eye.field_1350 < box.field_1321;
      boolean maxZ = eye.field_1350 > box.field_1324;
      double x = class_3532.method_15350(eye.field_1352, box.field_1323, box.field_1320);
      double y = class_3532.method_15350(eye.field_1351, box.field_1322, box.field_1325);
      double z = class_3532.method_15350(eye.field_1350, box.field_1321, box.field_1324);
      if (minX) {
         x += 0.0625;
      } else if (maxX) {
         x -= 0.0625;
      }

      if (minZ) {
         z += 0.0625;
      } else if (maxZ) {
         z -= 0.0625;
      }

      return new class_243(x, y, z);
   }

   private static double distSqToBox(class_243 p, class_238 box) {
      double dx = p.field_1352 < box.field_1323 ? box.field_1323 - p.field_1352 : (p.field_1352 > box.field_1320 ? p.field_1352 - box.field_1320 : 0.0);
      double dy = p.field_1351 < box.field_1322 ? box.field_1322 - p.field_1351 : (p.field_1351 > box.field_1325 ? p.field_1351 - box.field_1325 : 0.0);
      double dz = p.field_1350 < box.field_1321 ? box.field_1321 - p.field_1350 : (p.field_1350 > box.field_1324 ? p.field_1350 - box.field_1324 : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private class_243 getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float k = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new class_243(g * h, k, f * h);
   }

   private int pistonSlot() {
      int s = this.hotbarSlotOf(class_1802.field_8249);
      return s >= 0 ? s : this.hotbarSlotOf(class_1802.field_8105);
   }

   private int hotbarSlotOf(class_1792 item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() && r.type() != ResultType.OFFHAND ? r.slot() : -1;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         int fadeTicks = (int)(this.fadeTime.getValue() * 20.0F);
         int now = mc.field_1724.field_6012;

         for (Entry<class_2338, Integer> entry : this.renderMap.entrySet()) {
            int age = now - entry.getValue();
            if (age <= fadeTicks) {
               double t = (double)age / fadeTicks;
               Color fc = this.fillColor.getValue();
               Color oc = this.outlineColor.getValue();
               RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
               RenderUtil.drawBox(event.getMatrix(), entry.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
            }
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), class_3532.method_15340(a, 0, 255));
   }

   @Override
   public String getDisplayInfo() {
      return this.lastDamage > 0.0F ? String.format("%.1f", this.lastDamage) : null;
   }

   private record ArmorProfile(float armor, float toughness, float resistanceMul, int protPoints) {
   }

   private record RedstoneSpot(class_2338 pos, boolean place) {
   }

   private record Setup(
      class_2350 dir,
      class_2338 piston,
      class_2338 redstone,
      class_2338 crystal,
      class_2338 base,
      class_2338 head,
      boolean placeRedstone,
      boolean placeBase,
      float damage
   ) {
   }
}
