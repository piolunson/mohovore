package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.PlacementManager;
import dev.leonetic.v2.render.BlockRender;
import dev.leonetic.v2.util.inventory.InventorySnapshot;
import dev.leonetic.v2.util.inventory.InventoryUtil;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import dev.leonetic.v2.util.network.PacketLimiter;
import dev.leonetic.v2.util.world.ExplosionMath;
import dev.leonetic.v2.util.world.PlacementWorldSnapshot;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

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
   private final Map<BlockPos, Long> renderMap = new ConcurrentHashMap<>();

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
      if (mc.player != null && mc.world != null && NetworkThreadExecutor.isConnected()) {
         long now = System.currentTimeMillis();
         this.expireRenders(now);
         this.expirePops(now);
         this.expireCrystalClaims(now);
         this.forgetGonePearls();
         InventorySnapshot inventory = InventoryUtil.snapshot();
         if (inventory.available() && !inventory.deadOrDying()) {
            List<EnderPearlEntity> pearls = this.enemyPearls();
            if (!pearls.isEmpty()) {
               if (this.crystal.getValue()) {
                  this.popCrystals(pearls, now);
               }

               boolean useObsidian = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.HOTBAR_SCOPE).found();
               boolean useCrystal = this.crystal.getValue() && InventoryUtil.find(Items.END_CRYSTAL, InventoryUtil.HOTBAR_SCOPE).found();
               if (useObsidian || useCrystal) {
                  List<PearlBlockerModule.Interception> interceptions = new ArrayList<>();

                  for (EnderPearlEntity pearl : pearls) {
                     if (!this.handled.contains(pearl.getId())) {
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
                                 .thenComparingInt(interceptionx -> interceptionx.pearl().age)
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
      int pearlId = best.pearl().getId();
      if (this.handled.add(pearlId)) {
         BlockPos pos = best.pos();
         this.renderMap.put(pos, now);
         Homovore2Client.placements().place(pos, null, Items.OBSIDIAN, OPTIONS).thenAccept(sent -> {
            if (!Boolean.TRUE.equals(sent)) {
               this.handled.remove(pearlId);
               this.renderMap.remove(pos);
            }
         });
      }
   }

   private void answerWithCrystal(PearlBlockerModule.Interception best, long now) {
      int pearlId = best.pearl().getId();
      if (this.handled.add(pearlId)) {
         PearlBlockerModule.CrystalPlan plan = best.crystal();
         BlockPos cell = plan.cell();
         this.renderMap.put(cell, now);
         this.crystalClaims.put(pearlId, now + plan.arrivalTicks() * 50L + 400L);
         BlockHitResult hit = new BlockHitResult(crystalSource(cell), Direction.UP, cell.down(), false);
         Homovore2Client.placements().useItemOn(List.of(hit), Items.END_CRYSTAL, true).thenAccept(sent -> {
            if (sent == null || sent <= 0) {
               this.handled.remove(pearlId);
               this.crystalClaims.remove(pearlId);
               this.renderMap.remove(cell);
            }
         });
      }
   }

   private void popCrystals(List<EnderPearlEntity> pearls, long now) {
      Vec3d eye = mc.player.getEyePos();
      double breakReach = 3.0;
      List<Vec3d[]> paths = pearls.stream().map(pearl -> path(pearl, 2)).toList();

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof EndCrystalEntity && !entity.isRemoved()) {
            int id = entity.getId();
            if (!this.popped.containsKey(id)) {
               Box box = entity.getBoundingBox();
               if (!(distanceToBoxSqr(eye, box) > breakReach * breakReach)) {
                  Box window = upperHalf(box);
                  Vec3d source = new Vec3d(entity.getX(), box.minY, entity.getZ());

                  for (Vec3d[] path : paths) {
                     Vec3d hit = entryPoint(window, path[0], path[1]);
                     if (hit == null) {
                        hit = entryPoint(window, path[1], path[2]);
                     }

                     if (hit != null && inBlastRange(source, hit) && this.blastsAway(source, hit)) {
                        if (this.survivable(source)) {
                           this.popped.put(id, now);
                           this.renderMap.put(BlockPos.ofFloored(source).down(), now);
                           NetworkThreadExecutor.<Boolean>submit(context -> {
                              if (context.limits().remaining(PacketLimiter.Bucket.GLOBAL) < 2) {
                                 return false;
                              }

                              if (!context.attack(id)) {
                                 return false;
                              }

                              context.swing(Hand.MAIN_HAND);
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

   private List<EnderPearlEntity> enemyPearls() {
      List<EnderPearlEntity> pearls = new ArrayList<>();

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof EnderPearlEntity pearl
            && !pearl.isRemoved()
            && this.isEnemyPearl(pearl)
            && (!pearl.isTouchingWater() || !(pearl.getVelocity().length() < 0.015))) {
            pearls.add(pearl);
         }
      }

      return pearls;
   }

   private boolean isEnemyPearl(EnderPearlEntity pearl) {
      return pearl.getOwner() instanceof PlayerEntity owner && owner != mc.player && !Homovore2Client.friends().isFriend(owner);
   }

   private PearlBlockerModule.Interception findInterception(EnderPearlEntity pearl, boolean wantCrystal, boolean wantObsidian) {
      PlacementWorldSnapshot.Snapshot world = PlacementWorldSnapshot.snapshot();
      if (!world.available()) {
         return null;
      }

      Vec3d pos = pearl.getEntityPos();
      Vec3d velocity = pearl.getVelocity();
      double gravity = pearl.getFinalGravity();
      boolean inWater = pearl.isTouchingWater();
      BlockPos candidate = null;
      int candidateTick = 0;
      PearlBlockerModule.CrystalPlan crystalPlan = null;

      for (int tick = 0; tick < 60; tick++) {
         velocity = step(velocity, gravity, inWater);
         Vec3d next = pos.add(velocity);
         HitResult collision = this.findCollision(pearl, pos, next, tick == 0);
         Vec3d pathEnd = collision.getType() == Type.MISS ? next : collision.getPos();
         int arrivalTicks = tick + 1;
         if (wantCrystal && crystalPlan == null && arrivalTicks >= 2 && collision.getType() == Type.MISS) {
            BlockPos cell = this.findCrystalCell(world, pos, next);
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

         if (collision.getType() != Type.MISS) {
            int remainingTicks = arrivalTicks - candidateTick;
            if (candidate != null && remainingTicks >= 2) {
               return new PearlBlockerModule.Interception(pearl, candidate, candidateTick, remainingTicks, crystalPlan);
            }

            return crystalOnly(pearl, crystalPlan);
         }

         pos = next;
         inWater = mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
         if (pos.y < mc.world.getBottomY() - 5) {
            return crystalOnly(pearl, crystalPlan);
         }
      }

      return crystalOnly(pearl, crystalPlan);
   }

   private static PearlBlockerModule.Interception crystalOnly(EnderPearlEntity pearl, PearlBlockerModule.CrystalPlan crystalPlan) {
      return crystalPlan == null ? null : new PearlBlockerModule.Interception(pearl, null, 0, 0, crystalPlan);
   }

   private static Vec3d step(Vec3d velocity, double gravity, boolean inWater) {
      double drag = inWater ? 0.8 : 0.99;
      return new Vec3d(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);
   }

   private static Vec3d[] path(EnderPearlEntity pearl, int ticks) {
      Vec3d[] points = new Vec3d[ticks + 1];
      Vec3d pos = pearl.getEntityPos();
      Vec3d velocity = pearl.getVelocity();
      double gravity = pearl.getFinalGravity();
      boolean inWater = pearl.isTouchingWater();
      points[0] = pos;

      for (int tick = 1; tick <= ticks; tick++) {
         velocity = step(velocity, gravity, inWater);
         pos = pos.add(velocity);
         points[tick] = pos;
      }

      return points;
   }

   private HitResult findCollision(EnderPearlEntity pearl, Vec3d from, Vec3d to, boolean includeEntities) {
      BlockHitResult blockHit = mc.world.raycast(new RaycastContext(from, to, ShapeType.COLLIDER, FluidHandling.NONE, pearl));
      if (!includeEntities) {
         return blockHit;
      } else {
         Box box = pearl.getBoundingBox().offset(from.subtract(pearl.getEntityPos()));
         EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
            mc.world,
            pearl,
            from,
            to,
            box.stretch(to.subtract(from)).expand(1.0),
            entity -> entity != pearl.getOwner() && entity.isAlive() && !entity.isSpectator() && entity.canHit()
         );
         if (entityHit == null) {
            return blockHit;
         } else {
            return (HitResult)(blockHit.getType() != Type.MISS && !(from.squaredDistanceTo(entityHit.getPos()) < from.squaredDistanceTo(blockHit.getPos()))
               ? blockHit
               : entityHit);
         }
      }
   }

   private BlockPos findPlacementCell(PlacementWorldSnapshot.Snapshot world, Vec3d from, Vec3d to) {
      return (BlockPos)BlockView.raycast(from, to, this, (module, pos) -> {
         if (!this.fillable(world, pos)) {
            return null;
         } else {
            return !mc.world.getBlockState(pos.down()).getCollisionShape(mc.world, pos.down()).isEmpty() ? null : pos.toImmutable();
         }
      }, module -> null);
   }

   private boolean free(PlacementWorldSnapshot.Snapshot world, BlockPos pos) {
      return world.known(pos) && world.replaceable(pos) && !world.selfCollision(pos) && !world.entityCollision(pos);
   }

   private boolean fillable(PlacementWorldSnapshot.Snapshot world, BlockPos pos) {
      return !this.free(world, pos) ? false : world.withinPlaceReach(pos);
   }

   private BlockPos findCrystalCell(PlacementWorldSnapshot.Snapshot world, Vec3d from, Vec3d to) {
      return (BlockPos)BlockView.raycast(from, to, this, (module, pos) -> {
         BlockPos cell = pos.down();
         if (this.hasSupport(cell.down()) && this.canHostCrystal(world, cell)) {
            Vec3d source = crystalSource(cell);
            Vec3d hit = entryPoint(upperHalf(crystalBox(cell)), from, to);
            if (hit != null && inBlastRange(source, hit) && this.blastsAway(source, hit)) {
               return !this.survivable(source) ? null : cell.toImmutable();
            } else {
               return null;
            }
         } else {
            return null;
         }
      }, module -> null);
   }

   private boolean hasSupport(BlockPos base) {
      return mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN) || mc.world.getBlockState(base).isOf(Blocks.BEDROCK);
   }

   private boolean canHostCrystal(PlacementWorldSnapshot.Snapshot world, BlockPos cell) {
      if (this.free(world, cell) && mc.world.getBlockState(cell).isAir()) {
         if (!mc.world
            .getOtherEntities((Entity)null, new Box(cell.getX(), cell.getY(), cell.getZ(), cell.getX() + 1.0, cell.getY() + 2.0, cell.getZ() + 1.0))
            .isEmpty()) {
            return false;
         }

         Vec3d eye = mc.player.getEyePos();
         BlockPos support = cell.down();
         double placeReach = 4.5;
         if (distanceToBoxSqr(eye, new Box(support.getX(), support.getY(), support.getZ(), support.getX() + 1.0, support.getY() + 1.0, support.getZ() + 1.0))
            > placeReach * placeReach) {
            return false;
         }

         double breakReach = 3.0;
         return distanceToBoxSqr(eye, crystalBox(cell)) <= breakReach * breakReach;
      } else {
         return false;
      }
   }

   private static Box crystalBox(BlockPos cell) {
      return new Box(cell.getX() - 0.5, cell.getY(), cell.getZ() - 0.5, cell.getX() + 1.5, cell.getY() + 2.0, cell.getZ() + 1.5);
   }

   private static Vec3d crystalSource(BlockPos cell) {
      return new Vec3d(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
   }

   private static Box upperHalf(Box box) {
      return new Box(box.minX, (box.minY + box.maxY) * 0.5, box.minZ, box.maxX, box.maxY, box.maxZ);
   }

   private static Vec3d entryPoint(Box box, Vec3d from, Vec3d to) {
      return box.contains(from) ? from : (Vec3d)box.raycast(from, to).orElse(null);
   }

   private static boolean inBlastRange(Vec3d source, Vec3d point) {
      return source.squaredDistanceTo(point) <= 144.0;
   }

   private boolean blastsAway(Vec3d source, Vec3d pearl) {
      double pushX = pearl.x - source.x;
      double pushZ = pearl.z - source.z;
      if (pushX * pushX + pushZ * pushZ < 0.25) {
         return true;
      }

      Vec3d self = mc.player.getEntityPos();
      return pushX * (pearl.x - self.x) + pushZ * (pearl.z - self.z) >= 0.0;
   }

   private boolean survivable(Vec3d source) {
      double damage = ExplosionMath.selfDamage(mc.world, mc.player, source);
      return damage <= this.maxSelfDamage.getValue() && damage + 1.0 < mc.player.getHealth() + mc.player.getAbsorptionAmount();
   }

   private static double distanceToBoxSqr(Vec3d point, Box box) {
      double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
      double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
      double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
      return dx * dx + dy * dy + dz * dz;
   }

   private void forgetGonePearls() {
      this.handled.removeIf(this::gone);
      this.crystalClaims.keySet().removeIf(this::gone);
   }

   private boolean gone(int id) {
      Entity entity = mc.world.getEntityById(id);
      return entity == null || entity.isRemoved();
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
   public void onRender3D(Camera camera, float partialTick) {
      if (this.render.getValue() && !this.renderMap.isEmpty()) {
         long now = System.currentTimeMillis();

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
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

   private record CrystalPlan(BlockPos cell, int arrivalTicks) {
   }

   private record Interception(EnderPearlEntity pearl, BlockPos pos, int arrivalTicks, int remainingTicks, PearlBlockerModule.CrystalPlan crystal) {
   }
}
