package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.PreTickEvent;
import dev.piolunson.event.impl.network.PacketEvent;
import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.client.TargetsModule;
import dev.piolunson.features.modules.world.SpeedMineModule;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.PlacementManager;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.util.DamageSyncTracker;
import dev.piolunson.util.EnchantmentUtil;
import dev.piolunson.util.MathUtil;
import dev.piolunson.util.PlaceUtil;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.ResultType;
import dev.piolunson.util.models.Timer;
import dev.piolunson.util.player.ChatUtil;
import dev.piolunson.util.player.EatUtil;
import dev.piolunson.util.render.RenderUtil;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

public class AutoCrystalModule extends Module {
   private final Setting<Boolean> place = this.bool("Place", true).setPage("General");
   private final Setting<Integer> placeDelay = this.num("PlaceDelay", 0, 0, 2000).setPage("General");
   private final Setting<Boolean> doBreak = this.bool("Break", true).setPage("General");
   private final Setting<Integer> breakDelay = this.num("BreakDelay", 0, 0, 2000).setPage("General");
   private final Setting<Boolean> swing = this.bool("Swing", true).setPage("General");
   private final Setting<Double> minDamage = this.num("MinDamage", 4.0, 0.0, 36.0).setPage("General");
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 4.0, 0.0, 36.0).setPage("General");
   private final Setting<Boolean> antiSurround = this.bool("AntiSurround", true).setPage("General");
   private final Setting<Integer> antiSurroundCompletion = this.num("AntiSurroundCompletion", 70, 0, 100).setPage("General");
   private final Setting<Boolean> damageSync = this.bool("DamageSync", false).setPage("General");
   private final Setting<Boolean> antiChinese = this.bool("AntiChinese", false).setPage("AntiChinese");
   private final Setting<Double> antiChineseRange = this.num("Range", 5.0, 1.0, 5.14).setPage("AntiChinese");
   private final Setting<Double> antiChineseEnemyRange = this.num("EnemyRange", 8.0, 1.0, 30.0).setPage("AntiChinese");
   private final Setting<Boolean> basePlace = this.bool("BasePlace", true).setPage("BasePlace");
   private final Setting<Double> basePlaceTargetRange = this.num("BasePlaceEnemyRange", 7.0, 0.1, 15.0).setPage("BasePlace");
   private final Setting<Double> basePlaceMinDamage = this.num("BasePlaceMinDamage", 5.0, 0.0, 36.0).setPage("BasePlace");
   private final Setting<Boolean> debug = this.bool("Debug", false).setPage("Extra");
   private final Setting<Boolean> packetLog = this.bool("PacketLog", false).setPage("Extra");
   private final Setting<AutoCrystalModule.RenderMode> renderMode = this.mode("Render", AutoCrystalModule.RenderMode.GRADIENT).setPage("Render");
   private final Setting<Color> sideColor = this.color("SideColor", 130, 80, 255, 45).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 130, 80, 255, 255).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Double> gradientHeight = this.num("GradientHeight", 0.7, 0.0, 1.0).setPage("Render");
   private final Setting<Integer> renderTime = this.num("RenderTime", 10, 1, 40).setPage("Render");
   private final Setting<Integer> smoothness = this.num("Smoothness", 10, 1, 50).setPage("Render");
   private final Setting<Color> pulseColor = this.color("PulseColor", 130, 80, 255, 150).setPage("Render");
   private final Setting<Double> pulseSpeed = this.num("PulseSpeed", 2.0, 0.2, 6.0).setPage("Render");
   private final Setting<Double> pulseExpand = this.num("PulseExpand", 0.35, 0.0, 1.0).setPage("Render");
   private BlockPos renderPos = null;
   private long renderStartMs = 0L;
   private Box smoothBox = null;
   private static final double PLACE_RANGE = 6.0;
   private static final double BASE_PLACE_RANGE = 6.0;
   private static final double BREAK_RANGE = 3.0;
   private static final int BALANCE = 4;
   private static final double HEALTH_BALANCE = 0.2;
   private static final double ARMOR_BALANCE = 0.2;
   private static final boolean NO_SELF_POP = true;
   private static final long PLACE_PENDING_MS = 40L;
   private static final long CRYSTAL_TRACK_MS = 1750L;
   private final Timer placeTimer = new Timer();
   private final Timer breakTimer = new Timer();
   private final Long2LongOpenHashMap crystalPlaces = new Long2LongOpenHashMap();
   private float lastBestDamage = 0.0F;
   private final HashMap<String, int[]> diagSendCounts = new HashMap<>();
   private long diagWindowStart = 0L;
   private int diagWindowSends = 0;
   private int diagPeakWindow = 0;
   private long diagLifetimeSends = 0L;
   private long diagLastTickSends = 0L;
   private int diagPeakTickSends = 0;
   private int diagPlaceAttempt;
   private int diagPlaceSent;
   private int diagBreakSent;
   private int diagInstaBreak;
   private double lastCalcMs = 0.0;
   private int lastReactorPlaceTick = -1;
   private BlockPos lastPlaceAir = null;
   private final AutoCrystalModule.ExposureContext exposureCtx = new AutoCrystalModule.ExposureContext();
   private final ArrayList<AutoCrystalModule.PlaceCandidate> candidates = new ArrayList<>();
   private final Set<Integer> deadIds = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> seeThrough = new HashSet<>();
   private int cachedChunkX = Integer.MIN_VALUE;
   private int cachedChunkZ = Integer.MIN_VALUE;
   private Chunk cachedChunk;
   private final PlacementManager.PlacementListener placementListener = this::onBlockUpdate;
   private static final int[][] FACE_OFFSETS = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
   private final ArrayDeque<Long> explodeTimes = new ArrayDeque<>();
   private static final float EXPLODABLE_RESISTANCE = 600.0F;
   private static final double ANTI_CHINESE_RADIUS = 3.0;

   public AutoCrystalModule() {
      super("AutoCrystal", "Automatically places and breaks end crystals.", Module.Category.COMBAT);
      this.placeDelay.setVisibility(v -> this.place.getValue());
      this.breakDelay.setVisibility(v -> this.doBreak.getValue());
      this.swing.setVisibility(v -> this.doBreak.getValue());
      this.basePlaceTargetRange.setVisibility(v -> this.basePlace.getValue());
      this.basePlaceMinDamage.setVisibility(v -> this.basePlace.getValue());
      this.antiChineseRange.setVisibility(v -> this.antiChinese.getValue());
      this.antiChineseEnemyRange.setVisibility(v -> this.antiChinese.getValue());
   }

   @Override
   public void onEnable() {
      this.crystalPlaces.clear();
      this.deadIds.clear();
      this.placeTimer.reset();
      this.breakTimer.reset();
      this.lastBestDamage = 0.0F;
      this.lastCalcMs = 0.0;
      this.lastReactorPlaceTick = -1;
      this.resetDiag();
      Homovore.placementManager.addListener(this.placementListener);
   }

   @Override
   public void onDisable() {
      Homovore.placementManager.removeListener(this.placementListener);
      this.crystalPlaces.clear();
      this.deadIds.clear();
      this.lastBestDamage = 0.0F;
      this.lastPlaceAir = null;
      this.resetDiag();
      this.renderPos = null;
      this.smoothBox = null;
   }

   private void markRender(BlockPos crystalPos) {
      if (this.renderMode.getValue() != AutoCrystalModule.RenderMode.NONE) {
         this.renderPos = crystalPos;
         this.renderStartMs = System.currentTimeMillis();
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck() && this.renderMode.getValue() != AutoCrystalModule.RenderMode.NONE && this.renderPos != null) {
         long elapsed = System.currentTimeMillis() - this.renderStartMs;
         long life = this.renderTime.getValue().intValue() * 50L;
         if (elapsed > life) {
            this.renderPos = null;
            this.smoothBox = null;
         } else {
            switch ((AutoCrystalModule.RenderMode)this.renderMode.getValue()) {
               case GRADIENT:
                  this.drawGradient(event, false);
                  break;
               case GRADIENT_SMOOTH:
                  this.drawGradient(event, true);
                  break;
               case BOX_SMOOTH:
                  this.drawBoxSmooth(event);
                  break;
               case PULSE:
                  this.drawPulse(event, elapsed, life);
            }
         }
      }
   }

   private void updateSmoothBox(Box target) {
      if (this.smoothBox == null) {
         this.smoothBox = target;
      } else {
         double f = 1.0 / this.smoothness.getValue().intValue();
         this.smoothBox = new Box(
            lerp(this.smoothBox.minX, target.minX, f),
            lerp(this.smoothBox.minY, target.minY, f),
            lerp(this.smoothBox.minZ, target.minZ, f),
            lerp(this.smoothBox.maxX, target.maxX, f),
            lerp(this.smoothBox.maxY, target.maxY, f),
            lerp(this.smoothBox.maxZ, target.maxZ, f)
         );
      }
   }

   private void drawBoxSmooth(Render3DEvent event) {
      this.updateSmoothBox(new Box(this.renderPos.down()));
      RenderUtil.drawBoxFilled(event.getMatrix(), this.smoothBox, this.sideColor.getValue());
      RenderUtil.drawBox(event.getMatrix(), this.smoothBox, this.lineColor.getValue(), this.lineWidth.getValue());
   }

   private void drawGradient(Render3DEvent event, boolean smooth) {
      Color side = this.sideColor.getValue();
      Color line = this.lineColor.getValue();
      Color clear = new Color(side.getRed(), side.getGreen(), side.getBlue(), 0);
      double height = this.gradientHeight.getValue();
      float lw = this.lineWidth.getValue();
      BlockPos basePos = this.renderPos.down();
      double x1;
      double z1;
      double x2;
      double z2;
      double yTop;
      if (smooth) {
         this.updateSmoothBox(new Box(basePos));
         x1 = this.smoothBox.minX;
         z1 = this.smoothBox.minZ;
         x2 = this.smoothBox.maxX;
         z2 = this.smoothBox.maxZ;
         yTop = this.smoothBox.maxY;
      } else {
         x1 = basePos.getX();
         z1 = basePos.getZ();
         x2 = x1 + 1.0;
         z2 = z1 + 1.0;
         yTop = basePos.getY() + 1;
      }

      double yBottom = yTop - height;
      MatrixStack m = event.getMatrix();
      RenderUtil.quadHorizontal(m, x1, z1, x2, z2, yTop, side);
      RenderUtil.gradientQuadVertical(m, x1, z1, x2, z1, yTop, yBottom, side, clear);
      RenderUtil.gradientQuadVertical(m, x2, z1, x2, z2, yTop, yBottom, side, clear);
      RenderUtil.gradientQuadVertical(m, x2, z2, x1, z2, yTop, yBottom, side, clear);
      RenderUtil.gradientQuadVertical(m, x1, z2, x1, z1, yTop, yBottom, side, clear);
      RenderUtil.drawLine(new Vec3d(x1, yTop, z1), new Vec3d(x2, yTop, z1), line, lw);
      RenderUtil.drawLine(new Vec3d(x2, yTop, z1), new Vec3d(x2, yTop, z2), line, lw);
      RenderUtil.drawLine(new Vec3d(x2, yTop, z2), new Vec3d(x1, yTop, z2), line, lw);
      RenderUtil.drawLine(new Vec3d(x1, yTop, z2), new Vec3d(x1, yTop, z1), line, lw);
      RenderUtil.drawLine(new Vec3d(x1, yTop, z1), new Vec3d(x1, yBottom, z1), line, lw);
      RenderUtil.drawLine(new Vec3d(x2, yTop, z1), new Vec3d(x2, yBottom, z1), line, lw);
      RenderUtil.drawLine(new Vec3d(x2, yTop, z2), new Vec3d(x2, yBottom, z2), line, lw);
      RenderUtil.drawLine(new Vec3d(x1, yTop, z2), new Vec3d(x1, yBottom, z2), line, lw);
   }

   private void drawPulse(Render3DEvent event, long elapsed, long life) {
      double fade = 1.0 - (double)elapsed / life;
      double phase = elapsed / 1000.0 * this.pulseSpeed.getValue();
      double pulse = (Math.sin(phase * Math.PI * 2.0) + 1.0) * 0.5;
      double grow = pulse * this.pulseExpand.getValue();
      Color base = this.pulseColor.getValue();
      Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int)(base.getAlpha() * fade), 0, 255));
      Color line = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int)(255.0 * fade), 0, 255));
      Box box = new Box(this.renderPos).expand(grow);
      RenderUtil.drawBoxFilled(event.getMatrix(), box, fill);
      RenderUtil.drawBox(event.getMatrix(), box, line, this.lineWidth.getValue());
   }

   private static double lerp(double a, double b, double f) {
      return a + (b - a) * f;
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (mc.world != null) {
         if (event.getPacket() instanceof EntityStatusS2CPacket pkt) {
            if (pkt.getStatus() == 3) {
               Entity e = pkt.getEntity(mc.world);
               if (e != null) {
                  this.deadIds.add(e.getId());
               }
            }
         } else {
            if (event.getPacket() instanceof EntitySpawnS2CPacket pkt) {
               this.onCrystalSpawn(pkt);
            }
         }
      }
   }

   @Subscribe
   private void onPacketSendDiag(PacketEvent.Send event) {
      if (this.packetLog.getValue()) {
         this.diagSendCounts.computeIfAbsent(event.getPacket().getClass().getSimpleName(), k -> new int[1])[0]++;
         this.diagWindowSends++;
         this.diagLifetimeSends++;
         long now = System.currentTimeMillis();
         if (this.diagWindowStart == 0L) {
            this.diagWindowStart = now;
         }

         if (now - this.diagWindowStart >= 1000L) {
            this.flushDiag(now);
         }
      }
   }

   @Subscribe(priority = 100)
   private void onDiagTickBoundary(PreTickEvent event) {
      if (this.packetLog.getValue()) {
         int tickDelta = (int)(this.diagLifetimeSends - this.diagLastTickSends);
         this.diagLastTickSends = this.diagLifetimeSends;
         if (tickDelta > this.diagPeakTickSends) {
            this.diagPeakTickSends = tickDelta;
         }
      }
   }

   private void flushDiag(long now) {
      if (this.diagWindowSends > this.diagPeakWindow) {
         this.diagPeakWindow = this.diagWindowSends;
      }

      StringBuilder breakdown = new StringBuilder();
      this.diagSendCounts
         .entrySet()
         .stream()
         .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
         .forEach(e -> breakdown.append(e.getKey()).append('=').append(e.getValue()[0]).append(' '));
      Homovore.LOGGER
         .info(
            "[ACDiag] {}ms sends={} peakTick={} peakSec={} | place att/sent={}/{} break={} insta={} | {}",
            now - this.diagWindowStart,
            this.diagWindowSends,
            this.diagPeakTickSends,
            this.diagPeakWindow,
            this.diagPlaceAttempt,
            this.diagPlaceSent,
            this.diagBreakSent,
            this.diagInstaBreak,
            breakdown.toString().trim()
         );
      this.diagSendCounts.clear();
      this.diagWindowSends = 0;
      this.diagPeakTickSends = 0;
      this.diagPlaceAttempt = this.diagPlaceSent = this.diagBreakSent = this.diagInstaBreak = 0;
      this.diagWindowStart = now;
   }

   private void resetDiag() {
      this.diagSendCounts.clear();
      this.diagWindowStart = 0L;
      this.diagWindowSends = 0;
      this.diagPeakWindow = 0;
      this.diagLifetimeSends = 0L;
      this.diagLastTickSends = 0L;
      this.diagPeakTickSends = 0;
      this.diagPlaceAttempt = this.diagPlaceSent = this.diagBreakSent = this.diagInstaBreak = 0;
   }

   private void onCrystalSpawn(EntitySpawnS2CPacket pkt) {
      if (this.doBreak.getValue() && this.breakDelay.getValue() == 0) {
         if (pkt.getEntityType() == EntityType.END_CRYSTAL) {
            if (!nullCheck() && !mc.player.isDead()) {
               if (!EatUtil.shouldDefer()) {
                  AutoSwordModule sword = Homovore.moduleManager.getModuleByClass(AutoSwordModule.class);
                  if (sword == null || !sword.isEnabled() || !sword.isMaceAttackReady()) {
                     EndCrystalEntity crystal = new EndCrystalEntity(EntityType.END_CRYSTAL, mc.world);
                     crystal.setPosition(pkt.getX(), pkt.getY(), pkt.getZ());
                     crystal.setId(pkt.getEntityId());
                     Box bb = crystal.getBoundingBox();
                     Vec3d reachEye = this.bestReachEye(bb);
                     if (!(sqDistToBox(reachEye, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ) > 9.0)) {
                        float optimisticSelf = this.maxDamageSelf(crystal.getEntityPos());
                        float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                        if (!(optimisticSelf > this.maxSelfDamage.getValue()) && !(optimisticSelf + 1.5F >= playerHealth)) {
                           float serverYaw = Homovore.rotationManager.getServerYaw();
                           float serverPitch = Homovore.rotationManager.getServerPitch();
                           if (this.canBreakCrystal(crystal, serverYaw, serverPitch)) {
                              this.diagInstaBreak++;
                              mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                              if (this.swing.getValue()) {
                                 mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void onBlockUpdate(BlockPos pos, boolean nowAir) {
      if (nowAir) {
         if (!nullCheck() && !mc.player.isDead()) {
            if (!EatUtil.shouldDefer()) {
               AutoSwordModule sword = Homovore.moduleManager.getModuleByClass(AutoSwordModule.class);
               if (sword == null || !sword.isEnabled() || !sword.isMaceAttackReady()) {
                  double cutoff = 64.0;
                  if (!(mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > cutoff)) {
                     this.updateSeeThrough();
                     TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
                     double scanRadius = 18.0;
                     List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, scanRadius);
                     if (this.doBreak.getValue() && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                        EndCrystalEntity target = this.findBestCrystal(potentialTargets);
                        if (target != null) {
                           this.breakCrystal(target);
                           this.breakTimer.reset();
                           return;
                        }
                     }

                     if (this.place.getValue() && this.placeTimer.passedMs(this.placeDelay.getValue().intValue())) {
                        if (mc.player.age != this.lastReactorPlaceTick) {
                           this.lastReactorPlaceTick = mc.player.age;
                           AutoCrystalModule.PlaceTarget placeTarget = this.findBestPlace(potentialTargets);
                           if (placeTarget != null) {
                              this.doPlace(placeTarget);
                              this.placeTimer.reset();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDead()) {
         if (!EatUtil.shouldDefer()) {
            AutoSwordModule sword = Homovore.moduleManager.getModuleByClass(AutoSwordModule.class);
            if (sword != null && sword.isEnabled() && sword.isMaceAttackReady()) {
               this.lastBestDamage = 0.0F;
            } else {
               this.ageCrystalPlaces();
               this.updateSeeThrough();
               boolean antiChineseReady = this.antiChinese.getValue() && this.enemyNearby(this.antiChineseEnemyRange.getValue());
               boolean broke = false;
               if (this.doBreak.getValue() && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                  TargetsModule breakTargets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
                  List<AutoCrystalModule.TargetCache> breakTargetCache = this.collectTargets(breakTargets, 15.0);
                  EndCrystalEntity target = this.findBestCrystal(breakTargetCache);
                  if (target != null) {
                     this.breakCrystal(target);
                     this.breakTimer.reset();
                     broke = true;
                  }
               }

               if (!broke && antiChineseReady && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                  EndCrystalEntity acTarget = this.findAntiChineseCrystal();
                  if (acTarget != null) {
                     this.breakCrystal(acTarget);
                     this.breakTimer.reset();
                  }
               }

               boolean canPlace = this.place.getValue() && this.placeTimer.passedMs(this.placeDelay.getValue().intValue());
               long calcStart = System.nanoTime();
               TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
               double scanRadius = 18.0 + (this.basePlace.getValue() ? 6.0 : 0.0);
               List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, scanRadius);
               AutoCrystalModule.PlaceTarget crystalTarget = canPlace ? this.findBestPlace(potentialTargets) : null;
               AutoCrystalModule.BasePlaceTarget baseTarget = this.basePlace.getValue() ? this.findBestBasePlace(targets, potentialTargets) : null;
               this.lastCalcMs = (System.nanoTime() - calcStart) / 1000000.0;
               if (this.debug.getValue()) {
                  ChatUtil.sendPersistent("autocrystal:calc", Text.literal(String.format("AutoCrystal calc: %.3f ms", this.lastCalcMs)));
               }

               boolean baseWins = baseTarget != null && (crystalTarget == null || baseTarget.damage > crystalTarget.damage);
               boolean placed = false;
               if (baseWins) {
                  boolean baseQueued = this.doBasePlace(baseTarget);
                  if (baseQueued) {
                     Homovore.placementManager.flushQueue();
                     if (canPlace) {
                        this.doPlace(new AutoCrystalModule.PlaceTarget(baseTarget.base, baseTarget.damage), true);
                        this.placeTimer.reset();
                        placed = true;
                     }
                  }
               } else if (crystalTarget != null) {
                  this.doPlace(crystalTarget);
                  this.placeTimer.reset();
                  placed = true;
               }

               if (!placed && antiChineseReady && canPlace) {
                  AutoCrystalModule.PlaceTarget acPlace = this.findAntiChinesePlace();
                  if (acPlace != null) {
                     this.doPlace(acPlace);
                     this.placeTimer.reset();
                  }
               }
            }
         }
      }
   }

   private AutoCrystalModule.BasePlaceTarget findBestBasePlace(TargetsModule targets, List<AutoCrystalModule.TargetCache> potentialTargets) {
      LivingEntity primary = this.findClosestBaseTarget(targets);
      if (primary == null) {
         return null;
      }

      Vec3d eye = mc.player.getCameraPosVec(1.0F);
      Vec3d playerCenter = mc.player.getEntityPos();
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      double baseMin = this.basePlaceMinDamage.getValue();
      int targetFeetY = primary.getBlockPos().getY();
      double range = 6.0;
      double rangeSq = range * range;
      int r = (int)Math.ceil(range);
      BlockPos playerPos = mc.player.getBlockPos();
      int topLayerY = targetFeetY - 1;
      int bottomLayerY = playerPos.getY() - r;
      int rr = r * r;

      for (int by = topLayerY; by >= bottomLayerY; by--) {
         BlockPos layerBest = null;
         float layerDmg = 0.0F;
         int dy = by - playerPos.getY();
         int dyy = dy * dy;
         if (dyy <= rr) {
            for (int dx = -r; dx <= r; dx++) {
               int dxxdyy = dx * dx + dyy;

               for (int dz = -r; dz <= r; dz++) {
                  if (dxxdyy + dz * dz <= rr) {
                     BlockPos basePos = new BlockPos(playerPos.getX() + dx, by, playerPos.getZ() + dz);
                     BlockPos crystalPos = basePos.up();
                     BlockState crystalState = mc.world.getBlockState(crystalPos);
                     if ((crystalState.isAir() || crystalState.isReplaceable())
                        && !this.isBlocked(crystalPos)
                        && !(playerCenter.squaredDistanceTo(basePos.getX() + 0.5, basePos.getY() + 0.5, basePos.getZ() + 0.5) > rangeSq)
                        && PlaceUtil.canPlace(basePos)
                        && !(
                           sqDistToBox(
                                 eye,
                                 crystalPos.getX(),
                                 crystalPos.getY(),
                                 crystalPos.getZ(),
                                 crystalPos.getX() + 1,
                                 crystalPos.getY() + 1,
                                 crystalPos.getZ() + 1
                              )
                              > 36.0
                        )) {
                        Vec3d crystalCenter = new Vec3d(crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
                        double cMinX = crystalCenter.x - 1.0;
                        double cMinZ = crystalCenter.z - 1.0;
                        double cMaxX = crystalCenter.x + 1.0;
                        double cMaxY = crystalCenter.y + 2.0;
                        double cMaxZ = crystalCenter.z + 1.0;
                        Vec3d breakEye = this.bestReachEye(new Box(cMinX, crystalCenter.y, cMinZ, cMaxX, cMaxY, cMaxZ));
                        if (!(sqDistToBox(breakEye, cMinX, crystalCenter.y, cMinZ, cMaxX, cMaxY, cMaxZ) > 9.0)
                           && anyTargetWithin(potentialTargets, crystalCenter, 144.0)) {
                           float bound = 0.0F;
                           boolean anyBound = false;
                           int i = 0;

                           for (int n = potentialTargets.size(); i < n; i++) {
                              AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
                              if (!(tc.pos.squaredDistanceTo(crystalCenter) > 144.0)) {
                                 float dmg = this.maxDamage(tc, crystalCenter);
                                 if (!(dmg < this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken))) {
                                    bound += dmg;
                                    anyBound = true;
                                 }
                              }
                           }

                           if (anyBound && !(bound < baseMin) && !(bound <= layerDmg) && this.selfDamageOk(crystalCenter, basePos, maxSelf, playerHealth)) {
                              float totalDmg = 0.0F;
                              boolean anyTarget = false;
                              int ix = 0;

                              for (int n = potentialTargets.size(); ix < n; ix++) {
                                 AutoCrystalModule.TargetCache tc = potentialTargets.get(ix);
                                 if (!(tc.pos.squaredDistanceTo(crystalCenter) > 144.0)) {
                                    float dmg = this.calcDamage(tc, crystalCenter, basePos);
                                    if (!(dmg < this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken))) {
                                       totalDmg += dmg;
                                       anyTarget = true;
                                    }
                                 }
                              }

                              if (anyTarget && !(totalDmg < baseMin) && totalDmg > layerDmg) {
                                 layerDmg = totalDmg;
                                 layerBest = basePos;
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (layerBest != null) {
               return new AutoCrystalModule.BasePlaceTarget(layerBest, layerDmg);
            }
         }
      }

      return null;
   }

   private boolean doBasePlace(AutoCrystalModule.BasePlaceTarget target) {
      Result obs = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.HOTBAR_SCOPE);
      return obs.found() && obs.type() != ResultType.OFFHAND ? Homovore.placementManager.enqueue(target.base, Items.OBSIDIAN) : false;
   }

   private LivingEntity findClosestBaseTarget(TargetsModule targets) {
      double range = this.basePlaceTargetRange.getValue();
      double rangeSq = range * range;
      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;
      Box area = mc.player.getBoundingBox().expand(range);

      for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
         if (e instanceof PlayerEntity p && (targets == null || targets.isValidPlayerTarget(p))) {
            double d = mc.player.squaredDistanceTo(p);
            if (!(d > rangeSq) && d < bestDist) {
               bestDist = d;
               best = p;
            }
         }
      }

      return best;
   }

   private EndCrystalEntity findBestCrystal(List<AutoCrystalModule.TargetCache> potentialTargets) {
      EndCrystalEntity best = null;
      float bestDmg = 0.0F;
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      double maxSelf = this.maxSelfDamage.getValue();
      double breakRangeSq = 9.0;
      Box searchArea = mc.player.getBoundingBox().expand(5.0);

      for (Entity e : mc.world.getOtherEntities(null, searchArea)) {
         if (e instanceof EndCrystalEntity crystal) {
            Box bb = crystal.getBoundingBox();
            Vec3d reachEye = this.bestReachEye(bb);
            if (!(sqDistToBox(reachEye, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ) > breakRangeSq)) {
               Vec3d cp = crystal.getEntityPos();
               if (anyTargetWithin(potentialTargets, cp, 144.0) && this.selfDamageOk(cp, null, maxSelf, playerHealth)) {
                  float totalDmg = 0.0F;
                  boolean anyTarget = false;

                  for (AutoCrystalModule.TargetCache tc : potentialTargets) {
                     if (!(tc.pos.squaredDistanceTo(cp) > 144.0) && (!this.damageSync.getValue() || !DamageSyncTracker.shouldHold(tc.entity))) {
                        float dmg = this.calcDamage(tc, cp);
                        if (!(dmg < this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken))) {
                           totalDmg += dmg;
                           anyTarget = true;
                        }
                     }
                  }

                  if (anyTarget && totalDmg > bestDmg) {
                     bestDmg = totalDmg;
                     best = crystal;
                  }
               }
            }
         }
      }

      this.lastBestDamage = bestDmg;
      return best;
   }

   private void breakCrystal(EndCrystalEntity crystal) {
      Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
      Vec3d hit = this.getClosestPointToEye(eyePos, crystal.getBoundingBox());
      float[] angles = MathUtil.calcAngle(eyePos, hit);
      if (this.canBreakCrystal(crystal, angles[0], angles[1])) {
         Homovore.rotationManager.submit(new RotationRequest("AutoCrystal_break", 60, angles[0], angles[1], RotationRequest.Mode.SILENT));
         this.diagBreakSent++;
         mc.interactionManager.attackEntity(mc.player, crystal);
         if (this.swing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
         }
      }
   }

   private boolean canBreakCrystal(EndCrystalEntity crystal, float yaw, float pitch) {
      Box bb = crystal.getBoundingBox();
      Vec3d eyePos = this.bestReachEye(bb);
      if (bb.contains(eyePos)) {
         return true;
      }

      Vec3d look = this.getLookVector(yaw, pitch);
      Vec3d reachEnd = eyePos.add(look.multiply(3.0));
      return bb.raycast(eyePos, reachEnd).isPresent();
   }

   private AutoCrystalModule.PlaceTarget findBestPlace(List<AutoCrystalModule.TargetCache> potentialTargets) {
      Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
      BlockPos playerPos = mc.player.getBlockPos();
      int r = (int)Math.ceil(6.0);
      long now = System.currentTimeMillis();
      boolean isEnd = mc.world.getRegistryKey().equals(World.END);
      double placeRangeSq = 36.0;
      double breakRangeSq = 9.0;
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      Mutable cursor = new Mutable();
      int rr = r * r;
      this.candidates.clear();

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                  BlockState baseState = mc.world.getBlockState(cursor);
                  if (baseState.isOf(Blocks.OBSIDIAN) || baseState.isOf(Blocks.BEDROCK)) {
                     int bx = cursor.getX();
                     int by = cursor.getY();
                     int bz = cursor.getZ();
                     int ay = by + 1;
                     cursor.set(bx, ay, bz);
                     BlockState airState = mc.world.getBlockState(cursor);
                     if ((airState.isAir() || airState.isOf(Blocks.FIRE) && isEnd) && !(sqDistToBox(eyePos, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq)
                        )
                      {
                        double cx = bx + 0.5;
                        double cy = by + 1.0;
                        double cz = bz + 0.5;
                        Vec3d breakEye = this.bestReachEye(new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0));
                        if (!(sqDistToBox(breakEye, cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0) > breakRangeSq)) {
                           Vec3d crystalCenter = new Vec3d(cx, cy, cz);
                           float boundTotal = 0.0F;
                           boolean anyTarget = false;
                           int i = 0;

                           for (int n = potentialTargets.size(); i < n; i++) {
                              AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
                              if (!(tc.pos.squaredDistanceTo(crystalCenter) > 144.0)) {
                                 float dmg = this.maxDamage(tc, crystalCenter);
                                 if (!(dmg < this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken))) {
                                    boundTotal += dmg;
                                    anyTarget = true;
                                 }
                              }
                           }

                           if (anyTarget) {
                              this.candidates
                                 .add(new AutoCrystalModule.PlaceCandidate(new BlockPos(bx, by, bz), new BlockPos(bx, ay, bz), crystalCenter, boundTotal));
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (this.candidates.isEmpty()) {
         return null;
      }

      this.candidates.sort((a, b) -> Float.compare(b.bound, a.bound));
      BlockPos locked = null;
      if (this.lastPlaceAir != null) {
         long lockTs = this.crystalPlaces.get(this.lastPlaceAir.asLong());
         boolean pending = lockTs != 0L && now - lockTs < 40L;
         if (!pending && !this.hasLiveCrystalAt(this.lastPlaceAir)) {
            this.lastPlaceAir = null;
         } else {
            double cx = this.lastPlaceAir.getX() + 0.5;
            double cy = this.lastPlaceAir.getY();
            double cz = this.lastPlaceAir.getZ() + 0.5;
            Vec3d lockEye = this.bestReachEye(new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0));
            if (sqDistToBox(lockEye, cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0) <= breakRangeSq) {
               locked = this.lastPlaceAir;
            } else {
               this.lastPlaceAir = null;
            }
         }
      }

      AutoCrystalModule.PlaceTarget best = null;
      int i = 0;

      for (int n = this.candidates.size(); i < n; i++) {
         AutoCrystalModule.PlaceCandidate c = this.candidates.get(i);
         if (best != null && c.bound <= best.damage) {
            break;
         }

         if ((locked == null || c.airPos.equals(locked)) && !this.isBlocked(c.airPos)) {
            long pendingTs = this.crystalPlaces.get(c.airPos.asLong());
            if ((pendingTs == 0L || now - pendingTs >= 40L) && this.selfDamageOk(c.crystalCenter, null, maxSelf, playerHealth)) {
               float totalDmg = 0.0F;
               boolean anyTarget = false;
               int j = 0;

               for (int m = potentialTargets.size(); j < m; j++) {
                  AutoCrystalModule.TargetCache tc = potentialTargets.get(j);
                  if (!(tc.pos.squaredDistanceTo(c.crystalCenter) > 144.0)) {
                     float dmg = this.calcDamage(tc, c.crystalCenter);
                     if (!(dmg < this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken))) {
                        totalDmg += dmg;
                        anyTarget = true;
                     }
                  }
               }

               if (anyTarget && (best == null || totalDmg > best.damage)) {
                  best = new AutoCrystalModule.PlaceTarget(c.base, totalDmg);
               }
            }
         }
      }

      return best;
   }

   private boolean isBurrowBlock(BlockState state) {
      return state.isOf(Blocks.REDSTONE_BLOCK)
         || state.isOf(Blocks.PISTON)
         || state.isOf(Blocks.STICKY_PISTON)
         || state.isOf(Blocks.PISTON_HEAD)
         || state.isOf(Blocks.MOVING_PISTON);
   }

   private boolean nearBurrowBlock(Vec3d center) {
      int r = (int)Math.ceil(3.0);
      double radSq = 9.0;
      double playerRangeSq = this.antiChineseRange.getValue() * this.antiChineseRange.getValue();
      Vec3d playerPos = mc.player.getEntityPos();
      int cx = MathHelper.floor(center.x);
      int cy = MathHelper.floor(center.y);
      int cz = MathHelper.floor(center.z);
      Mutable cursor = new Mutable();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               int bx = cx + dx;
               int by = cy + dy;
               int bz = cz + dz;
               double ox = bx + 0.5 - center.x;
               double oy = by + 0.5 - center.y;
               double oz = bz + 0.5 - center.z;
               if (!(ox * ox + oy * oy + oz * oz > radSq) && !(playerPos.squaredDistanceTo(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq)) {
                  cursor.set(bx, by, bz);
                  if (this.isBurrowBlock(this.getStateFast(cursor))) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean adjacentToBurrowBlock(BlockPos airPos) {
      double playerRangeSq = this.antiChineseRange.getValue() * this.antiChineseRange.getValue();
      Vec3d playerPos = mc.player.getEntityPos();
      Mutable cursor = new Mutable();

      for (int[] o : FACE_OFFSETS) {
         int bx = airPos.getX() + o[0];
         int by = airPos.getY() + o[1];
         int bz = airPos.getZ() + o[2];
         if (!(playerPos.squaredDistanceTo(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq)) {
            cursor.set(bx, by, bz);
            if (this.isBurrowBlock(this.getStateFast(cursor))) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean enemyNearby(double range) {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      double rangeSq = range * range;
      Box area = mc.player.getBoundingBox().expand(range);

      for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
         if (e instanceof PlayerEntity p && (targets == null || targets.isValidPlayerTarget(p)) && mc.player.squaredDistanceTo(p) <= rangeSq) {
            return true;
         }
      }

      return false;
   }

   private AutoCrystalModule.PlaceTarget findAntiChinesePlace() {
      Vec3d eyePos = mc.player.getCameraPosVec(1.0F);
      BlockPos playerPos = mc.player.getBlockPos();
      int r = (int)Math.ceil(6.0);
      long now = System.currentTimeMillis();
      boolean isEnd = mc.world.getRegistryKey().equals(World.END);
      double placeRangeSq = 36.0;
      double breakRangeSq = 9.0;
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      Mutable cursor = new Mutable();
      int rr = r * r;
      AutoCrystalModule.PlaceTarget best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                  BlockState baseState = mc.world.getBlockState(cursor);
                  if (baseState.isOf(Blocks.OBSIDIAN) || baseState.isOf(Blocks.BEDROCK)) {
                     int bx = cursor.getX();
                     int by = cursor.getY();
                     int bz = cursor.getZ();
                     int ay = by + 1;
                     cursor.set(bx, ay, bz);
                     BlockState airState = mc.world.getBlockState(cursor);
                     if ((airState.isAir() || airState.isOf(Blocks.FIRE) && isEnd) && !(sqDistToBox(eyePos, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq)
                        )
                      {
                        double cx = bx + 0.5;
                        double cy = by + 1.0;
                        double cz = bz + 0.5;
                        Vec3d breakEye = this.bestReachEye(new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0));
                        if (!(sqDistToBox(breakEye, cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0) > breakRangeSq)) {
                           Vec3d crystalCenter = new Vec3d(cx, cy, cz);
                           double dSq = eyePos.squaredDistanceTo(crystalCenter);
                           if (!(dSq >= bestDistSq)) {
                              BlockPos airPos = new BlockPos(bx, ay, bz);
                              if (this.adjacentToBurrowBlock(airPos) && !this.isBlocked(airPos)) {
                                 long pendingTs = this.crystalPlaces.get(airPos.asLong());
                                 if ((pendingTs == 0L || now - pendingTs >= 40L) && this.selfDamageOk(crystalCenter, null, maxSelf, playerHealth)) {
                                    bestDistSq = dSq;
                                    best = new AutoCrystalModule.PlaceTarget(new BlockPos(bx, by, bz), 0.0F);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private EndCrystalEntity findAntiChineseCrystal() {
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      double maxSelf = this.maxSelfDamage.getValue();
      double breakRangeSq = 9.0;
      EndCrystalEntity best = null;
      double bestDist = Double.MAX_VALUE;
      Box searchArea = mc.player.getBoundingBox().expand(5.0);

      for (Entity e : mc.world.getOtherEntities(null, searchArea)) {
         if (e instanceof EndCrystalEntity crystal) {
            Box bb = crystal.getBoundingBox();
            Vec3d reachEye = this.bestReachEye(bb);
            if (!(sqDistToBox(reachEye, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ) > breakRangeSq)) {
               Vec3d cp = crystal.getEntityPos();
               if (this.nearBurrowBlock(cp) && this.selfDamageOk(cp, null, maxSelf, playerHealth)) {
                  double d = mc.player.squaredDistanceTo(crystal);
                  if (d < bestDist) {
                     bestDist = d;
                     best = crystal;
                  }
               }
            }
         }
      }

      return best;
   }

   private List<AutoCrystalModule.TargetCache> collectTargets(TargetsModule targets, double scanRadius) {
      List<AutoCrystalModule.TargetCache> out = new ArrayList<>();
      Box area = mc.player.getBoundingBox().expand(scanRadius);

      for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
         if (e instanceof LivingEntity living && !living.isDead() && !this.deadIds.contains(e.getId()) && (targets == null || targets.isValidPlayerTarget(e))) {
            boolean armorBroken = e instanceof PlayerEntity p && this.isAnyArmorBroken(p);
            float armor = (float)living.getAttributeValue(EntityAttributes.ARMOR);
            float toughness = (float)living.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
            int protPoints = 0;
            if (!living.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
               protPoints += 4;
            }

            if (!living.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
               protPoints += 4;
            }

            if (!living.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) {
               protPoints += 8;
            }

            if (!living.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
               protPoints += 4;
            }

            out.add(
               new AutoCrystalModule.TargetCache(
                  living,
                  living.getEntityPos(),
                  living.getBoundingBox(),
                  living.getHealth(),
                  living.getAbsorptionAmount(),
                  armorBroken,
                  armor,
                  toughness,
                  protPoints
               )
            );
         }
      }

      return out;
   }

   private void updateSeeThrough() {
      this.seeThrough.clear();
      if (this.antiSurround.getValue()) {
         SpeedMineModule mine = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
         if (mine != null && mine.isEnabled()) {
            mine.collectMiningPositions(this.seeThrough, this.antiSurroundCompletion.getValue().intValue() / 100.0);
         }
      }
   }

   private static boolean anyTargetWithin(List<AutoCrystalModule.TargetCache> cache, Vec3d center, double maxDistSq) {
      int i = 0;

      for (int n = cache.size(); i < n; i++) {
         if (cache.get(i).pos.squaredDistanceTo(center) <= maxDistSq) {
            return true;
         }
      }

      return false;
   }

   private static double sqDistToBox(Vec3d p, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = p.x < minX ? minX - p.x : (p.x > maxX ? p.x - maxX : 0.0);
      double dy = p.y < minY ? minY - p.y : (p.y > maxY ? p.y - maxY : 0.0);
      double dz = p.z < minZ ? minZ - p.z : (p.z > maxZ ? p.z - maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private boolean isBlocked(BlockPos airPos) {
      for (Entity e : mc.world.getOtherEntities(null, new Box(airPos))) {
         if (!(e instanceof ItemEntity)
            && (!(e instanceof EndCrystalEntity crystal) || crystal.age >= 5)
            && (!(e instanceof EndCrystalEntity crystal) || !crystal.getBlockPos().equals(airPos))) {
            return true;
         }
      }

      return false;
   }

   private void doPlace(AutoCrystalModule.PlaceTarget target) {
      this.doPlace(target, false);
   }

   private void doPlace(AutoCrystalModule.PlaceTarget target, boolean trustBase) {
      if (!InventoryUtil.find(Items.END_CRYSTAL, EnumSet.of(ResultType.HOTBAR)).found()) {
         this.lastBestDamage = 0.0F;
      } else {
         this.lastBestDamage = target.damage;
         this.diagPlaceAttempt++;
         if (Homovore.placementManager.placeCrystal(target.base, trustBase)) {
            this.diagPlaceSent++;
            this.recordPlace(target.base.up());
         }
      }
   }

   private void recordPlace(BlockPos airPos) {
      this.crystalPlaces.put(airPos.asLong(), System.currentTimeMillis());
      this.markRender(airPos);
      this.lastPlaceAir = airPos;
   }

   private boolean hasLiveCrystalAt(BlockPos airPos) {
      for (Entity e : mc.world.getOtherEntities(null, new Box(airPos))) {
         if (e instanceof EndCrystalEntity && e.getBlockPos().equals(airPos)) {
            return true;
         }
      }

      return false;
   }

   public boolean preplaceCrystal(BlockPos airPos, boolean snap) {
      if (!nullCheck() && this.place.getValue()) {
         BlockPos base = airPos.down();
         BlockState baseState = mc.world.getBlockState(base);
         if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) {
            return false;
         }

         if (this.isBlocked(airPos)) {
            return false;
         }

         long now = System.currentTimeMillis();
         long pendingTs = this.crystalPlaces.get(airPos.asLong());
         if (pendingTs != 0L && now - pendingTs < 40L) {
            return false;
         }

         this.doPlace(new AutoCrystalModule.PlaceTarget(base, 0.0F), false);
         return this.crystalPlaces.get(airPos.asLong()) != pendingTs;
      } else {
         return false;
      }
   }

   public boolean isDesirablePlacement(BlockPos airPos) {
      if (nullCheck()) {
         return false;
      }

      BlockPos base = airPos.down();
      BlockState baseState = mc.world.getBlockState(base);
      if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) {
         return false;
      }

      int bx = airPos.getX();
      int by = airPos.getY();
      int bz = airPos.getZ();
      Vec3d eye = mc.player.getCameraPosVec(1.0F);
      if (sqDistToBox(eye, bx, by, bz, bx + 1, by + 1, bz + 1) > 36.0) {
         return false;
      }

      double cx = bx + 0.5;
      double cy = by;
      double cz = bz + 0.5;
      if (sqDistToBox(this.bestReachEye(new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0)), cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0)
         > 9.0) {
         return false;
      }

      Vec3d crystalCenter = new Vec3d(cx, cy, cz);
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      if (!this.selfDamageOk(crystalCenter, null, this.maxSelfDamage.getValue(), playerHealth)) {
         return false;
      }

      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, 18.0);
      int i = 0;

      for (int n = potentialTargets.size(); i < n; i++) {
         AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
         if (!(tc.pos.squaredDistanceTo(crystalCenter) > 144.0)) {
            if (this.calcDamage(tc, crystalCenter) >= this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken)) {
               return true;
            }

            if (this.antiSurround.getValue() && this.isFeetAdjacent(tc.pos, airPos)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isFeetAdjacent(Vec3d targetPos, BlockPos airPos) {
      BlockPos feet = BlockPos.ofFloored(targetPos.x, targetPos.y, targetPos.z);
      if (feet.getY() != airPos.getY()) {
         return false;
      }

      int dx = airPos.getX() - feet.getX();
      int dz = airPos.getZ() - feet.getZ();
      return Math.abs(dx) == 1 && dz == 0 || Math.abs(dz) == 1 && dx == 0;
   }

   private void ageCrystalPlaces() {
      long now = System.currentTimeMillis();
      LongIterator it = this.crystalPlaces.keySet().iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (now - this.crystalPlaces.get(key) > 1750L) {
            it.remove();
         }
      }

      if (mc.player != null && mc.player.age % 100 == 0) {
         this.deadIds.clear();
      }
   }

   private float calcDamage(AutoCrystalModule.TargetCache tc, Vec3d explosionPos) {
      return this.calcDamage(tc, explosionPos, null);
   }

   private float calcDamage(AutoCrystalModule.TargetCache tc, Vec3d explosionPos, BlockPos phantomObsidianPos) {
      double dist = tc.pos.distanceTo(explosionPos);
      if (dist > 12.0) {
         return 0.0F;
      }

      double exposure = this.calcExposure(explosionPos, tc.box, phantomObsidianPos);
      return this.targetDamage(tc, dist, exposure);
   }

   private float maxDamage(AutoCrystalModule.TargetCache tc, Vec3d explosionPos) {
      double dist = tc.pos.distanceTo(explosionPos);
      return dist > 12.0 ? 0.0F : this.targetDamage(tc, dist, 1.0);
   }

   private float targetDamage(AutoCrystalModule.TargetCache tc, double dist, double exposure) {
      if (exposure <= 0.0) {
         return 0.0F;
      }

      double impact = (1.0 - dist / 12.0) * exposure;
      if (impact <= 0.0) {
         return 0.0F;
      }

      float dmg = this.baseExplosionDamage(impact);
      dmg = this.armorAbsorb(dmg, tc.armor, tc.toughness);
      dmg = DamageUtil.getInflictedDamage(dmg, tc.protPoints);
      return Math.max(dmg, 0.0F);
   }

   private float maxDamageSelf(Vec3d explosionPos) {
      double dist = mc.player.getEntityPos().distanceTo(explosionPos);
      return dist > 12.0 ? 0.0F : this.selfDamage(dist, 1.0);
   }

   private float calcDamageSelf(Vec3d explosionPos, BlockPos phantomObsidianPos) {
      double dist = mc.player.getEntityPos().distanceTo(explosionPos);
      if (dist > 12.0) {
         return 0.0F;
      }

      double exposure = this.calcExposure(explosionPos, mc.player.getBoundingBox(), phantomObsidianPos);
      return this.selfDamage(dist, exposure);
   }

   private float selfDamage(double dist, double exposure) {
      if (exposure <= 0.0) {
         return 0.0F;
      }

      double impact = (1.0 - dist / 12.0) * exposure;
      if (impact <= 0.0) {
         return 0.0F;
      }

      float dmg = this.baseExplosionDamage(impact);
      float armor = (float)mc.player.getAttributeValue(EntityAttributes.ARMOR);
      float toughness = (float)mc.player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
      dmg = this.armorAbsorb(dmg, armor, toughness);
      dmg = this.selfProtectionAbsorb(dmg);
      return Math.max(dmg, 0.0F);
   }

   private float baseExplosionDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (mc.world.getDifficulty()) {
         case EASY:
            dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
            break;
         case HARD:
            dmg *= 1.5F;
      }

      return dmg;
   }

   private boolean selfDamageOk(Vec3d crystalCenter, BlockPos phantomObsidianPos, double maxSelf, float playerHealth) {
      float optimistic = this.maxDamageSelf(crystalCenter);
      if (optimistic <= maxSelf && optimistic + 1.5F < playerHealth) {
         return true;
      }

      float real = this.calcDamageSelf(crystalCenter, phantomObsidianPos);
      return real > maxSelf ? false : !(real + 1.5F >= playerHealth);
   }

   private float armorAbsorb(float dmg, float armor, float toughness) {
      float i = 2.0F + toughness / 4.0F;
      float j = MathHelper.clamp(armor - dmg / i, armor * 0.2F, 20.0F);
      return dmg * (1.0F - j / 25.0F);
   }

   private float selfProtectionAbsorb(float dmg) {
      int epf = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = mc.player.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            epf += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            epf += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
         }
      }

      if (epf > 20) {
         epf = 20;
      }

      return DamageUtil.getInflictedDamage(dmg, epf);
   }

   private double calcExposure(Vec3d source, Box box, BlockPos phantomObsidianPos) {
      double dx = box.getLengthX();
      double dy = box.getLengthY();
      double dz = box.getLengthZ();
      int steps = 2;
      int total = 0;
      int unblocked = 0;
      AutoCrystalModule.ExposureContext ctx = this.exposureCtx;
      BiFunction<AutoCrystalModule.ExposureContext, BlockPos, BlockHitResult> tester = (c, pos) -> {
         Vec3d from = c.start;
         Vec3d to = c.end;
         if (phantomObsidianPos != null && pos.equals(phantomObsidianPos)) {
            return VoxelShapes.fullCube().raycast(from, to, pos);
         }

         if (!this.seeThrough.isEmpty() && this.seeThrough.contains(pos)) {
            return null;
         }

         BlockState state = this.getStateFast(pos);
         if (state.isAir()) {
            return null;
         }

         if (state.getBlock().getBlastResistance() < 600.0F) {
            return null;
         }

         VoxelShape shape = state.getCollisionShape(mc.world, pos);
         return shape.isEmpty() ? null : shape.raycast(from, to, pos);
      };

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               Vec3d point = new Vec3d(box.minX + dx * xi / steps, box.minY + dy * yi / steps, box.minZ + dz * zi / steps);
               ctx.set(point, source);
               if (BlockView.raycast(point, source, ctx, tester, c -> null) == null) {
                  unblocked++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unblocked / total;
   }

   private double getDynamicMin(float health, float absorption, boolean armorBroken) {
      double min = this.minDamage.getValue();
      float hp = health + absorption;
      if (hp / 36.0F <= 0.2) {
         min /= 4.0;
      }

      if (armorBroken) {
         min /= 4.0;
      }

      return min;
   }

   private boolean isAnyArmorBroken(PlayerEntity player) {
      int threshold = 20;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = player.getEquippedStack(slot);
         if (!stack.isEmpty() && stack.getMaxDamage() > 0) {
            int durabilityPct = 100 - stack.getDamage() * 100 / stack.getMaxDamage();
            if (durabilityPct < threshold) {
               return true;
            }
         }
      }

      return false;
   }

   private Vec3d getClosestPointToEye(Vec3d eye, Box box) {
      double x = eye.x;
      double y = eye.y;
      double z = eye.z;
      double VEC = 0.0625;
      double EPS = 1.0E-9;
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

      if (Math.abs(x - box.minX) < 1.0E-9) {
         x = Math.min(box.minX + 0.0625, box.maxX - 1.0E-9);
      } else if (Math.abs(x - box.maxX) < 1.0E-9) {
         x = Math.max(box.maxX - 0.0625, box.minX + 1.0E-9);
      }

      if (Math.abs(z - box.minZ) < 1.0E-9) {
         z = Math.min(box.minZ + 0.0625, box.maxZ - 1.0E-9);
      } else if (Math.abs(z - box.maxZ) < 1.0E-9) {
         z = Math.max(box.maxZ - 0.0625, box.minZ + 1.0E-9);
      }

      return new Vec3d(x, y, z);
   }

   private Vec3d getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3d(g * h, i, f * h);
   }

   public float getLastBestDamage() {
      return this.lastBestDamage;
   }

   public double getMinDamage() {
      return this.minDamage.getValue();
   }

   @Override
   public String getDisplayInfo() {
      return this.lastBestDamage > 0.0F ? String.format("%.1f", this.lastBestDamage) : null;
   }

   @Subscribe
   private void onExplodePacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ExplosionS2CPacket) {
         long now = System.currentTimeMillis();
         this.explodeTimes.addLast(now);

         while (!this.explodeTimes.isEmpty() && now - this.explodeTimes.peekFirst() > 1000L) {
            this.explodeTimes.removeFirst();
         }
      }
   }

   @Override
   public String getMeta() {
      long now = System.currentTimeMillis();

      while (!this.explodeTimes.isEmpty() && now - this.explodeTimes.peekFirst() > 1000L) {
         this.explodeTimes.removeFirst();
      }

      return String.valueOf(this.explodeTimes.size());
   }

   private BlockState getStateFast(BlockPos pos) {
      if (mc.world.isOutOfHeightLimit(pos.getY())) {
         return Blocks.VOID_AIR.getDefaultState();
      }

      int cx = pos.getX() >> 4;
      int cz = pos.getZ() >> 4;
      if (this.cachedChunkX != cx || this.cachedChunkZ != cz || this.cachedChunk == null) {
         this.cachedChunk = mc.world.getChunk(cx, cz);
         this.cachedChunkX = cx;
         this.cachedChunkZ = cz;
      }

      Chunk chunk = this.cachedChunk;
      if (chunk == null) {
         return Blocks.AIR.getDefaultState();
      } else {
         int sectionIdx = mc.world.getSectionIndex(pos.getY());
         ChunkSection[] sections = chunk.getSectionArray();
         if (sectionIdx >= 0 && sectionIdx < sections.length) {
            ChunkSection section = sections[sectionIdx];
            return section != null && !section.isEmpty()
               ? section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)
               : Blocks.AIR.getDefaultState();
         } else {
            return Blocks.AIR.getDefaultState();
         }
      }
   }

   private Vec3d bestReachEye(Box box) {
      Vec3d base = mc.player.getCameraPosVec(1.0F);
      float scale = (float)mc.player.getAttributeValue(EntityAttributes.SCALE);
      double standing = 1.62 * scale;
      double sneaking = 1.27 * scale;
      double swimming = 0.4 * scale;
      Vec3d pos = mc.player.getEntityPos();
      EntityPose pose = mc.player.getPose();

      double[] heights = switch (pose) {
         case GLIDING, SPIN_ATTACK, SWIMMING -> new double[]{swimming, standing, sneaking};
         case CROUCHING -> new double[]{sneaking, standing, swimming};
         default -> new double[]{standing, sneaking, swimming};
      };
      Vec3d bestEye = base;
      double bestSq = sqDistToBox(base, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);

      for (double h : heights) {
         Vec3d e = new Vec3d(pos.x, pos.y + h, pos.z);
         double sq = sqDistToBox(e, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
         if (sq < bestSq) {
            bestSq = sq;
            bestEye = e;
         }
      }

      return bestEye;
   }

   private record BasePlaceTarget(BlockPos base, float damage) {
   }

   private static final class ExposureContext {
      private Vec3d start = Vec3d.ZERO;
      private Vec3d end = Vec3d.ZERO;

      void set(Vec3d start, Vec3d end) {
         this.start = start;
         this.end = end;
      }
   }

   private record PlaceCandidate(BlockPos base, BlockPos airPos, Vec3d crystalCenter, float bound) {
   }

   private record PlaceTarget(BlockPos base, float damage) {
   }

   private enum RenderMode {
      NONE,
      GRADIENT,
      GRADIENT_SMOOTH,
      BOX_SMOOTH,
      PULSE;
   }

   private record TargetCache(LivingEntity entity, Vec3d pos, Box box, float hp, float abs, boolean armorBroken, float armor, float toughness, int protPoints) {
   }
}
