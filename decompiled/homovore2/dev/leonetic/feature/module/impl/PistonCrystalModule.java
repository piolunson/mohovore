package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.manager.RotationManager;
import dev.leonetic.render.BlockRender;
import dev.leonetic.util.RotationUtil;
import dev.leonetic.util.inventory.InventorySnapshot;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.network.NetworkActionContext;
import dev.leonetic.util.network.NetworkThreadExecutor;
import dev.leonetic.util.network.PacketLimiter;
import dev.leonetic.util.world.ExplosionMath;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1542;
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
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_4184;
import net.minecraft.class_5134;
import net.minecraft.class_2338.class_2339;

public final class PistonCrystalModule extends Module {
   private static final int TARGET_RANGE_SQ = 100;
   private static final int BREAK_SCAN_TICKS = 10;
   private static final int PUSH_GRACE_TICKS = 4;
   private static final int CONTRAPTION_PROTECT_TICKS = 60;
   private static final int ATTACK_RETRY_TICKS = 2;
   private static final double SWEEP_INFLATE = 1.5;
   private static final double BLOCK_REACH = 4.5;
   private static final double ENTITY_REACH = 3.0;
   private static final class_2350[] HORIZONTAL = new class_2350[]{
      class_2350.field_11043, class_2350.field_11035, class_2350.field_11034, class_2350.field_11039
   };
   public final Setting<Double> minDamage = this.number("MinDamage", (double)10.0, (double)0.0, (double)36.0).page("General");
   public final Setting<Double> maxSelfDamage = this.number("MaxSelfDamage", (double)5.0, (double)0.0, (double)36.0)
      .describe("Setups whose blast would take more than this off you are discarded.")
      .page("General");
   public final Setting<Integer> delay = this.integer("Delay", 0, 0, 20).describe("Ticks between one finished contraption and the next.").page("General");
   public final Setting<Boolean> autoBase = this.bool("AutoBase", true).page("General");
   public final Setting<Boolean> doBreak = this.bool("Break", true)
      .describe("Attacks the crystal once the piston has pushed it into the target.")
      .page("General");
   public final Setting<Boolean> pauseWhileUsing = this.bool("PauseWhileUsing", true)
      .describe("Pauses while eating or otherwise using an item.")
      .page("General");
   public final Setting<Boolean> render = this.bool("Render", true).page("Render");
   public final Setting<Double> fadeTime = this.number("FadeTime", (double)0.2, (double)0.05, (double)2.0).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Color> fillColor = this.color("FillColor", 0, 62, 122, 148).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Color> outlineColor = this.color("OutlineColor", 0, 120, 255, 220).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)1.0, (double)0.1, (double)8.0).visibleWhen(this.render::getValue).page("Render");
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true).visibleWhen(this.render::getValue).page("Render");
   private final Map<class_2338, Long> renderMap = new HashMap<>();
   private final Map<class_2338, Integer> placedContraption = new HashMap<>();
   private final Map<Integer, Integer> attackedCrystals = new HashMap<>();
   private final ConcurrentLinkedQueue<PistonCrystalModule.ActionCompletion> completions = new ConcurrentLinkedQueue<>();
   private final AtomicReference<PistonCrystalModule.Protection> protection = new AtomicReference<>(PistonCrystalModule.Protection.EMPTY);
   private float lastDamage;
   private boolean actionPending;
   private int delayTicks;
   private long generation;
   private class_2338 activeHead;
   private class_2338 activeCrystal;
   private int activeExpireTick;
   private int activePlaceTick;
   private final class_2339 rayCursor = new class_2339();

   public PistonCrystalModule() {
      super("PistonCrystal", Category.COMBAT, "Pushes an end crystal into a trapped player with a piston.");
   }

   @Override
   public int getTickPriority() {
      return 70;
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.generation++;
      Homovore2Client.rotations().releaseSilent(this);
      this.resetState();
   }

   private void resetState() {
      this.lastDamage = 0.0F;
      this.actionPending = false;
      this.delayTicks = 0;
      this.activeHead = null;
      this.activeCrystal = null;
      this.activeExpireTick = 0;
      this.activePlaceTick = 0;
      this.completions.clear();
      this.renderMap.clear();
      this.placedContraption.clear();
      this.attackedCrystals.clear();
      this.publishProtection();
   }

   @Override
   public void onTick() {
      this.applyCompletions();
      if (mc.field_1724 != null && mc.field_1687 != null && !mc.field_1724.method_29504()) {
         int now = mc.field_1724.field_6012;
         long renderNow = System.currentTimeMillis();
         this.renderMap.entrySet().removeIf(entry -> BlockRender.expired(entry.getValue(), renderNow, this.fadeTime.getValue()));
         this.attackedCrystals.entrySet().removeIf(entry -> now - entry.getValue() > 20);
         boolean expired = this.placedContraption.entrySet().removeIf(entry -> now - entry.getValue() > 60);
         if (this.activeHead != null && now > this.activeExpireTick) {
            this.activeHead = null;
            this.activeCrystal = null;
            expired = true;
         }

         if (expired) {
            this.publishProtection();
         }

         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available()
            && inventory.menuId() == 0
            && !this.actionPending
            && (!this.pauseWhileUsing.getValue() || !inventory.usingItem())
            && (this.activeHead == null || !this.sweepHead(this.activeHead))) {
            if (this.delayTicks < this.delay.getValue()) {
               this.delayTicks++;
            } else {
               class_1792 piston = this.pistonItem();
               if (piston != null && crystalResult().found()) {
                  PistonCrystalModule.Setup setup = this.findSetup();
                  if (setup != null && !this.sweepHead(setup.head())) {
                     if (setup.placeBase()) {
                        this.submitBase(setup.base(), setup.baseFace());
                     } else if (!setup.placeRedstone() || this.hotbarItem(class_1802.field_8793)) {
                        this.submitDirect(setup, piston, inventory);
                     }
                  }
               } else {
                  this.lastDamage = 0.0F;
               }
            }
         }
      }
   }

   private boolean sweepHead(class_2338 head) {
      class_243 eye = mc.field_1724.method_33571();
      boolean found = false;

      for (class_1511 crystal : mc.field_1687.method_8390(class_1511.class, new class_238(head).method_1014(1.5), class_1297::method_5805)) {
         found = true;
         if (this.doBreak.getValue() && !this.awaitingPush(crystal) && !(RotationUtil.distanceTo(eye, crystal.method_5829()) > 3.0)) {
            this.attackCrystal(crystal, eye);
         }
      }

      return found;
   }

   private boolean awaitingPush(class_1511 crystal) {
      return this.activeCrystal != null && mc.field_1724.field_6012 - this.activePlaceTick <= 4
         ? this.activeCrystal.equals(class_2338.method_49638(crystal.method_73189()))
         : false;
   }

   private void attackCrystal(class_1511 crystal, class_243 eye) {
      int id = crystal.method_5628();
      int now = mc.field_1724.field_6012;
      Integer last = this.attackedCrystals.get(id);
      if (last == null || now - last >= 2) {
         this.attackedCrystals.put(id, now);
         InventorySnapshot inventory = InventoryUtil.snapshot();
         RotationManager.Angles angles = RotationUtil.anglesTo(eye, RotationUtil.closestAimPoint(eye, crystal.method_5829()));
         NetworkThreadExecutor.submit(context -> {
            try (RotationManager.SilentRotation rotation = Homovore2Client.rotations().beginSilentNow(context, inventory, angles)) {
               if (!rotation.applied()) {
                  return false;
               }

               if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
                  return false;
               }

               if (!context.attack(id)) {
                  return false;
               }

               context.swing(class_1268.field_5808);
               return true;
            }
         });
      }
   }

   private void submitBase(class_2338 base, class_2350 face) {
      if (this.hotbarItem(class_1802.field_8281)) {
         long submittedGeneration = this.generation;
         this.actionPending = true;
         NetworkThreadExecutor.<Boolean>submit(context -> this.placeBlockNow(context, base, face, class_1802.field_8281))
            .whenComplete(
               (success, failure) -> {
                  boolean placed = failure == null && Boolean.TRUE.equals(success);
                  this.completions
                     .add(
                        new PistonCrystalModule.ActionCompletion(
                           submittedGeneration, PistonCrystalModule.ActionKind.BASE, placed, placed ? List.of(base) : List.of(), null, null
                        )
                     );
               }
            );
      }
   }

   private void submitDirect(PistonCrystalModule.Setup setup, class_1792 piston, InventorySnapshot inventory) {
      if (!Homovore2Client.rotations().silentReservedByOther(this)) {
         long submittedGeneration = this.generation;
         this.actionPending = true;
         this.activeHead = setup.head();
         this.activeCrystal = setup.crystal();
         this.activePlaceTick = mc.field_1724.field_6012;
         this.activeExpireTick = this.activePlaceTick + 10;
         this.publishProtection();
         Homovore2Client.rotations().reserveSilent(this);
         NetworkThreadExecutor.<PistonCrystalModule.NetworkResult>submit(context -> this.directNow(context, inventory, setup, piston))
            .whenComplete(
               (result, failure) -> {
                  Homovore2Client.rotations().releaseSilent(this);
                  this.completions
                     .add(
                        new PistonCrystalModule.ActionCompletion(
                           submittedGeneration,
                           PistonCrystalModule.ActionKind.DIRECT,
                           failure == null && result != null && result.success(),
                           result == null ? List.of() : result.placed(),
                           setup.head(),
                           setup.crystal()
                        )
                     );
               }
            );
      }
   }

   private PistonCrystalModule.NetworkResult directNow(
      NetworkActionContext context, InventorySnapshot inventory, PistonCrystalModule.Setup setup, class_1792 piston
   ) {
      List<class_2338> placed = new ArrayList<>(3);
      Result crystal = crystalResult();
      if (!crystal.found()) {
         return new PistonCrystalModule.NetworkResult(false, placed);
      }

      int uses = setup.placeRedstone() ? 3 : 2;
      if (context.limits().remaining(PacketLimiter.Bucket.USE_ITEM) >= uses && context.swaps().silentRemaining(context) >= uses) {
         try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
               .beginSilentNow(context, inventory, new RotationManager.Angles(setup.dir().method_10144(), 0.0F), inventory.horizontalCollision(), this)) {
            if (!rotation.applied()) {
               return new PistonCrystalModule.NetworkResult(false, placed);
            }

            if (!this.placeBlockNow(context, setup.piston(), setup.pistonFace(), piston)) {
               return new PistonCrystalModule.NetworkResult(false, placed);
            }

            placed.add(setup.piston());
            if (setup.placeRedstone()) {
               if (!this.placeBlockNow(context, setup.redstone(), setup.redstoneFace(), class_1802.field_8793)) {
                  return new PistonCrystalModule.NetworkResult(false, placed);
               }

               placed.add(setup.redstone());
            }

            if (!this.placeCrystalNow(context, setup.base(), crystal)) {
               return new PistonCrystalModule.NetworkResult(false, placed);
            }

            placed.add(setup.crystal());
            return new PistonCrystalModule.NetworkResult(true, placed);
         }
      } else {
         return new PistonCrystalModule.NetworkResult(false, placed);
      }
   }

   private boolean placeBlockNow(NetworkActionContext context, class_2338 pos, class_2350 support, class_1792 item) {
      if (support == null) {
         return Homovore2Client.placements().placeNow(context, pos, null, item, new PlacementManager.Options(true, false, false, true));
      }

      class_2338 supportPos = pos.method_10093(support);
      class_243 location = new class_243(
         pos.method_10263() + 0.5 + support.method_10148() * 0.5,
         pos.method_10264() + 0.5 + support.method_10164() * 0.5,
         pos.method_10260() + 0.5 + support.method_10165() * 0.5
      );
      class_3965 hit = new class_3965(location, support.method_10153(), supportPos, false);
      return Homovore2Client.placements().useItemOnNow(context, hit, item, true);
   }

   private boolean placeCrystalNow(NetworkActionContext context, class_2338 base, Result crystal) {
      if (!crystal.found()) {
         return false;
      }

      class_243 location = new class_243(base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5);
      class_3965 hit = new class_3965(location, class_2350.field_11036, base, false);
      boolean[] sent = new boolean[]{false};
      return InventoryUtil.withSwapNow(context, crystal, action -> sent[0] = action.place(crystal.hand(), hit, true)) && sent[0];
   }

   private void applyCompletions() {
      PistonCrystalModule.ActionCompletion completion;
      while ((completion = this.completions.poll()) != null) {
         if (completion.generation() == this.generation) {
            this.actionPending = false;
            int tick = mc.field_1724 == null ? 0 : mc.field_1724.field_6012;
            long now = System.currentTimeMillis();

            for (class_2338 pos : completion.placed()) {
               this.renderMap.put(pos, now);
            }

            if (completion.kind() == PistonCrystalModule.ActionKind.DIRECT) {
               this.delayTicks = 0;

               for (class_2338 pos : completion.placed()) {
                  if (!pos.equals(completion.crystal())) {
                     this.placedContraption.put(pos, tick);
                  }
               }

               if (completion.success()) {
                  this.activeHead = completion.head();
                  this.activeCrystal = completion.crystal();
                  this.activePlaceTick = tick;
                  this.activeExpireTick = tick + 10;
               } else {
                  this.activeHead = null;
                  this.activeCrystal = null;
               }
            } else if (completion.success()) {
               for (class_2338 pos : completion.placed()) {
                  this.placedContraption.put(pos, tick);
               }
            }

            this.publishProtection();
         }
      }
   }

   private void publishProtection() {
      Set<class_2338> blocks = Set.copyOf(this.placedContraption.keySet());
      Set<class_2338> crystals = new HashSet<>();
      if (this.activeCrystal != null) {
         crystals.add(this.activeCrystal);
      }

      if (this.activeHead != null) {
         for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
               for (int z = -1; z <= 1; z++) {
                  crystals.add(this.activeHead.method_10069(x, y, z));
               }
            }
         }
      }

      this.protection.set(new PistonCrystalModule.Protection(blocks, Set.copyOf(crystals)));
   }

   public boolean isOwnPistonBlock(class_2338 pos) {
      return pos != null && this.isEnabled() && this.protection.get().blocks().contains(pos);
   }

   public boolean isOwnPistonBlock(int x, int y, int z) {
      return this.isEnabled() && this.protection.get().blocks().contains(new class_2338(x, y, z));
   }

   public boolean isProtectedCrystal(int entityId, double x, double y, double z) {
      return !this.isEnabled()
         ? false
         : this.protection.get().crystals().contains(new class_2338(class_3532.method_15357(x), class_3532.method_15357(y), class_3532.method_15357(z)));
   }

   public boolean isProtectedCrystal(class_1511 crystal) {
      return crystal != null && this.isProtectedCrystal(crystal.method_5628(), crystal.method_23317(), crystal.method_23318(), crystal.method_23321());
   }

   private static Result crystalResult() {
      return InventoryUtil.find((stack, type) -> stack.method_31574(class_1802.field_8301) && type == ResultType.HOTBAR);
   }

   private boolean hotbarItem(class_1792 item) {
      return InventoryUtil.find(item, Set.of(ResultType.HOTBAR)).found();
   }

   private class_1792 pistonItem() {
      if (this.hotbarItem(class_1802.field_8249)) {
         return class_1802.field_8249;
      } else {
         return this.hotbarItem(class_1802.field_8105) ? class_1802.field_8105 : null;
      }
   }

   private PistonCrystalModule.Setup findSetup() {
      List<class_1657> targets = this.findTargets();
      if (targets.isEmpty()) {
         this.lastDamage = 0.0F;
         return null;
      }

      class_243 eye = mc.field_1724.method_33571();
      PistonCrystalModule.Setup best = null;

      for (class_1657 target : targets) {
         for (class_2338 head : this.headVectors(target)) {
            for (class_2350 dir : HORIZONTAL) {
               PistonCrystalModule.Setup side = this.sideSetup(target, head, dir, eye);
               if (side != null && (best == null || side.damage() > best.damage())) {
                  best = side;
               }

               PistonCrystalModule.Setup top = this.topSetup(target, head, dir, eye);
               if (top != null && (best == null || top.damage() > best.damage())) {
                  best = top;
               }
            }
         }
      }

      this.lastDamage = best == null ? 0.0F : best.damage();
      return best;
   }

   private Set<class_2338> headVectors(class_1657 target) {
      class_238 box = target.method_5829();
      boolean lowPose = target.method_17682() <= 1.0F;
      LinkedHashSet<class_2338> heads = new LinkedHashSet<>();

      for (int x = minCell(box.field_1323); x <= maxCell(box.field_1320); x++) {
         for (int y = minCell(box.field_1322); y <= maxCell(box.field_1325); y++) {
            for (int z = minCell(box.field_1321); z <= maxCell(box.field_1324); z++) {
               class_2338 cell = new class_2338(x, y, z);
               if (lowPose) {
                  heads.add(cell);
               }

               heads.add(cell.method_10084());
            }
         }
      }

      return heads;
   }

   private static int minCell(double value) {
      return class_3532.method_15357(value + 1.0E-7);
   }

   private static int maxCell(double value) {
      return class_3532.method_15357(value - 1.0E-7);
   }

   private PistonCrystalModule.Setup sideSetup(class_1657 target, class_2338 head, class_2350 dir, class_243 eye) {
      class_2338 crystalPos = head.method_10093(dir);
      class_2338 base = crystalPos.method_10074();
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      } else if (mc.field_1687.method_8320(crystalPos).method_26215() && mc.field_1687.method_8320(head).method_26215()) {
         class_243 explosion = new class_243(head.method_10263() + 0.5, head.method_10264(), head.method_10260() + 0.5);
         return this.buildSetup(target, dir, crystalPos, base, head.method_10079(dir, 2), head, explosion, eye, placeBase);
      } else {
         return null;
      }
   }

   private PistonCrystalModule.Setup topSetup(class_1657 target, class_2338 head, class_2350 dir, class_243 eye) {
      class_2338 base = head.method_10093(dir);
      class_2338 crystalPos = base.method_10084();
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      }

      if (!mc.field_1687.method_8320(crystalPos).method_26215()) {
         return null;
      }

      class_2338 destination = head.method_10084();
      class_243 explosion = mc.field_1687.method_8320(destination).method_26215()
         ? new class_243(destination.method_10263() + 0.5, destination.method_10264(), destination.method_10260() + 0.5)
         : new class_243(
            crystalPos.method_10263() + 0.5 - dir.method_10148() * 0.5, crystalPos.method_10264(), crystalPos.method_10260() + 0.5 - dir.method_10165() * 0.5
         );
      return this.buildSetup(target, dir, crystalPos, base, crystalPos.method_10093(dir), destination, explosion, eye, placeBase);
   }

   private PistonCrystalModule.Setup buildSetup(
      class_1657 target,
      class_2350 dir,
      class_2338 crystalPos,
      class_2338 base,
      class_2338 pistonPos,
      class_2338 destination,
      class_243 explosion,
      class_243 eye,
      boolean placeBase
   ) {
      if (!this.canPlace(pistonPos) || !this.pistonCanExtend(pistonPos, dir) || this.itemInCrystalSpot(crystalPos)) {
         return null;
      }

      if (!outOfBlockReach(eye, pistonPos) && !outOfBlockReach(eye, base)) {
         class_238 blastBox = new class_238(
            explosion.field_1352 - 1.0,
            explosion.field_1351,
            explosion.field_1350 - 1.0,
            explosion.field_1352 + 1.0,
            explosion.field_1351 + 2.0,
            explosion.field_1350 + 1.0
         );
         if (RotationUtil.distanceTo(eye, blastBox) > 3.0) {
            return null;
         }

         class_2338 phantomBase = placeBase ? base : null;
         float damage = this.calcDamage(target, explosion, phantomBase);
         if (damage < this.minDamage.getValue()) {
            return null;
         }

         if (!this.safeForSelf(explosion, phantomBase)) {
            return null;
         }

         PistonCrystalModule.RedstoneSpot redstone = this.findRedstoneSpot(pistonPos, dir, explosion, eye, phantomBase);
         return redstone != null && !this.crystalObscuresPlacement(base, pistonPos, crystalPos, redstone.pos())
            ? new PistonCrystalModule.Setup(
               dir,
               pistonPos,
               this.supportFace(pistonPos, phantomBase),
               redstone.pos(),
               redstone.face(),
               crystalPos,
               base,
               placeBase ? this.supportFace(base) : null,
               destination,
               redstone.place(),
               placeBase,
               damage
            )
            : null;
      } else {
         return null;
      }
   }

   private boolean needsBase(class_2338 base) {
      class_2680 state = mc.field_1687.method_8320(base);
      return !state.method_27852(class_2246.field_10540) && !state.method_27852(class_2246.field_9987);
   }

   private boolean canAutoBase(class_2338 base) {
      return this.autoBase.getValue() && this.hotbarItem(class_1802.field_8281) && this.canPlace(base);
   }

   private boolean pistonCanExtend(class_2338 pistonPos, class_2350 placementLook) {
      class_2680 state = mc.field_1687.method_8320(pistonPos.method_10093(placementLook.method_10153()));
      return state.method_26215() || state.method_45474();
   }

   private boolean canPlace(class_2338 pos) {
      return !mc.field_1687.method_8320(pos).method_45474()
         ? false
         : mc.field_1687
            .method_8333((class_1297)null, new class_238(pos), entity -> !entity.method_31481() && !entity.method_7325() && entity.field_23807)
            .isEmpty();
   }

   private boolean crystalObscuresPlacement(class_2338... positions) {
      for (class_2338 position : positions) {
         if (position != null && !mc.field_1687.method_8390(class_1511.class, new class_238(position), class_1297::method_5805).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private boolean itemInCrystalSpot(class_2338 crystalPos) {
      class_238 box = new class_238(
         crystalPos.method_10263(),
         crystalPos.method_10264(),
         crystalPos.method_10260(),
         crystalPos.method_10263() + 1,
         crystalPos.method_10264() + 2,
         crystalPos.method_10260() + 1
      );
      return !mc.field_1687.method_18467(class_1542.class, box).isEmpty();
   }

   private class_2350 supportFace(class_2338 pos, class_2338... phantoms) {
      class_2350 best = null;
      double bestDistance = Double.MAX_VALUE;
      class_243 eye = mc.field_1724.method_33571();

      for (class_2350 direction : class_2350.values()) {
         class_2338 neighbor = pos.method_10093(direction);
         class_2680 state = mc.field_1687.method_8320(neighbor);
         if (!state.method_26215() && !state.method_45474() && state.method_26227().method_15769()) {
            double distance = eye.method_1025(class_243.method_24953(neighbor));
            if (distance < bestDistance) {
               best = direction;
               bestDistance = distance;
            }
         }
      }

      if (best != null) {
         return best;
      }

      for (class_2350 direction : class_2350.values()) {
         class_2338 neighbor = pos.method_10093(direction);

         for (class_2338 phantom : phantoms) {
            if (phantom != null && phantom.equals(neighbor)) {
               return direction;
            }
         }
      }

      return null;
   }

   private static boolean outOfBlockReach(class_243 eye, class_2338 pos) {
      return RotationUtil.distanceTo(
            eye,
            new class_238(
               pos.method_10263(), pos.method_10264(), pos.method_10260(), pos.method_10263() + 1.0, pos.method_10264() + 1.0, pos.method_10260() + 1.0
            )
         )
         > 4.5;
   }

   private PistonCrystalModule.RedstoneSpot findRedstoneSpot(class_2338 pistonPos, class_2350 dir, class_243 explosion, class_243 eye, class_2338 phantomBase) {
      class_2338[] candidates = new class_2338[]{
         pistonPos.method_10084(),
         pistonPos.method_10093(dir),
         pistonPos.method_10093(dir.method_10170()),
         pistonPos.method_10093(dir.method_10160()),
         pistonPos.method_10074()
      };

      for (class_2338 pos : candidates) {
         if (mc.field_1687.method_8320(pos).method_27852(class_2246.field_10002)) {
            return new PistonCrystalModule.RedstoneSpot(pos, null, false);
         }
      }

      PistonCrystalModule.RedstoneSpot fallback = null;

      for (class_2338 pos : candidates) {
         if (this.canPlace(pos) && !outOfBlockReach(eye, pos)) {
            PistonCrystalModule.RedstoneSpot candidate = new PistonCrystalModule.RedstoneSpot(pos, this.supportFace(pos, pistonPos, phantomBase), true);
            if (this.redstoneSafe(pos, explosion)) {
               return candidate;
            }

            if (fallback == null) {
               fallback = candidate;
            }
         }
      }

      return fallback;
   }

   private boolean redstoneSafe(class_2338 pos, class_243 explosion) {
      class_243 target = class_243.method_24953(pos);
      if (explosion.method_1025(target) > 36.0) {
         return true;
      }

      class_243 difference = target.method_1020(explosion);
      int steps = (int)Math.ceil(difference.method_1033() / 0.25);
      long previous = Long.MIN_VALUE;

      for (int i = 1; i < steps; i++) {
         double scale = (double)i / steps;
         this.rayCursor
            .method_10103(
               class_3532.method_15357(explosion.field_1352 + difference.field_1352 * scale),
               class_3532.method_15357(explosion.field_1351 + difference.field_1351 * scale),
               class_3532.method_15357(explosion.field_1350 + difference.field_1350 * scale)
            );
         long packed = this.rayCursor.method_10063();
         if (packed != previous) {
            previous = packed;
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

   private List<class_1657> findTargets() {
      List<class_1657> result = new ArrayList<>();

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player != mc.field_1724
            && !player.method_29504()
            && !Homovore2Client.friends().isFriend(player)
            && !(mc.field_1724.method_5858(player) > 100.0)
            && (!(player.method_17682() > 1.0F) || !this.isPhased(player))) {
            result.add(player);
         }
      }

      result.sort((first, second) -> Double.compare(mc.field_1724.method_5858(first), mc.field_1724.method_5858(second)));
      return result;
   }

   private boolean isPhased(class_1657 target) {
      class_238 box = target.method_5829().method_1011(0.001);
      class_2339 cursor = new class_2339();

      for (int x = class_3532.method_15357(box.field_1323); x <= class_3532.method_15357(box.field_1320); x++) {
         for (int y = class_3532.method_15357(box.field_1322); y <= class_3532.method_15357(box.field_1325); y++) {
            for (int z = class_3532.method_15357(box.field_1321); z <= class_3532.method_15357(box.field_1324); z++) {
               cursor.method_10103(x, y, z);
               class_2680 state = mc.field_1687.method_8320(cursor);
               class_265 shape = state.method_26220(mc.field_1687, cursor);
               if (!state.method_26215() && !shape.method_1110() && shape.method_1107().method_989(x, y, z).method_994(box)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean safeForSelf(class_243 explosion, class_2338 phantomBase) {
      return ExplosionMath.safeForSelf(
         this.calcDamage(mc.field_1724, explosion, phantomBase),
         this.maxSelfDamage.getValue(),
         mc.field_1724.method_6032() + mc.field_1724.method_6067(),
         mc.field_1724.method_31549().field_7480
      );
   }

   private float calcDamage(class_1657 target, class_243 explosion, class_2338 phantomBase) {
      if (target.method_31549().field_7480) {
         return 0.0F;
      }

      double distance = target.method_5707(explosion);
      double exposure = phantomBase == null
         ? ExplosionMath.exposure(mc.field_1687, explosion, target)
         : ExplosionMath.exposure(mc.field_1687, explosion, target, phantomBase);
      return (float)ExplosionMath.damage(
         distance,
         exposure,
         target.method_6096(),
         target.method_45325(class_5134.field_23725),
         ExplosionMath.resistanceFactor(target),
         ExplosionMath.protectionPoints(target),
         mc.field_1687.method_8407().method_5461()
      );
   }

   @Override
   public void onRender3D(class_4184 camera, float partialTick) {
      if (this.render.getValue() && !this.renderMap.isEmpty()) {
         long now = System.currentTimeMillis();

         for (Entry<class_2338, Long> entry : this.renderMap.entrySet()) {
            float alpha = BlockRender.alpha(entry.getValue(), now, this.fadeTime.getValue());
            if (!(alpha <= 0.0F)) {
               BlockRender.cuboid(
                  entry.getKey(),
                  this.outlineColor.getValue(),
                  this.fillColor.getValue(),
                  this.lineWidth.getValue().floatValue(),
                  alpha,
                  this.throughWalls.getValue()
               );
            }
         }
      }
   }

   public float lastDamage() {
      return this.lastDamage;
   }

   private record ActionCompletion(
      long generation, PistonCrystalModule.ActionKind kind, boolean success, List<class_2338> placed, class_2338 head, class_2338 crystal
   ) {
   }

   private enum ActionKind {
      DIRECT,
      BASE;
   }

   private record NetworkResult(boolean success, List<class_2338> placed) {
   }

   private record Protection(Set<class_2338> blocks, Set<class_2338> crystals) {
      private static final PistonCrystalModule.Protection EMPTY = new PistonCrystalModule.Protection(Set.of(), Set.of());
   }

   private record RedstoneSpot(class_2338 pos, class_2350 face, boolean place) {
   }

   private record Setup(
      class_2350 dir,
      class_2338 piston,
      class_2350 pistonFace,
      class_2338 redstone,
      class_2350 redstoneFace,
      class_2338 crystal,
      class_2338 base,
      class_2350 baseFace,
      class_2338 head,
      boolean placeRedstone,
      boolean placeBase,
      float damage
   ) {
   }
}
