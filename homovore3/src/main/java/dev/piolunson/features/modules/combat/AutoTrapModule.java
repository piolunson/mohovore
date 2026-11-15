package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.TickEvent;
import dev.piolunson.event.impl.network.PacketEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.TargetsModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.PlacementManager;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

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
   private final Set<BlockPos> wantedPoses = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> ownedQueued = new HashSet<>();
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private LivingEntity target;
   private int cachedSlot = -1;
   private Item cachedItem = null;
   private boolean buildInterceptNext = true;
   private long lastPlaceTime = 0L;
   private BlockPos lockedCenter;
   private Axis lockedPrimary;
   private Axis lockedSecondary;
   private Axis lockedTertiary;
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
      if (event.getPacket() instanceof PlaySoundS2CPacket pkt) {
         if (pkt.getSound().value() == SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH) {
            double sx = pkt.getX();
            double sy = pkt.getY();
            double sz = pkt.getZ();
            mc.execute(() -> {
               if (!nullCheck()) {
                  for (PlayerEntity p : mc.world.getPlayers()) {
                     double dx = p.getX() - sx;
                     double dy = p.getY() - sy;
                     double dz = p.getZ() - sz;
                     if (dx * dx + dy * dy + dz * dz < 9.0) {
                        this.boostingTicks.put(p.getUuid(), 40);
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
      if (!nullCheck() && mc.currentScreen == null) {
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
            this.cachedItem = mc.player.getInventory().getStack(slot).getItem();
            LivingEntity sel = this.findTarget();
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

            if (this.lockedTargetUUID != null && !this.lockedTargetUUID.equals(this.target.getUuid())) {
               this.resetInterceptLock();
            }

            if (this.lockedTargetUUID != null && !this.target.isGliding()) {
               this.resetInterceptLock();
            }

            if (!this.pauseEat.getValue() || !mc.player.isUsingItem()) {
               if (!EatUtil.shouldDefer()) {
                  if (this.target.isGliding() && !this.buildInterceptNext && System.currentTimeMillis() - this.lastPlaceTime > 500L) {
                     this.resetInterceptLock();
                  }

                  Vec3d predicted = this.predictPosition(this.target);
                  Set<BlockPos> placePoses;
                  if (this.target.isGliding()) {
                     placePoses = this.buildInterceptNext ? this.elytraInterceptWedge(predicted, this.target) : this.elytraCloseShell(this.target);
                  } else if (isCubeHitbox(this.target)) {
                     placePoses = this.cubeTrap(predicted, this.target);
                  } else {
                     placePoses = this.standingTrap(predicted, this.target);
                  }

                  this.wantedPoses.clear();
                  this.wantedPoses.addAll(placePoses);
                  List<BlockPos> reachable = new ArrayList<>();
                  Vec3d eye = mc.player.getEyePos();

                  for (BlockPos pos : placePoses) {
                     if (eye.distanceTo(Vec3d.ofCenter(pos)) <= 5.1) {
                        reachable.add(pos.toImmutable());
                     }
                  }

                  if (reachable.isEmpty()) {
                     this.pruneOwned();
                  } else {
                     Vec3d sortPoint = new Vec3d(predicted.x, this.target.getY(), predicted.z);
                     reachable.sort(Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(sortPoint)));
                     if (this.faceFirst.getValue()) {
                        Direction lookDir = this.target.getHorizontalFacing();
                        BlockPos anchor = this.target.isGliding() ? BlockPos.ofFloored(predicted) : this.target.getBlockPos();
                        BlockPos leadBase = isCubeHitbox(this.target) ? anchor : anchor.up();
                        BlockPos prio = leadBase.offset(lookDir, this.faceLeadBlocks.getValue());
                        if (reachable.remove(prio)) {
                           reachable.add(0, prio);
                        } else if (this.wantedPoses.contains(prio) && eye.distanceTo(Vec3d.ofCenter(prio)) <= 5.1) {
                           reachable.add(0, prio);
                        }
                     }

                     if (this.target.isGliding() && eye.distanceTo(predicted) > 3.5) {
                        if (this.buildInterceptNext) {
                           this.resetInterceptLock();
                        }
                     } else {
                        this.pruneOwned();
                        long now = System.currentTimeMillis();
                        boolean placedAny = false;

                        for (BlockPos pos : reachable) {
                           BlockState state = mc.world.getBlockState(pos);
                           if (state.isReplaceable() && PlaceUtil.canPlace(pos) && Homovore.placementManager.enqueue(pos, this.cachedItem)) {
                              this.ownedQueued.add(pos);
                              this.renderMap.put(pos, now);
                              placedAny = true;
                           }
                        }

                        if (placedAny) {
                           this.lastPlaceTime = now;
                        }

                        if (this.target.isGliding() && placedAny && this.buildInterceptNext) {
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

   private static boolean isCubeHitbox(LivingEntity e) {
      return e.isGliding() || e.isCrawling() || e.isSwimming();
   }

   private Set<BlockPos> standingTrap(Vec3d predicted, LivingEntity entity) {
      LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();
      double half = entity.getWidth() * 0.5;
      int x0 = PlaceUtil.minCell(predicted.x - half);
      int x1 = PlaceUtil.maxCell(predicted.x + half);
      int z0 = PlaceUtil.minCell(predicted.z - half);
      int z1 = PlaceUtil.maxCell(predicted.z + half);
      int feetY = entity.getBlockPos().getY();
      int headY = feetY + 1;
      int capY = feetY + 2;

      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            poses.add(new BlockPos(x, capY, z));

            for (Direction d : Type.HORIZONTAL) {
               int nx = x + d.getOffsetX();
               int nz = z + d.getOffsetZ();
               if (nx < x0 || nx > x1 || nz < z0 || nz > z1) {
                  poses.add(new BlockPos(nx, headY, nz));
               }
            }
         }
      }

      if (!entity.isOnGround()) {
         for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
               poses.add(new BlockPos(x, feetY - 1, z));
            }
         }
      }

      return poses;
   }

   private Set<BlockPos> cubeTrap(Vec3d predicted, LivingEntity entity) {
      LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();
      double half = entity.getWidth() * 0.5;
      double height = entity.getHeight();
      int x0 = PlaceUtil.minCell(predicted.x - half);
      int x1 = PlaceUtil.maxCell(predicted.x + half);
      int z0 = PlaceUtil.minCell(predicted.z - half);
      int z1 = PlaceUtil.maxCell(predicted.z + half);
      int y0 = PlaceUtil.minCell(entity.getY());
      int y1 = PlaceUtil.maxCell(entity.getY() + height);
      BlockPos feetBlock = entity.getBlockPos();
      Set<BlockPos> crystalSpots = null;
      if (this.skipCrystalSpots.getValue()) {
         crystalSpots = new HashSet<>(4);

         for (Direction d : Type.HORIZONTAL) {
            crystalSpots.add(feetBlock.offset(d));
         }
      }

      for (int x = x0 - 1; x <= x1 + 1; x++) {
         for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int y = y0 - 1; y <= y1 + 1; y++) {
               int outsideX = x >= x0 && x <= x1 ? 0 : 1;
               int outsideZ = z >= z0 && z <= z1 ? 0 : 1;
               int outsideY = y >= y0 && y <= y1 ? 0 : 1;
               if (outsideX + outsideZ + outsideY == 1) {
                  BlockPos pos = new BlockPos(x, y, z);
                  if (crystalSpots == null || !crystalSpots.contains(pos)) {
                     poses.add(pos);
                  }
               }
            }
         }
      }

      return poses;
   }

   private Set<BlockPos> elytraInterceptWedge(Vec3d predicted, LivingEntity entity) {
      LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();
      this.lockedCenter = BlockPos.ofFloored(predicted);
      this.lockedTargetUUID = entity.getUuid();
      Vec3d vel = entity.getVelocity();
      if (vel.lengthSquared() < 1.0E-6) {
         Vec3d look = entity.getRotationVector();
         vel = look.lengthSquared() < 1.0E-6 ? new Vec3d(1.0, 0.0, 0.0) : look;
      }

      Axis[] axes = Axis.values();
      double[] mag = new double[]{Math.abs(vel.x), Math.abs(vel.y), Math.abs(vel.z)};
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

   private Set<BlockPos> elytraCloseShell(LivingEntity entity) {
      LinkedHashSet<BlockPos> poses = new LinkedHashSet<>();
      Box bb = entity.getBoundingBox();
      int feetY = entity.getBlockPos().getY();
      int minX = PlaceUtil.minCell(bb.minX);
      int maxX = PlaceUtil.maxCell(bb.maxX);
      int minZ = PlaceUtil.minCell(bb.minZ);
      int maxZ = PlaceUtil.maxCell(bb.maxZ);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            BlockPos feetPos = new BlockPos(x, feetY, z);

            for (int ox = -1; ox <= 1; ox++) {
               for (int oz = -1; oz <= 1; oz++) {
                  if (Math.abs(ox) + Math.abs(oz) == 1) {
                     BlockPos adj = feetPos.add(ox, 0, oz);
                     if (this.canFill(adj)) {
                        poses.add(adj);
                     }

                     BlockPos head = adj.up();
                     if (this.canFill(head)) {
                        poses.add(head);
                     }
                  }
               }
            }

            BlockPos below = feetPos.down();
            if (this.canFill(below)) {
               poses.add(below);
            }

            BlockPos top = feetPos.up(2);
            if (this.canFill(top)) {
               poses.add(top);
            }
         }
      }

      return poses;
   }

   private boolean canFill(BlockPos pos) {
      BlockState s = mc.world.getBlockState(pos);
      return s.isAir() || s.isReplaceable();
   }

   private static double componentAlong(Vec3d v, Axis axis) {
      return switch (axis) {
         case X -> v.x;
         case Y -> v.y;
         case Z -> v.z;
         default -> throw new MatchException(null, null);
      };
   }

   private static BlockPos offset(BlockPos pos, Axis axis, int n) {
      return switch (axis) {
         case X -> pos.add(n, 0, 0);
         case Y -> pos.add(0, n, 0);
         case Z -> pos.add(0, 0, n);
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

   @Override
   public String getDisplayInfo() {
      return this.wantedPoses.isEmpty() ? null : String.valueOf(this.wantedPoses.size());
   }

   private int resolveSlot() {
      if (this.block.getValue() == AutoTrapModule.BlockType.HYBRID) {
         int web = this.hotbarSlotOf(Items.COBWEB);
         return web >= 0 ? web : this.hotbarSlotOf(Items.OBSIDIAN);
      } else {
         return this.hotbarSlotOf(this.block.getValue().item);
      }
   }

   private int hotbarSlotOf(Item item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() && r.type() != ResultType.OFFHAND ? r.slot() : -1;
   }

   private LivingEntity findTarget() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      double maxSq = this.range.getValue() * this.range.getValue();
      LivingEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (Entity e : mc.world.getEntities()) {
         if (e instanceof LivingEntity living && (targets == null || targets.isValidPlayerTarget(e))) {
            double dSq = mc.player.squaredDistanceTo(e);
            if (!(dSq > maxSq) && !(dSq >= bestSq)) {
               bestSq = dSq;
               best = living;
            }
         }
      }

      return best;
   }

   private boolean isValidStillTarget(LivingEntity e) {
      if (e != null && e.isAlive()) {
         if (mc.player.squaredDistanceTo(e) > this.range.getValue() * this.range.getValue()) {
            return false;
         }

         TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
         return targets == null || targets.isValidPlayerTarget(e);
      } else {
         return false;
      }
   }

   private Vec3d predictPosition(LivingEntity entity) {
      if (!this.prediction.getValue()) {
         return entity.getEntityPos();
      } else if (this.isAlreadyTrapped(entity)) {
         return entity.getEntityPos();
      } else {
         int ticks = entity.isGliding() ? this.elytraTicks.getValue() : this.predictionTicks.getValue();
         if (ticks <= 0) {
            return entity.getEntityPos();
         } else if (entity.isGliding()) {
            return this.boostingTicks.containsKey(entity.getUuid()) ? this.simulateBoostedElytra(entity, ticks) : this.simulateElytra(entity, ticks);
         } else {
            return this.simulateGround(entity, ticks);
         }
      }
   }

   private boolean isAlreadyTrapped(LivingEntity entity) {
      if (entity.isSwimming()) {
         return false;
      }

      BlockPos pos = entity.getBlockPos();
      if (mc.world.getBlockState(pos.down()).isReplaceable()) {
         return false;
      }

      for (Direction d : Type.HORIZONTAL) {
         if (mc.world.getBlockState(pos.up().offset(d)).isReplaceable()) {
            return false;
         }
      }

      return !mc.world.getBlockState(pos.up(2)).isReplaceable();
   }

   private Vec3d hybridHorizVel(LivingEntity entity) {
      double dx = entity.getX() - entity.lastX;
      double dz = entity.getZ() - entity.lastZ;
      double speed = Math.hypot(dx, dz);
      if (speed < 1.0E-4) {
         return Vec3d.ZERO;
      }

      float yawRad = (float)Math.toRadians(entity.getYaw());
      double lookX = -Math.sin(yawRad);
      double lookZ = Math.cos(yawRad);
      double bias = this.lookBlend.getValue() * Math.min(1.0, speed / 0.21);
      return new Vec3d(dx * (1.0 - bias) + lookX * speed * bias, 0.0, dz * (1.0 - bias) + lookZ * speed * bias);
   }

   private Vec3d simulateGround(LivingEntity entity, int ticks) {
      Vec3d pos = entity.getEntityPos();
      Vec3d horiz = this.hybridHorizVel(entity);
      double vy = entity.getY() - entity.lastY;
      boolean onGround = entity.isOnGround();
      if (onGround && vy <= 0.05) {
         vy = 0.0;
      }

      Vec3d vel = new Vec3d(horiz.x, vy, horiz.z);

      for (int i = 0; i < ticks; i++) {
         Vec3d nextPos = pos.add(vel);
         if (vel.y != 0.0) {
            Vec3d rayStart = pos.add(0.0, 0.5, 0.0);
            Vec3d rayEnd = nextPos.add(0.0, 0.5, 0.0);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(rayStart, rayEnd, ShapeType.COLLIDER, FluidHandling.NONE, entity));
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getSide() == Direction.UP) {
               return new Vec3d(nextPos.x, hit.getBlockPos().getY() + 1.0, nextPos.z);
            }
         }

         pos = nextPos;
         if (!onGround) {
            vel = new Vec3d(vel.x * 0.98, (vel.y - 0.08) * 0.98, vel.z * 0.98);
         }

         if (vel.lengthSquared() < 1.0E-4) {
            break;
         }
      }

      return pos;
   }

   private Vec3d simulateElytra(LivingEntity entity, int ticks) {
      Vec3d pos = entity.getEntityPos();
      Vec3d tickVel = new Vec3d(entity.getX() - entity.lastX, entity.getY() - entity.lastY, entity.getZ() - entity.lastZ);
      Vec3d look = entity.getRotationVector();
      double speed = tickVel.length();
      double bias = this.lookBlend.getValue();
      Vec3d vel = new Vec3d(
         tickVel.x * (1.0 - bias) + look.x * speed * bias, tickVel.y * (1.0 - bias) + look.y * speed * bias, tickVel.z * (1.0 - bias) + look.z * speed * bias
      );
      float pitchRad = (float)Math.toRadians(entity.getPitch());
      double cos = Math.cos(pitchRad);

      for (int i = 0; i < ticks; i++) {
         pos = pos.add(vel);
         double horizSpeed = Math.hypot(vel.x, vel.z);
         double len = vel.length();
         double liftFactor = cos * cos * Math.min(1.0, len / 0.4);
         double vy = vel.y + (-0.04 + liftFactor * 0.06);
         if (vel.y < 0.0 && horizSpeed > 0.0) {
            vy += -0.1 * vel.y * liftFactor;
         }

         Vec3d vAfterLift = new Vec3d(vel.x, vy, vel.z);
         Vec3d align = new Vec3d(
            look.x * 0.1 + (look.x * 1.5 - vAfterLift.x) * 0.01,
            look.y * 0.1 + (look.y * 1.5 - vAfterLift.y) * 0.01,
            look.z * 0.1 + (look.z * 1.5 - vAfterLift.z) * 0.01
         );
         Vec3d vAligned = vAfterLift.add(align);
         vel = new Vec3d(vAligned.x * 0.99, vAligned.y * 0.98, vAligned.z * 0.99);
      }

      return pos;
   }

   private Vec3d simulateBoostedElytra(LivingEntity entity, int ticks) {
      Vec3d pos = entity.getEntityPos();
      Vec3d vel = new Vec3d(entity.getX() - entity.lastX, entity.getY() - entity.lastY, entity.getZ() - entity.lastZ);

      for (int i = 0; i < ticks; i++) {
         pos = pos.add(vel);
         double vy = Math.max(vel.y, -3.92);
         vel = new Vec3d(vel.x * 0.991, vy, vel.z * 0.991);
      }

      return pos;
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
   }

   public enum BlockType {
      OBSIDIAN(Items.OBSIDIAN),
      COBWEB(Items.COBWEB),
      HYBRID(null);

      final Item item;

      BlockType(Item item) {
         this.item = item;
      }
   }
}
