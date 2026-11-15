package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.manager.PlacementManager;
import dev.piolunson.v2.manager.RotationManager;
import dev.piolunson.v2.render.BlockRender;
import dev.piolunson.v2.util.RotationUtil;
import dev.piolunson.v2.util.inventory.InventorySnapshot;
import dev.piolunson.v2.util.inventory.InventoryUtil;
import dev.piolunson.v2.util.inventory.Result;
import dev.piolunson.v2.util.inventory.ResultType;
import dev.piolunson.v2.util.network.NetworkActionContext;
import dev.piolunson.v2.util.network.NetworkThreadExecutor;
import dev.piolunson.v2.util.network.PacketLimiter;
import dev.piolunson.v2.util.world.ExplosionMath;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShape;

public final class PistonCrystalModule extends Module {
   private static final int TARGET_RANGE_SQ = 100;
   private static final int BREAK_SCAN_TICKS = 10;
   private static final int PUSH_GRACE_TICKS = 4;
   private static final int CONTRAPTION_PROTECT_TICKS = 60;
   private static final int ATTACK_RETRY_TICKS = 2;
   private static final double SWEEP_INFLATE = 1.5;
   private static final double BLOCK_REACH = 4.5;
   private static final double ENTITY_REACH = 3.0;
   private static final Direction[] HORIZONTAL = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
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
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private final Map<BlockPos, Integer> placedContraption = new HashMap<>();
   private final Map<Integer, Integer> attackedCrystals = new HashMap<>();
   private final ConcurrentLinkedQueue<PistonCrystalModule.ActionCompletion> completions = new ConcurrentLinkedQueue<>();
   private final AtomicReference<PistonCrystalModule.Protection> protection = new AtomicReference<>(PistonCrystalModule.Protection.EMPTY);
   private float lastDamage;
   private boolean actionPending;
   private int delayTicks;
   private long generation;
   private BlockPos activeHead;
   private BlockPos activeCrystal;
   private int activeExpireTick;
   private int activePlaceTick;
   private final Mutable rayCursor = new Mutable();

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
      if (mc.player != null && mc.world != null && !mc.player.isDead()) {
         int now = mc.player.age;
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
               Item piston = this.pistonItem();
               if (piston != null && crystalResult().found()) {
                  PistonCrystalModule.Setup setup = this.findSetup();
                  if (setup != null && !this.sweepHead(setup.head())) {
                     if (setup.placeBase()) {
                        this.submitBase(setup.base(), setup.baseFace());
                     } else if (!setup.placeRedstone() || this.hotbarItem(Items.REDSTONE_BLOCK)) {
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

   private boolean sweepHead(BlockPos head) {
      Vec3d eye = mc.player.getEyePos();
      boolean found = false;

      for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(head).expand(1.5), Entity::isAlive)) {
         found = true;
         if (this.doBreak.getValue() && !this.awaitingPush(crystal) && !(RotationUtil.distanceTo(eye, crystal.getBoundingBox()) > 3.0)) {
            this.attackCrystal(crystal, eye);
         }
      }

      return found;
   }

   private boolean awaitingPush(EndCrystalEntity crystal) {
      return this.activeCrystal != null && mc.player.age - this.activePlaceTick <= 4
         ? this.activeCrystal.equals(BlockPos.ofFloored(crystal.getEntityPos()))
         : false;
   }

   private void attackCrystal(EndCrystalEntity crystal, Vec3d eye) {
      int id = crystal.getId();
      int now = mc.player.age;
      Integer last = this.attackedCrystals.get(id);
      if (last == null || now - last >= 2) {
         this.attackedCrystals.put(id, now);
         InventorySnapshot inventory = InventoryUtil.snapshot();
         RotationManager.Angles angles = RotationUtil.anglesTo(eye, RotationUtil.closestAimPoint(eye, crystal.getBoundingBox()));
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

               context.swing(Hand.MAIN_HAND);
               return true;
            }
         });
      }
   }

   private void submitBase(BlockPos base, Direction face) {
      if (this.hotbarItem(Items.OBSIDIAN)) {
         long submittedGeneration = this.generation;
         this.actionPending = true;
         NetworkThreadExecutor.<Boolean>submit(context -> this.placeBlockNow(context, base, face, Items.OBSIDIAN))
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

   private void submitDirect(PistonCrystalModule.Setup setup, Item piston, InventorySnapshot inventory) {
      if (!Homovore2Client.rotations().silentReservedByOther(this)) {
         long submittedGeneration = this.generation;
         this.actionPending = true;
         this.activeHead = setup.head();
         this.activeCrystal = setup.crystal();
         this.activePlaceTick = mc.player.age;
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

   private PistonCrystalModule.NetworkResult directNow(NetworkActionContext context, InventorySnapshot inventory, PistonCrystalModule.Setup setup, Item piston) {
      List<BlockPos> placed = new ArrayList<>(3);
      Result crystal = crystalResult();
      if (!crystal.found()) {
         return new PistonCrystalModule.NetworkResult(false, placed);
      }

      int uses = setup.placeRedstone() ? 3 : 2;
      if (context.limits().remaining(PacketLimiter.Bucket.USE_ITEM) >= uses && context.swaps().silentRemaining(context) >= uses) {
         try (RotationManager.SilentRotation rotation = Homovore2Client.rotations()
               .beginSilentNow(
                  context, inventory, new RotationManager.Angles(setup.dir().getPositiveHorizontalDegrees(), 0.0F), inventory.horizontalCollision(), this
               )) {
            if (!rotation.applied()) {
               return new PistonCrystalModule.NetworkResult(false, placed);
            }

            if (!this.placeBlockNow(context, setup.piston(), setup.pistonFace(), piston)) {
               return new PistonCrystalModule.NetworkResult(false, placed);
            }

            placed.add(setup.piston());
            if (setup.placeRedstone()) {
               if (!this.placeBlockNow(context, setup.redstone(), setup.redstoneFace(), Items.REDSTONE_BLOCK)) {
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

   private boolean placeBlockNow(NetworkActionContext context, BlockPos pos, Direction support, Item item) {
      if (support == null) {
         return Homovore2Client.placements().placeNow(context, pos, null, item, new PlacementManager.Options(true, false, false, true));
      }

      BlockPos supportPos = pos.offset(support);
      Vec3d location = new Vec3d(
         pos.getX() + 0.5 + support.getOffsetX() * 0.5, pos.getY() + 0.5 + support.getOffsetY() * 0.5, pos.getZ() + 0.5 + support.getOffsetZ() * 0.5
      );
      BlockHitResult hit = new BlockHitResult(location, support.getOpposite(), supportPos, false);
      return Homovore2Client.placements().useItemOnNow(context, hit, item, true);
   }

   private boolean placeCrystalNow(NetworkActionContext context, BlockPos base, Result crystal) {
      if (!crystal.found()) {
         return false;
      }

      Vec3d location = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
      BlockHitResult hit = new BlockHitResult(location, Direction.UP, base, false);
      boolean[] sent = new boolean[]{false};
      return InventoryUtil.withSwapNow(context, crystal, action -> sent[0] = action.place(crystal.hand(), hit, true)) && sent[0];
   }

   private void applyCompletions() {
      PistonCrystalModule.ActionCompletion completion;
      while ((completion = this.completions.poll()) != null) {
         if (completion.generation() == this.generation) {
            this.actionPending = false;
            int tick = mc.player == null ? 0 : mc.player.age;
            long now = System.currentTimeMillis();

            for (BlockPos pos : completion.placed()) {
               this.renderMap.put(pos, now);
            }

            if (completion.kind() == PistonCrystalModule.ActionKind.DIRECT) {
               this.delayTicks = 0;

               for (BlockPos pos : completion.placed()) {
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
               for (BlockPos pos : completion.placed()) {
                  this.placedContraption.put(pos, tick);
               }
            }

            this.publishProtection();
         }
      }
   }

   private void publishProtection() {
      Set<BlockPos> blocks = Set.copyOf(this.placedContraption.keySet());
      Set<BlockPos> crystals = new HashSet<>();
      if (this.activeCrystal != null) {
         crystals.add(this.activeCrystal);
      }

      if (this.activeHead != null) {
         for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
               for (int z = -1; z <= 1; z++) {
                  crystals.add(this.activeHead.add(x, y, z));
               }
            }
         }
      }

      this.protection.set(new PistonCrystalModule.Protection(blocks, Set.copyOf(crystals)));
   }

   public boolean isOwnPistonBlock(BlockPos pos) {
      return pos != null && this.isEnabled() && this.protection.get().blocks().contains(pos);
   }

   public boolean isOwnPistonBlock(int x, int y, int z) {
      return this.isEnabled() && this.protection.get().blocks().contains(new BlockPos(x, y, z));
   }

   public boolean isProtectedCrystal(int entityId, double x, double y, double z) {
      return !this.isEnabled() ? false : this.protection.get().crystals().contains(new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)));
   }

   public boolean isProtectedCrystal(EndCrystalEntity crystal) {
      return crystal != null && this.isProtectedCrystal(crystal.getId(), crystal.getX(), crystal.getY(), crystal.getZ());
   }

   private static Result crystalResult() {
      return InventoryUtil.find((stack, type) -> stack.isOf(Items.END_CRYSTAL) && type == ResultType.HOTBAR);
   }

   private boolean hotbarItem(Item item) {
      return InventoryUtil.find(item, Set.of(ResultType.HOTBAR)).found();
   }

   private Item pistonItem() {
      if (this.hotbarItem(Items.PISTON)) {
         return Items.PISTON;
      } else {
         return this.hotbarItem(Items.STICKY_PISTON) ? Items.STICKY_PISTON : null;
      }
   }

   private PistonCrystalModule.Setup findSetup() {
      List<PlayerEntity> targets = this.findTargets();
      if (targets.isEmpty()) {
         this.lastDamage = 0.0F;
         return null;
      }

      Vec3d eye = mc.player.getEyePos();
      PistonCrystalModule.Setup best = null;

      for (PlayerEntity target : targets) {
         for (BlockPos head : this.headVectors(target)) {
            for (Direction dir : HORIZONTAL) {
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

   private Set<BlockPos> headVectors(PlayerEntity target) {
      Box box = target.getBoundingBox();
      boolean lowPose = target.getHeight() <= 1.0F;
      LinkedHashSet<BlockPos> heads = new LinkedHashSet<>();

      for (int x = minCell(box.minX); x <= maxCell(box.maxX); x++) {
         for (int y = minCell(box.minY); y <= maxCell(box.maxY); y++) {
            for (int z = minCell(box.minZ); z <= maxCell(box.maxZ); z++) {
               BlockPos cell = new BlockPos(x, y, z);
               if (lowPose) {
                  heads.add(cell);
               }

               heads.add(cell.up());
            }
         }
      }

      return heads;
   }

   private static int minCell(double value) {
      return MathHelper.floor(value + 1.0E-7);
   }

   private static int maxCell(double value) {
      return MathHelper.floor(value - 1.0E-7);
   }

   private PistonCrystalModule.Setup sideSetup(PlayerEntity target, BlockPos head, Direction dir, Vec3d eye) {
      BlockPos crystalPos = head.offset(dir);
      BlockPos base = crystalPos.down();
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      } else if (mc.world.getBlockState(crystalPos).isAir() && mc.world.getBlockState(head).isAir()) {
         Vec3d explosion = new Vec3d(head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
         return this.buildSetup(target, dir, crystalPos, base, head.offset(dir, 2), head, explosion, eye, placeBase);
      } else {
         return null;
      }
   }

   private PistonCrystalModule.Setup topSetup(PlayerEntity target, BlockPos head, Direction dir, Vec3d eye) {
      BlockPos base = head.offset(dir);
      BlockPos crystalPos = base.up();
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      }

      if (!mc.world.getBlockState(crystalPos).isAir()) {
         return null;
      }

      BlockPos destination = head.up();
      Vec3d explosion = mc.world.getBlockState(destination).isAir()
         ? new Vec3d(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5)
         : new Vec3d(crystalPos.getX() + 0.5 - dir.getOffsetX() * 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5 - dir.getOffsetZ() * 0.5);
      return this.buildSetup(target, dir, crystalPos, base, crystalPos.offset(dir), destination, explosion, eye, placeBase);
   }

   private PistonCrystalModule.Setup buildSetup(
      PlayerEntity target,
      Direction dir,
      BlockPos crystalPos,
      BlockPos base,
      BlockPos pistonPos,
      BlockPos destination,
      Vec3d explosion,
      Vec3d eye,
      boolean placeBase
   ) {
      if (!this.canPlace(pistonPos) || !this.pistonCanExtend(pistonPos, dir) || this.itemInCrystalSpot(crystalPos)) {
         return null;
      }

      if (!outOfBlockReach(eye, pistonPos) && !outOfBlockReach(eye, base)) {
         Box blastBox = new Box(explosion.x - 1.0, explosion.y, explosion.z - 1.0, explosion.x + 1.0, explosion.y + 2.0, explosion.z + 1.0);
         if (RotationUtil.distanceTo(eye, blastBox) > 3.0) {
            return null;
         }

         BlockPos phantomBase = placeBase ? base : null;
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

   private boolean needsBase(BlockPos base) {
      BlockState state = mc.world.getBlockState(base);
      return !state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK);
   }

   private boolean canAutoBase(BlockPos base) {
      return this.autoBase.getValue() && this.hotbarItem(Items.OBSIDIAN) && this.canPlace(base);
   }

   private boolean pistonCanExtend(BlockPos pistonPos, Direction placementLook) {
      BlockState state = mc.world.getBlockState(pistonPos.offset(placementLook.getOpposite()));
      return state.isAir() || state.isReplaceable();
   }

   private boolean canPlace(BlockPos pos) {
      return !mc.world.getBlockState(pos).isReplaceable()
         ? false
         : mc.world
            .getOtherEntities((Entity)null, new Box(pos), entity -> !entity.isRemoved() && !entity.isSpectator() && entity.intersectionChecked)
            .isEmpty();
   }

   private boolean crystalObscuresPlacement(BlockPos... positions) {
      for (BlockPos position : positions) {
         if (position != null && !mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(position), Entity::isAlive).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private boolean itemInCrystalSpot(BlockPos crystalPos) {
      Box box = new Box(crystalPos.getX(), crystalPos.getY(), crystalPos.getZ(), crystalPos.getX() + 1, crystalPos.getY() + 2, crystalPos.getZ() + 1);
      return !mc.world.getNonSpectatingEntities(ItemEntity.class, box).isEmpty();
   }

   private Direction supportFace(BlockPos pos, BlockPos... phantoms) {
      Direction best = null;
      double bestDistance = Double.MAX_VALUE;
      Vec3d eye = mc.player.getEyePos();

      for (Direction direction : Direction.values()) {
         BlockPos neighbor = pos.offset(direction);
         BlockState state = mc.world.getBlockState(neighbor);
         if (!state.isAir() && !state.isReplaceable() && state.getFluidState().isEmpty()) {
            double distance = eye.squaredDistanceTo(Vec3d.ofCenter(neighbor));
            if (distance < bestDistance) {
               best = direction;
               bestDistance = distance;
            }
         }
      }

      if (best != null) {
         return best;
      }

      for (Direction direction : Direction.values()) {
         BlockPos neighbor = pos.offset(direction);

         for (BlockPos phantom : phantoms) {
            if (phantom != null && phantom.equals(neighbor)) {
               return direction;
            }
         }
      }

      return null;
   }

   private static boolean outOfBlockReach(Vec3d eye, BlockPos pos) {
      return RotationUtil.distanceTo(eye, new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)) > 4.5;
   }

   private PistonCrystalModule.RedstoneSpot findRedstoneSpot(BlockPos pistonPos, Direction dir, Vec3d explosion, Vec3d eye, BlockPos phantomBase) {
      BlockPos[] candidates = new BlockPos[]{
         pistonPos.up(), pistonPos.offset(dir), pistonPos.offset(dir.rotateYClockwise()), pistonPos.offset(dir.rotateYCounterclockwise()), pistonPos.down()
      };

      for (BlockPos pos : candidates) {
         if (mc.world.getBlockState(pos).isOf(Blocks.REDSTONE_BLOCK)) {
            return new PistonCrystalModule.RedstoneSpot(pos, null, false);
         }
      }

      PistonCrystalModule.RedstoneSpot fallback = null;

      for (BlockPos pos : candidates) {
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

   private boolean redstoneSafe(BlockPos pos, Vec3d explosion) {
      Vec3d target = Vec3d.ofCenter(pos);
      if (explosion.squaredDistanceTo(target) > 36.0) {
         return true;
      }

      Vec3d difference = target.subtract(explosion);
      int steps = (int)Math.ceil(difference.length() / 0.25);
      long previous = Long.MIN_VALUE;

      for (int i = 1; i < steps; i++) {
         double scale = (double)i / steps;
         this.rayCursor
            .set(
               MathHelper.floor(explosion.x + difference.x * scale),
               MathHelper.floor(explosion.y + difference.y * scale),
               MathHelper.floor(explosion.z + difference.z * scale)
            );
         long packed = this.rayCursor.asLong();
         if (packed != previous) {
            previous = packed;
            if (this.rayCursor.equals(pos)) {
               break;
            }

            if (mc.world.getBlockState(this.rayCursor).getBlock().getBlastResistance() >= 600.0F) {
               return true;
            }
         }
      }

      return false;
   }

   private List<PlayerEntity> findTargets() {
      List<PlayerEntity> result = new ArrayList<>();

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player
            && !player.isDead()
            && !Homovore2Client.friends().isFriend(player)
            && !(mc.player.squaredDistanceTo(player) > 100.0)
            && (!(player.getHeight() > 1.0F) || !this.isPhased(player))) {
            result.add(player);
         }
      }

      result.sort((first, second) -> Double.compare(mc.player.squaredDistanceTo(first), mc.player.squaredDistanceTo(second)));
      return result;
   }

   private boolean isPhased(PlayerEntity target) {
      Box box = target.getBoundingBox().contract(0.001);
      Mutable cursor = new Mutable();

      for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
         for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY); y++) {
            for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
               cursor.set(x, y, z);
               BlockState state = mc.world.getBlockState(cursor);
               VoxelShape shape = state.getCollisionShape(mc.world, cursor);
               if (!state.isAir() && !shape.isEmpty() && shape.getBoundingBox().offset(x, y, z).intersects(box)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean safeForSelf(Vec3d explosion, BlockPos phantomBase) {
      return ExplosionMath.safeForSelf(
         this.calcDamage(mc.player, explosion, phantomBase),
         this.maxSelfDamage.getValue(),
         mc.player.getHealth() + mc.player.getAbsorptionAmount(),
         mc.player.getAbilities().invulnerable
      );
   }

   private float calcDamage(PlayerEntity target, Vec3d explosion, BlockPos phantomBase) {
      if (target.getAbilities().invulnerable) {
         return 0.0F;
      }

      double distance = target.squaredDistanceTo(explosion);
      double exposure = phantomBase == null
         ? ExplosionMath.exposure(mc.world, explosion, target)
         : ExplosionMath.exposure(mc.world, explosion, target, phantomBase);
      return (float)ExplosionMath.damage(
         distance,
         exposure,
         target.getArmor(),
         target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
         ExplosionMath.resistanceFactor(target),
         ExplosionMath.protectionPoints(target),
         mc.world.getDifficulty().getId()
      );
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (this.render.getValue() && !this.renderMap.isEmpty()) {
         long now = System.currentTimeMillis();

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
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
      long generation, PistonCrystalModule.ActionKind kind, boolean success, List<BlockPos> placed, BlockPos head, BlockPos crystal
   ) {
   }

   private enum ActionKind {
      DIRECT,
      BASE;
   }

   private record NetworkResult(boolean success, List<BlockPos> placed) {
   }

   private record Protection(Set<BlockPos> blocks, Set<BlockPos> crystals) {
      private static final PistonCrystalModule.Protection EMPTY = new PistonCrystalModule.Protection(Set.of(), Set.of());
   }

   private record RedstoneSpot(BlockPos pos, Direction face, boolean place) {
   }

   private record Setup(
      Direction dir,
      BlockPos piston,
      Direction pistonFace,
      BlockPos redstone,
      Direction redstoneFace,
      BlockPos crystal,
      BlockPos base,
      Direction baseFace,
      BlockPos head,
      boolean placeRedstone,
      boolean placeBase,
      float damage
   ) {
   }
}
