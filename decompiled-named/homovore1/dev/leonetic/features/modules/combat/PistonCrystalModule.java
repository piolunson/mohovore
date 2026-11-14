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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Difficulty;

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
   private final Map<BlockPos, Integer> renderMap = new HashMap<>();
   private PistonCrystalModule.Setup pending;
   private PistonCrystalModule.Setup active;
   private int delayTicks;
   private int rotateHeld;
   private int waitTicks;
   private float lastDamage;
   private PistonCrystalModule.ArmorProfile targetProfile;
   private Difficulty targetDifficulty;
   private final Mutable rayCursor = new Mutable();

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
      if (!nullCheck() && !mc.player.isDead()) {
         if (!EatUtil.shouldDefer()) {
            int fadeTicks = (int)(this.fadeTime.getValue() * 20.0F);
            int now = mc.player.age;
            this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeTicks);
            if (this.active != null) {
               this.tickActive();
            } else if (this.delayTicks < this.delay.getValue()) {
               this.delayTicks++;
            } else {
               int pistonSlot = this.pistonSlot();
               int redstoneSlot = this.hotbarSlotOf(Items.REDSTONE_BLOCK);
               int crystalSlot = this.hotbarSlotOf(Items.END_CRYSTAL);
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
                        .submit(
                           new RotationRequest("PistonCrystal", 70, setup.dir().getPositiveHorizontalDegrees(), 0.0F, RotationRequest.Mode.MOTION, true, false)
                        );
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
      if (!setup.placeBase() || this.hotbarSlotOf(Items.OBSIDIAN) >= 0) {
         if (!setup.placeBase() || Homovore.placementManager.placeDirect(setup.base(), null, Items.OBSIDIAN)) {
            Item piston = mc.player.getInventory().getStack(pistonSlot).getItem();
            if (Homovore.placementManager.placeDirect(setup.piston(), null, piston)) {
               if (setup.placeRedstone()) {
                  Homovore.placementManager.placeDirect(setup.redstone(), null, Items.REDSTONE_BLOCK);
               }

               Homovore.placementManager.placeCrystal(setup.base(), true);
               int tick = mc.player.age;
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
      BlockState pistonState = mc.world.getBlockState(this.active.piston());
      boolean extended = pistonState.contains(Properties.EXTENDED) && (Boolean)pistonState.get(Properties.EXTENDED);
      if (!extended) {
         extended = mc.world.getBlockState(this.active.crystal()).isOf(Blocks.PISTON_HEAD)
            || mc.world.getBlockState(this.active.crystal()).isOf(Blocks.MOVING_PISTON);
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

   private boolean breakCrystalsAround(BlockPos head) {
      Vec3d eye = mc.player.getEyePos();
      double rangeSq = 9.0;
      Box area = new Box(head).expand(1.0);
      boolean found = false;

      for (Entity e : mc.world.getOtherEntities(null, area)) {
         if (e instanceof EndCrystalEntity crystal) {
            found = true;
            if (!(distSqToBox(eye, crystal.getBoundingBox()) > rangeSq)) {
               this.breakCrystal(crystal);
            }
         }
      }

      return found;
   }

   private void breakCrystal(EndCrystalEntity crystal) {
      Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
      Vec3d hit = this.closestPointOnBox(eyePos, crystal.getBoundingBox());
      float[] angles = MathUtil.calcAngle(eyePos, hit);
      if (this.canHitCrystal(crystal, angles[0], angles[1])) {
         Homovore.rotationManager.submit(new RotationRequest("PistonCrystal_break", 60, angles[0], angles[1], RotationRequest.Mode.SILENT));
         mc.interactionManager.attackEntity(mc.player, crystal);
         if (this.swing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
         }
      }
   }

   private boolean canHitCrystal(EndCrystalEntity crystal, float yaw, float pitch) {
      Box bb = crystal.getBoundingBox();
      Vec3d eye = mc.player.getCameraPosVec(1.0F);
      if (bb.contains(eye)) {
         return true;
      }

      Vec3d look = this.getLookVector(yaw, pitch);
      Vec3d reachEnd = eye.add(look.multiply(3.0));
      return bb.raycast(eye, reachEnd).isPresent();
   }

   private PistonCrystalModule.Setup findSetup(int pistonSlot, int redstoneSlot, int crystalSlot) {
      LivingEntity target = this.findTarget();
      if (target == null) {
         this.lastDamage = 0.0F;
         return null;
      }

      if (this.damageSync.getValue() && DamageSyncTracker.shouldHold(target)) {
         this.lastDamage = 0.0F;
         return null;
      }

      this.targetProfile = this.profileOf(target);
      this.targetDifficulty = mc.world.getDifficulty();
      Vec3d eye = mc.player.getEyePos();
      double range = 6.0;
      Box bb = target.getBoundingBox();
      int minX = MathHelper.floor(bb.minX);
      int maxX = MathHelper.floor(bb.maxX - 1.0E-7);
      int minY = MathHelper.floor(bb.minY);
      int maxY = MathHelper.floor(bb.maxY - 1.0E-7);
      int minZ = MathHelper.floor(bb.minZ);
      int maxZ = MathHelper.floor(bb.maxZ - 1.0E-7);
      PistonCrystalModule.Setup best = null;
      Mutable cursor = new Mutable();
      boolean lowPose = target.getHeight() <= 1.0F;

      for (int bx = minX; bx <= maxX; bx++) {
         for (int by = minY; by <= maxY; by++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
               cursor.set(bx, by, bz);
               BlockPos cell = cursor.toImmutable();
               BlockPos[] heads = lowPose ? new BlockPos[]{cell, cell.up()} : new BlockPos[]{cell.up()};

               for (BlockPos head : heads) {
                  for (Direction dir : Type.HORIZONTAL) {
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

   private PistonCrystalModule.Setup sideSetup(LivingEntity target, BlockPos head, Direction dir, Vec3d eye, double range) {
      BlockPos crystalPos = head.offset(dir);
      BlockPos base = crystalPos.down();
      BlockPos pistonPos = head.offset(dir, 2);
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      }

      if (!mc.world.getBlockState(crystalPos).isAir()) {
         return null;
      }

      if (!mc.world.getBlockState(head).isAir()) {
         return null;
      }

      Vec3d explosionPos = new Vec3d(head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
      return this.buildSetup(target, head, dir, crystalPos, base, pistonPos, explosionPos, eye, range, placeBase);
   }

   private PistonCrystalModule.Setup topSetup(LivingEntity target, BlockPos head, Direction dir, Vec3d eye, double range) {
      BlockPos base = head.offset(dir);
      BlockPos crystalPos = base.up();
      BlockPos pistonPos = crystalPos.offset(dir);
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      }

      if (!mc.world.getBlockState(crystalPos).isAir()) {
         return null;
      }

      BlockPos dest = head.up();
      Vec3d explosionPos = mc.world.getBlockState(dest).isAir()
         ? new Vec3d(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5)
         : new Vec3d(crystalPos.getX() + 0.5 - dir.getOffsetX() * 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5 - dir.getOffsetZ() * 0.5);
      return this.buildSetup(target, head, dir, crystalPos, base, pistonPos, explosionPos, eye, range, placeBase);
   }

   private boolean needsBase(BlockPos base) {
      BlockState s = mc.world.getBlockState(base);
      return !s.isOf(Blocks.OBSIDIAN) && !s.isOf(Blocks.BEDROCK);
   }

   private boolean canAutoBase(BlockPos base) {
      return this.autoBase.getValue() && this.hotbarSlotOf(Items.OBSIDIAN) >= 0 && PlaceUtil.canPlace(base);
   }

   private PistonCrystalModule.Setup buildSetup(
      LivingEntity target,
      BlockPos head,
      Direction dir,
      BlockPos crystalPos,
      BlockPos base,
      BlockPos pistonPos,
      Vec3d explosionPos,
      Vec3d eye,
      double range,
      boolean placeBase
   ) {
      if (!PlaceUtil.canPlace(pistonPos)) {
         return null;
      }

      double rangeSq = range * range;
      if (eye.squaredDistanceTo(Vec3d.ofCenter(pistonPos)) > rangeSq) {
         return null;
      }

      if (eye.squaredDistanceTo(Vec3d.ofCenter(base)) > rangeSq) {
         return null;
      }

      Box crystalBox = new Box(explosionPos.x - 1.0, explosionPos.y, explosionPos.z - 1.0, explosionPos.x + 1.0, explosionPos.y + 2.0, explosionPos.z + 1.0);
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

   private PistonCrystalModule.RedstoneSpot findRedstoneSpot(BlockPos pistonPos, Direction dir, Vec3d explosionPos, Vec3d eye, double rangeSq) {
      BlockPos above = pistonPos.up();
      BlockPos inDir = pistonPos.offset(dir);
      BlockPos cw = pistonPos.offset(dir.rotateYClockwise());
      BlockPos ccw = pistonPos.offset(dir.rotateYCounterclockwise());
      BlockPos below = pistonPos.down();
      if (mc.world.getBlockState(above).isOf(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(above, false);
      }

      if (mc.world.getBlockState(inDir).isOf(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(inDir, false);
      }

      if (mc.world.getBlockState(cw).isOf(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(cw, false);
      }

      if (mc.world.getBlockState(ccw).isOf(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(ccw, false);
      }

      if (mc.world.getBlockState(below).isOf(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(below, false);
      }

      BlockPos fallback = null;

      for (BlockPos pos : new BlockPos[]{above, inDir, cw, ccw, below}) {
         if (PlaceUtil.canPlace(pos) && !(eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > rangeSq)) {
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

   private boolean redstoneSafe(BlockPos pos, Vec3d explosionPos) {
      Vec3d to = Vec3d.ofCenter(pos);
      if (explosionPos.squaredDistanceTo(to) > 36.0) {
         return true;
      }

      Vec3d diff = to.subtract(explosionPos);
      int steps = (int)Math.ceil(diff.length() / 0.25);
      long last = Long.MIN_VALUE;

      for (int i = 1; i < steps; i++) {
         double s = (double)i / steps;
         this.rayCursor
            .set(MathHelper.floor(explosionPos.x + diff.x * s), MathHelper.floor(explosionPos.y + diff.y * s), MathHelper.floor(explosionPos.z + diff.z * s));
         long key = this.rayCursor.asLong();
         if (key != last) {
            last = key;
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

   private double calcExposure(Vec3d source, Box box, BlockPos phantomBase) {
      double dx = box.getLengthX();
      double dy = box.getLengthY();
      double dz = box.getLengthZ();
      int steps = 2;
      int total = 0;
      int unblocked = 0;

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               Vec3d point = new Vec3d(box.minX + dx * xi / steps, box.minY + dy * yi / steps, box.minZ + dz * zi / steps);
               if (!this.explosionBlocked(point, source, phantomBase)) {
                  unblocked++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unblocked / total;
   }

   private boolean explosionBlocked(Vec3d from, Vec3d to, BlockPos phantomBase) {
      Vec3d diff = to.subtract(from);
      int steps = (int)Math.ceil(diff.length() / 0.25);
      long last = Long.MIN_VALUE;

      for (int i = 1; i < steps; i++) {
         double s = (double)i / steps;
         this.rayCursor.set(MathHelper.floor(from.x + diff.x * s), MathHelper.floor(from.y + diff.y * s), MathHelper.floor(from.z + diff.z * s));
         long key = this.rayCursor.asLong();
         if (key != last) {
            last = key;
            if (phantomBase != null && this.rayCursor.equals(phantomBase)) {
               return true;
            }

            if (mc.world.getBlockState(this.rayCursor).getBlock().getBlastResistance() >= 600.0F) {
               return true;
            }
         }
      }

      return false;
   }

   private LivingEntity findTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      LivingEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (PlayerEntity p : mc.world.getPlayers()) {
         if (p != mc.player && !p.isDead() && (targets == null || targets.isValidPlayerTarget(p)) && !this.isPhased(p)) {
            double dSq = mc.player.squaredDistanceTo(p);
            if (!(dSq > 100.0) && !(dSq >= bestSq)) {
               bestSq = dSq;
               best = p;
            }
         }
      }

      return best;
   }

   private boolean isPhased(LivingEntity target) {
      Box bb = target.getBoundingBox().contract(0.001);
      int minX = MathHelper.floor(bb.minX);
      int maxX = MathHelper.floor(bb.maxX);
      int minY = MathHelper.floor(bb.minY);
      int maxY = MathHelper.floor(bb.maxY);
      int minZ = MathHelper.floor(bb.minZ);
      int maxZ = MathHelper.floor(bb.maxZ);
      Mutable cursor = new Mutable();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cursor.set(x, y, z);
               BlockState state = mc.world.getBlockState(cursor);
               if (!state.isAir()) {
                  VoxelShape shape = state.getCollisionShape(mc.world, cursor);
                  if (!shape.isEmpty() && shape.getBoundingBox().offset(x, y, z).intersects(bb)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private float calcDamage(LivingEntity target, Vec3d explosionPos, BlockPos phantomBase) {
      double distSq = target.getEntityPos().squaredDistanceTo(explosionPos);
      if (distSq > 144.0) {
         return 0.0F;
      }

      double exposure = this.calcExposure(explosionPos, target.getBoundingBox(), phantomBase);
      if (exposure <= 0.0) {
         return 0.0F;
      }

      double impact = (1.0 - Math.sqrt(distSq) / 12.0) * exposure;
      if (impact <= 0.0) {
         return 0.0F;
      }

      float damage = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (this.targetDifficulty) {
         case EASY:
            damage = Math.min(damage / 2.0F + 1.0F, damage);
            break;
         case HARD:
            damage *= 1.5F;
      }

      PistonCrystalModule.ArmorProfile p = this.targetProfile;
      float i = 2.0F + p.toughness() / 4.0F;
      float j = MathHelper.clamp(p.armor() - damage / i, p.armor() * 0.2F, 20.0F);
      damage *= 1.0F - j / 25.0F;
      damage *= p.resistanceMul();
      damage = DamageUtil.getInflictedDamage(damage, p.protPoints());
      return Math.max(damage, 0.0F);
   }

   private PistonCrystalModule.ArmorProfile profileOf(LivingEntity target) {
      float armor = (float)target.getAttributeValue(EntityAttributes.ARMOR);
      float tough = (float)target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
      float resistanceMul = 1.0F;
      StatusEffectInstance res = target.getStatusEffect(StatusEffects.RESISTANCE);
      if (res != null) {
         resistanceMul = 1.0F - 0.2F * (res.getAmplifier() + 1);
      }

      int protPoints = 0;
      if (!target.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
         protPoints += 4;
      }

      if (!target.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
         protPoints += 4;
      }

      if (!target.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) {
         protPoints += 8;
      }

      if (!target.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
         protPoints += 4;
      }

      return new PistonCrystalModule.ArmorProfile(armor, tough, resistanceMul, protPoints);
   }

   private Vec3d closestPointOnBox(Vec3d eye, Box box) {
      boolean minX = eye.x < box.minX;
      boolean maxX = eye.x > box.maxX;
      boolean minZ = eye.z < box.minZ;
      boolean maxZ = eye.z > box.maxZ;
      double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
      double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
      double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
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

      return new Vec3d(x, y, z);
   }

   private static double distSqToBox(Vec3d p, Box box) {
      double dx = p.x < box.minX ? box.minX - p.x : (p.x > box.maxX ? p.x - box.maxX : 0.0);
      double dy = p.y < box.minY ? box.minY - p.y : (p.y > box.maxY ? p.y - box.maxY : 0.0);
      double dz = p.z < box.minZ ? box.minZ - p.z : (p.z > box.maxZ ? p.z - box.maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private Vec3d getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float k = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3d(g * h, k, f * h);
   }

   private int pistonSlot() {
      int s = this.hotbarSlotOf(Items.PISTON);
      return s >= 0 ? s : this.hotbarSlotOf(Items.STICKY_PISTON);
   }

   private int hotbarSlotOf(Item item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() && r.type() != ResultType.OFFHAND ? r.slot() : -1;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         int fadeTicks = (int)(this.fadeTime.getValue() * 20.0F);
         int now = mc.player.age;

         for (Entry<BlockPos, Integer> entry : this.renderMap.entrySet()) {
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
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
   }

   @Override
   public String getDisplayInfo() {
      return this.lastDamage > 0.0F ? String.format("%.1f", this.lastDamage) : null;
   }

   private record ArmorProfile(float armor, float toughness, float resistanceMul, int protPoints) {
   }

   private record RedstoneSpot(BlockPos pos, boolean place) {
   }

   private record Setup(
      Direction dir, BlockPos piston, BlockPos redstone, BlockPos crystal, BlockPos base, BlockPos head, boolean placeRedstone, boolean placeBase, float damage
   ) {
   }
}
