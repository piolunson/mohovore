package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.TargetsModule;
import dev.leonetic.features.modules.world.SpeedMineModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.PlacementManager;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.DamageSyncTracker;
import dev.leonetic.util.EnchantmentUtil;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.models.Timer;
import dev.leonetic.util.player.ChatUtil;
import dev.leonetic.util.player.EatUtil;
import dev.leonetic.util.render.RenderUtil;
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
import net.minecraft.class_1268;
import net.minecraft.class_1280;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1511;
import net.minecraft.class_1542;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1893;
import net.minecraft.class_1922;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_259;
import net.minecraft.class_2604;
import net.minecraft.class_265;
import net.minecraft.class_2663;
import net.minecraft.class_2664;
import net.minecraft.class_2680;
import net.minecraft.class_2791;
import net.minecraft.class_2824;
import net.minecraft.class_2826;
import net.minecraft.class_2879;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_4050;
import net.minecraft.class_4587;
import net.minecraft.class_5134;
import net.minecraft.class_2338.class_2339;

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
   private class_2338 renderPos = null;
   private long renderStartMs = 0L;
   private class_238 smoothBox = null;
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
   private class_2338 lastPlaceAir = null;
   private final AutoCrystalModule.ExposureContext exposureCtx = new AutoCrystalModule.ExposureContext();
   private final ArrayList<AutoCrystalModule.PlaceCandidate> candidates = new ArrayList<>();
   private final Set<Integer> deadIds = ConcurrentHashMap.newKeySet();
   private final Set<class_2338> seeThrough = new HashSet<>();
   private int cachedChunkX = Integer.MIN_VALUE;
   private int cachedChunkZ = Integer.MIN_VALUE;
   private class_2791 cachedChunk;
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

   private void markRender(class_2338 crystalPos) {
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

   private void updateSmoothBox(class_238 target) {
      if (this.smoothBox == null) {
         this.smoothBox = target;
      } else {
         double f = 1.0 / this.smoothness.getValue().intValue();
         this.smoothBox = new class_238(
            lerp(this.smoothBox.field_1323, target.field_1323, f),
            lerp(this.smoothBox.field_1322, target.field_1322, f),
            lerp(this.smoothBox.field_1321, target.field_1321, f),
            lerp(this.smoothBox.field_1320, target.field_1320, f),
            lerp(this.smoothBox.field_1325, target.field_1325, f),
            lerp(this.smoothBox.field_1324, target.field_1324, f)
         );
      }
   }

   private void drawBoxSmooth(Render3DEvent event) {
      this.updateSmoothBox(new class_238(this.renderPos.method_10074()));
      RenderUtil.drawBoxFilled(event.getMatrix(), this.smoothBox, this.sideColor.getValue());
      RenderUtil.drawBox(event.getMatrix(), this.smoothBox, this.lineColor.getValue(), this.lineWidth.getValue());
   }

   private void drawGradient(Render3DEvent event, boolean smooth) {
      Color side = this.sideColor.getValue();
      Color line = this.lineColor.getValue();
      Color clear = new Color(side.getRed(), side.getGreen(), side.getBlue(), 0);
      double height = this.gradientHeight.getValue();
      float lw = this.lineWidth.getValue();
      class_2338 basePos = this.renderPos.method_10074();
      double x1;
      double z1;
      double x2;
      double z2;
      double yTop;
      if (smooth) {
         this.updateSmoothBox(new class_238(basePos));
         x1 = this.smoothBox.field_1323;
         z1 = this.smoothBox.field_1321;
         x2 = this.smoothBox.field_1320;
         z2 = this.smoothBox.field_1324;
         yTop = this.smoothBox.field_1325;
      } else {
         x1 = basePos.method_10263();
         z1 = basePos.method_10260();
         x2 = x1 + 1.0;
         z2 = z1 + 1.0;
         yTop = basePos.method_10264() + 1;
      }

      double yBottom = yTop - height;
      class_4587 m = event.getMatrix();
      RenderUtil.quadHorizontal(m, x1, z1, x2, z2, yTop, side);
      RenderUtil.gradientQuadVertical(m, x1, z1, x2, z1, yTop, yBottom, side, clear);
      RenderUtil.gradientQuadVertical(m, x2, z1, x2, z2, yTop, yBottom, side, clear);
      RenderUtil.gradientQuadVertical(m, x2, z2, x1, z2, yTop, yBottom, side, clear);
      RenderUtil.gradientQuadVertical(m, x1, z2, x1, z1, yTop, yBottom, side, clear);
      RenderUtil.drawLine(new class_243(x1, yTop, z1), new class_243(x2, yTop, z1), line, lw);
      RenderUtil.drawLine(new class_243(x2, yTop, z1), new class_243(x2, yTop, z2), line, lw);
      RenderUtil.drawLine(new class_243(x2, yTop, z2), new class_243(x1, yTop, z2), line, lw);
      RenderUtil.drawLine(new class_243(x1, yTop, z2), new class_243(x1, yTop, z1), line, lw);
      RenderUtil.drawLine(new class_243(x1, yTop, z1), new class_243(x1, yBottom, z1), line, lw);
      RenderUtil.drawLine(new class_243(x2, yTop, z1), new class_243(x2, yBottom, z1), line, lw);
      RenderUtil.drawLine(new class_243(x2, yTop, z2), new class_243(x2, yBottom, z2), line, lw);
      RenderUtil.drawLine(new class_243(x1, yTop, z2), new class_243(x1, yBottom, z2), line, lw);
   }

   private void drawPulse(Render3DEvent event, long elapsed, long life) {
      double fade = 1.0 - (double)elapsed / life;
      double phase = elapsed / 1000.0 * this.pulseSpeed.getValue();
      double pulse = (Math.sin(phase * Math.PI * 2.0) + 1.0) * 0.5;
      double grow = pulse * this.pulseExpand.getValue();
      Color base = this.pulseColor.getValue();
      Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int)(base.getAlpha() * fade), 0, 255));
      Color line = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int)(255.0 * fade), 0, 255));
      class_238 box = new class_238(this.renderPos).method_1014(grow);
      RenderUtil.drawBoxFilled(event.getMatrix(), box, fill);
      RenderUtil.drawBox(event.getMatrix(), box, line, this.lineWidth.getValue());
   }

   private static double lerp(double a, double b, double f) {
      return a + (b - a) * f;
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (mc.field_1687 != null) {
         if (event.getPacket() instanceof class_2663 pkt) {
            if (pkt.method_11470() == 3) {
               class_1297 e = pkt.method_11469(mc.field_1687);
               if (e != null) {
                  this.deadIds.add(e.method_5628());
               }
            }
         } else {
            if (event.getPacket() instanceof class_2604 pkt) {
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

   private void onCrystalSpawn(class_2604 pkt) {
      if (this.doBreak.getValue() && this.breakDelay.getValue() == 0) {
         if (pkt.method_11169() == class_1299.field_6110) {
            if (!nullCheck() && !mc.field_1724.method_29504()) {
               if (!EatUtil.shouldDefer()) {
                  AutoSwordModule sword = Homovore.moduleManager.getModuleByClass(AutoSwordModule.class);
                  if (sword == null || !sword.isEnabled() || !sword.isMaceAttackReady()) {
                     class_1511 crystal = new class_1511(class_1299.field_6110, mc.field_1687);
                     crystal.method_5814(pkt.method_11175(), pkt.method_11174(), pkt.method_11176());
                     crystal.method_5838(pkt.method_11167());
                     class_238 bb = crystal.method_5829();
                     class_243 reachEye = this.bestReachEye(bb);
                     if (!(sqDistToBox(reachEye, bb.field_1323, bb.field_1322, bb.field_1321, bb.field_1320, bb.field_1325, bb.field_1324) > 9.0)) {
                        float optimisticSelf = this.maxDamageSelf(crystal.method_73189());
                        float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
                        if (!(optimisticSelf > this.maxSelfDamage.getValue()) && !(optimisticSelf + 1.5F >= playerHealth)) {
                           float serverYaw = Homovore.rotationManager.getServerYaw();
                           float serverPitch = Homovore.rotationManager.getServerPitch();
                           if (this.canBreakCrystal(crystal, serverYaw, serverPitch)) {
                              this.diagInstaBreak++;
                              mc.field_1724.field_3944.method_52787(class_2824.method_34206(crystal, mc.field_1724.method_5715()));
                              if (this.swing.getValue()) {
                                 mc.field_1724.field_3944.method_52787(new class_2879(class_1268.field_5808));
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

   private void onBlockUpdate(class_2338 pos, boolean nowAir) {
      if (nowAir) {
         if (!nullCheck() && !mc.field_1724.method_29504()) {
            if (!EatUtil.shouldDefer()) {
               AutoSwordModule sword = Homovore.moduleManager.getModuleByClass(AutoSwordModule.class);
               if (sword == null || !sword.isEnabled() || !sword.isMaceAttackReady()) {
                  double cutoff = 64.0;
                  if (!(mc.field_1724.method_33571().method_1025(class_243.method_24953(pos)) > cutoff)) {
                     this.updateSeeThrough();
                     TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
                     double scanRadius = 18.0;
                     List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, scanRadius);
                     if (this.doBreak.getValue() && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                        class_1511 target = this.findBestCrystal(potentialTargets);
                        if (target != null) {
                           this.breakCrystal(target);
                           this.breakTimer.reset();
                           return;
                        }
                     }

                     if (this.place.getValue() && this.placeTimer.passedMs(this.placeDelay.getValue().intValue())) {
                        if (mc.field_1724.field_6012 != this.lastReactorPlaceTick) {
                           this.lastReactorPlaceTick = mc.field_1724.field_6012;
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
      if (!nullCheck() && !mc.field_1724.method_29504()) {
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
                  class_1511 target = this.findBestCrystal(breakTargetCache);
                  if (target != null) {
                     this.breakCrystal(target);
                     this.breakTimer.reset();
                     broke = true;
                  }
               }

               if (!broke && antiChineseReady && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                  class_1511 acTarget = this.findAntiChineseCrystal();
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
                  ChatUtil.sendPersistent("autocrystal:calc", class_2561.method_43470(String.format("AutoCrystal calc: %.3f ms", this.lastCalcMs)));
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
      class_1309 primary = this.findClosestBaseTarget(targets);
      if (primary == null) {
         return null;
      }

      class_243 eye = mc.field_1724.method_5836(1.0F);
      class_243 playerCenter = mc.field_1724.method_73189();
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      double baseMin = this.basePlaceMinDamage.getValue();
      int targetFeetY = primary.method_24515().method_10264();
      double range = 6.0;
      double rangeSq = range * range;
      int r = (int)Math.ceil(range);
      class_2338 playerPos = mc.field_1724.method_24515();
      int topLayerY = targetFeetY - 1;
      int bottomLayerY = playerPos.method_10264() - r;
      int rr = r * r;

      for (int by = topLayerY; by >= bottomLayerY; by--) {
         class_2338 layerBest = null;
         float layerDmg = 0.0F;
         int dy = by - playerPos.method_10264();
         int dyy = dy * dy;
         if (dyy <= rr) {
            for (int dx = -r; dx <= r; dx++) {
               int dxxdyy = dx * dx + dyy;

               for (int dz = -r; dz <= r; dz++) {
                  if (dxxdyy + dz * dz <= rr) {
                     class_2338 basePos = new class_2338(playerPos.method_10263() + dx, by, playerPos.method_10260() + dz);
                     class_2338 crystalPos = basePos.method_10084();
                     class_2680 crystalState = mc.field_1687.method_8320(crystalPos);
                     if ((crystalState.method_26215() || crystalState.method_45474())
                        && !this.isBlocked(crystalPos)
                        && !(playerCenter.method_1028(basePos.method_10263() + 0.5, basePos.method_10264() + 0.5, basePos.method_10260() + 0.5) > rangeSq)
                        && PlaceUtil.canPlace(basePos)
                        && !(
                           sqDistToBox(
                                 eye,
                                 crystalPos.method_10263(),
                                 crystalPos.method_10264(),
                                 crystalPos.method_10260(),
                                 crystalPos.method_10263() + 1,
                                 crystalPos.method_10264() + 1,
                                 crystalPos.method_10260() + 1
                              )
                              > 36.0
                        )) {
                        class_243 crystalCenter = new class_243(crystalPos.method_10263() + 0.5, crystalPos.method_10264(), crystalPos.method_10260() + 0.5);
                        double cMinX = crystalCenter.field_1352 - 1.0;
                        double cMinZ = crystalCenter.field_1350 - 1.0;
                        double cMaxX = crystalCenter.field_1352 + 1.0;
                        double cMaxY = crystalCenter.field_1351 + 2.0;
                        double cMaxZ = crystalCenter.field_1350 + 1.0;
                        class_243 breakEye = this.bestReachEye(new class_238(cMinX, crystalCenter.field_1351, cMinZ, cMaxX, cMaxY, cMaxZ));
                        if (!(sqDistToBox(breakEye, cMinX, crystalCenter.field_1351, cMinZ, cMaxX, cMaxY, cMaxZ) > 9.0)
                           && anyTargetWithin(potentialTargets, crystalCenter, 144.0)) {
                           float bound = 0.0F;
                           boolean anyBound = false;
                           int i = 0;

                           for (int n = potentialTargets.size(); i < n; i++) {
                              AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
                              if (!(tc.pos.method_1025(crystalCenter) > 144.0)) {
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
                                 if (!(tc.pos.method_1025(crystalCenter) > 144.0)) {
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
      Result obs = InventoryUtil.find(class_1802.field_8281, InventoryUtil.HOTBAR_SCOPE);
      return obs.found() && obs.type() != ResultType.OFFHAND ? Homovore.placementManager.enqueue(target.base, class_1802.field_8281) : false;
   }

   private class_1309 findClosestBaseTarget(TargetsModule targets) {
      double range = this.basePlaceTargetRange.getValue();
      double rangeSq = range * range;
      class_1309 best = null;
      double bestDist = Double.MAX_VALUE;
      class_238 area = mc.field_1724.method_5829().method_1014(range);

      for (class_1297 e : mc.field_1687.method_8335(mc.field_1724, area)) {
         if (e instanceof class_1657 p && (targets == null || targets.isValidPlayerTarget(p))) {
            double d = mc.field_1724.method_5858(p);
            if (!(d > rangeSq) && d < bestDist) {
               bestDist = d;
               best = p;
            }
         }
      }

      return best;
   }

   private class_1511 findBestCrystal(List<AutoCrystalModule.TargetCache> potentialTargets) {
      class_1511 best = null;
      float bestDmg = 0.0F;
      float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      double maxSelf = this.maxSelfDamage.getValue();
      double breakRangeSq = 9.0;
      class_238 searchArea = mc.field_1724.method_5829().method_1014(5.0);

      for (class_1297 e : mc.field_1687.method_8335(null, searchArea)) {
         if (e instanceof class_1511 crystal) {
            class_238 bb = crystal.method_5829();
            class_243 reachEye = this.bestReachEye(bb);
            if (!(sqDistToBox(reachEye, bb.field_1323, bb.field_1322, bb.field_1321, bb.field_1320, bb.field_1325, bb.field_1324) > breakRangeSq)) {
               class_243 cp = crystal.method_73189();
               if (anyTargetWithin(potentialTargets, cp, 144.0) && this.selfDamageOk(cp, null, maxSelf, playerHealth)) {
                  float totalDmg = 0.0F;
                  boolean anyTarget = false;

                  for (AutoCrystalModule.TargetCache tc : potentialTargets) {
                     if (!(tc.pos.method_1025(cp) > 144.0) && (!this.damageSync.getValue() || !DamageSyncTracker.shouldHold(tc.entity))) {
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

   private void breakCrystal(class_1511 crystal) {
      class_243 eyePos = mc.field_1724.method_5836(1.0F);
      class_243 hit = this.getClosestPointToEye(eyePos, crystal.method_5829());
      float[] angles = MathUtil.calcAngle(eyePos, hit);
      if (this.canBreakCrystal(crystal, angles[0], angles[1])) {
         Homovore.rotationManager.submit(new RotationRequest("AutoCrystal_break", 60, angles[0], angles[1], RotationRequest.Mode.SILENT));
         this.diagBreakSent++;
         mc.field_1761.method_2918(mc.field_1724, crystal);
         if (this.swing.getValue()) {
            mc.field_1724.method_6104(class_1268.field_5808);
         }
      }
   }

   private boolean canBreakCrystal(class_1511 crystal, float yaw, float pitch) {
      class_238 bb = crystal.method_5829();
      class_243 eyePos = this.bestReachEye(bb);
      if (bb.method_1006(eyePos)) {
         return true;
      }

      class_243 look = this.getLookVector(yaw, pitch);
      class_243 reachEnd = eyePos.method_1019(look.method_1021(3.0));
      return bb.method_992(eyePos, reachEnd).isPresent();
   }

   private AutoCrystalModule.PlaceTarget findBestPlace(List<AutoCrystalModule.TargetCache> potentialTargets) {
      class_243 eyePos = mc.field_1724.method_5836(1.0F);
      class_2338 playerPos = mc.field_1724.method_24515();
      int r = (int)Math.ceil(6.0);
      long now = System.currentTimeMillis();
      boolean isEnd = mc.field_1687.method_27983().equals(class_1937.field_25181);
      double placeRangeSq = 36.0;
      double breakRangeSq = 9.0;
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      class_2339 cursor = new class_2339();
      int rr = r * r;
      this.candidates.clear();

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.method_10103(playerPos.method_10263() + x, playerPos.method_10264() + y, playerPos.method_10260() + z);
                  class_2680 baseState = mc.field_1687.method_8320(cursor);
                  if (baseState.method_27852(class_2246.field_10540) || baseState.method_27852(class_2246.field_9987)) {
                     int bx = cursor.method_10263();
                     int by = cursor.method_10264();
                     int bz = cursor.method_10260();
                     int ay = by + 1;
                     cursor.method_10103(bx, ay, bz);
                     class_2680 airState = mc.field_1687.method_8320(cursor);
                     if ((airState.method_26215() || airState.method_27852(class_2246.field_10036) && isEnd)
                        && !(sqDistToBox(eyePos, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq)) {
                        double cx = bx + 0.5;
                        double cy = by + 1.0;
                        double cz = bz + 0.5;
                        class_243 breakEye = this.bestReachEye(new class_238(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0));
                        if (!(sqDistToBox(breakEye, cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0) > breakRangeSq)) {
                           class_243 crystalCenter = new class_243(cx, cy, cz);
                           float boundTotal = 0.0F;
                           boolean anyTarget = false;
                           int i = 0;

                           for (int n = potentialTargets.size(); i < n; i++) {
                              AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
                              if (!(tc.pos.method_1025(crystalCenter) > 144.0)) {
                                 float dmg = this.maxDamage(tc, crystalCenter);
                                 if (!(dmg < this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken))) {
                                    boundTotal += dmg;
                                    anyTarget = true;
                                 }
                              }
                           }

                           if (anyTarget) {
                              this.candidates
                                 .add(new AutoCrystalModule.PlaceCandidate(new class_2338(bx, by, bz), new class_2338(bx, ay, bz), crystalCenter, boundTotal));
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
      class_2338 locked = null;
      if (this.lastPlaceAir != null) {
         long lockTs = this.crystalPlaces.get(this.lastPlaceAir.method_10063());
         boolean pending = lockTs != 0L && now - lockTs < 40L;
         if (!pending && !this.hasLiveCrystalAt(this.lastPlaceAir)) {
            this.lastPlaceAir = null;
         } else {
            double cx = this.lastPlaceAir.method_10263() + 0.5;
            double cy = this.lastPlaceAir.method_10264();
            double cz = this.lastPlaceAir.method_10260() + 0.5;
            class_243 lockEye = this.bestReachEye(new class_238(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0));
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
            long pendingTs = this.crystalPlaces.get(c.airPos.method_10063());
            if ((pendingTs == 0L || now - pendingTs >= 40L) && this.selfDamageOk(c.crystalCenter, null, maxSelf, playerHealth)) {
               float totalDmg = 0.0F;
               boolean anyTarget = false;
               int j = 0;

               for (int m = potentialTargets.size(); j < m; j++) {
                  AutoCrystalModule.TargetCache tc = potentialTargets.get(j);
                  if (!(tc.pos.method_1025(c.crystalCenter) > 144.0)) {
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

   private boolean isBurrowBlock(class_2680 state) {
      return state.method_27852(class_2246.field_10002)
         || state.method_27852(class_2246.field_10560)
         || state.method_27852(class_2246.field_10615)
         || state.method_27852(class_2246.field_10379)
         || state.method_27852(class_2246.field_10008);
   }

   private boolean nearBurrowBlock(class_243 center) {
      int r = (int)Math.ceil(3.0);
      double radSq = 9.0;
      double playerRangeSq = this.antiChineseRange.getValue() * this.antiChineseRange.getValue();
      class_243 playerPos = mc.field_1724.method_73189();
      int cx = class_3532.method_15357(center.field_1352);
      int cy = class_3532.method_15357(center.field_1351);
      int cz = class_3532.method_15357(center.field_1350);
      class_2339 cursor = new class_2339();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               int bx = cx + dx;
               int by = cy + dy;
               int bz = cz + dz;
               double ox = bx + 0.5 - center.field_1352;
               double oy = by + 0.5 - center.field_1351;
               double oz = bz + 0.5 - center.field_1350;
               if (!(ox * ox + oy * oy + oz * oz > radSq) && !(playerPos.method_1028(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq)) {
                  cursor.method_10103(bx, by, bz);
                  if (this.isBurrowBlock(this.getStateFast(cursor))) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean adjacentToBurrowBlock(class_2338 airPos) {
      double playerRangeSq = this.antiChineseRange.getValue() * this.antiChineseRange.getValue();
      class_243 playerPos = mc.field_1724.method_73189();
      class_2339 cursor = new class_2339();

      for (int[] o : FACE_OFFSETS) {
         int bx = airPos.method_10263() + o[0];
         int by = airPos.method_10264() + o[1];
         int bz = airPos.method_10260() + o[2];
         if (!(playerPos.method_1028(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq)) {
            cursor.method_10103(bx, by, bz);
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
      class_238 area = mc.field_1724.method_5829().method_1014(range);

      for (class_1297 e : mc.field_1687.method_8335(mc.field_1724, area)) {
         if (e instanceof class_1657 p && (targets == null || targets.isValidPlayerTarget(p)) && mc.field_1724.method_5858(p) <= rangeSq) {
            return true;
         }
      }

      return false;
   }

   private AutoCrystalModule.PlaceTarget findAntiChinesePlace() {
      class_243 eyePos = mc.field_1724.method_5836(1.0F);
      class_2338 playerPos = mc.field_1724.method_24515();
      int r = (int)Math.ceil(6.0);
      long now = System.currentTimeMillis();
      boolean isEnd = mc.field_1687.method_27983().equals(class_1937.field_25181);
      double placeRangeSq = 36.0;
      double breakRangeSq = 9.0;
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      class_2339 cursor = new class_2339();
      int rr = r * r;
      AutoCrystalModule.PlaceTarget best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.method_10103(playerPos.method_10263() + x, playerPos.method_10264() + y, playerPos.method_10260() + z);
                  class_2680 baseState = mc.field_1687.method_8320(cursor);
                  if (baseState.method_27852(class_2246.field_10540) || baseState.method_27852(class_2246.field_9987)) {
                     int bx = cursor.method_10263();
                     int by = cursor.method_10264();
                     int bz = cursor.method_10260();
                     int ay = by + 1;
                     cursor.method_10103(bx, ay, bz);
                     class_2680 airState = mc.field_1687.method_8320(cursor);
                     if ((airState.method_26215() || airState.method_27852(class_2246.field_10036) && isEnd)
                        && !(sqDistToBox(eyePos, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq)) {
                        double cx = bx + 0.5;
                        double cy = by + 1.0;
                        double cz = bz + 0.5;
                        class_243 breakEye = this.bestReachEye(new class_238(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0));
                        if (!(sqDistToBox(breakEye, cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0) > breakRangeSq)) {
                           class_243 crystalCenter = new class_243(cx, cy, cz);
                           double dSq = eyePos.method_1025(crystalCenter);
                           if (!(dSq >= bestDistSq)) {
                              class_2338 airPos = new class_2338(bx, ay, bz);
                              if (this.adjacentToBurrowBlock(airPos) && !this.isBlocked(airPos)) {
                                 long pendingTs = this.crystalPlaces.get(airPos.method_10063());
                                 if ((pendingTs == 0L || now - pendingTs >= 40L) && this.selfDamageOk(crystalCenter, null, maxSelf, playerHealth)) {
                                    bestDistSq = dSq;
                                    best = new AutoCrystalModule.PlaceTarget(new class_2338(bx, by, bz), 0.0F);
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

   private class_1511 findAntiChineseCrystal() {
      float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      double maxSelf = this.maxSelfDamage.getValue();
      double breakRangeSq = 9.0;
      class_1511 best = null;
      double bestDist = Double.MAX_VALUE;
      class_238 searchArea = mc.field_1724.method_5829().method_1014(5.0);

      for (class_1297 e : mc.field_1687.method_8335(null, searchArea)) {
         if (e instanceof class_1511 crystal) {
            class_238 bb = crystal.method_5829();
            class_243 reachEye = this.bestReachEye(bb);
            if (!(sqDistToBox(reachEye, bb.field_1323, bb.field_1322, bb.field_1321, bb.field_1320, bb.field_1325, bb.field_1324) > breakRangeSq)) {
               class_243 cp = crystal.method_73189();
               if (this.nearBurrowBlock(cp) && this.selfDamageOk(cp, null, maxSelf, playerHealth)) {
                  double d = mc.field_1724.method_5858(crystal);
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
      class_238 area = mc.field_1724.method_5829().method_1014(scanRadius);

      for (class_1297 e : mc.field_1687.method_8335(mc.field_1724, area)) {
         if (e instanceof class_1309 living
            && !living.method_29504()
            && !this.deadIds.contains(e.method_5628())
            && (targets == null || targets.isValidPlayerTarget(e))) {
            boolean armorBroken = e instanceof class_1657 p && this.isAnyArmorBroken(p);
            float armor = (float)living.method_45325(class_5134.field_23724);
            float toughness = (float)living.method_45325(class_5134.field_23725);
            int protPoints = 0;
            if (!living.method_6118(class_1304.field_6169).method_7960()) {
               protPoints += 4;
            }

            if (!living.method_6118(class_1304.field_6174).method_7960()) {
               protPoints += 4;
            }

            if (!living.method_6118(class_1304.field_6172).method_7960()) {
               protPoints += 8;
            }

            if (!living.method_6118(class_1304.field_6166).method_7960()) {
               protPoints += 4;
            }

            out.add(
               new AutoCrystalModule.TargetCache(
                  living, living.method_73189(), living.method_5829(), living.method_6032(), living.method_6067(), armorBroken, armor, toughness, protPoints
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

   private static boolean anyTargetWithin(List<AutoCrystalModule.TargetCache> cache, class_243 center, double maxDistSq) {
      int i = 0;

      for (int n = cache.size(); i < n; i++) {
         if (cache.get(i).pos.method_1025(center) <= maxDistSq) {
            return true;
         }
      }

      return false;
   }

   private static double sqDistToBox(class_243 p, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = p.field_1352 < minX ? minX - p.field_1352 : (p.field_1352 > maxX ? p.field_1352 - maxX : 0.0);
      double dy = p.field_1351 < minY ? minY - p.field_1351 : (p.field_1351 > maxY ? p.field_1351 - maxY : 0.0);
      double dz = p.field_1350 < minZ ? minZ - p.field_1350 : (p.field_1350 > maxZ ? p.field_1350 - maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private boolean isBlocked(class_2338 airPos) {
      for (class_1297 e : mc.field_1687.method_8335(null, new class_238(airPos))) {
         if (!(e instanceof class_1542)
            && (!(e instanceof class_1511 crystal) || crystal.field_6012 >= 5)
            && (!(e instanceof class_1511 crystal) || !crystal.method_24515().equals(airPos))) {
            return true;
         }
      }

      return false;
   }

   private void doPlace(AutoCrystalModule.PlaceTarget target) {
      this.doPlace(target, false);
   }

   private void doPlace(AutoCrystalModule.PlaceTarget target, boolean trustBase) {
      if (!InventoryUtil.find(class_1802.field_8301, EnumSet.of(ResultType.HOTBAR)).found()) {
         this.lastBestDamage = 0.0F;
      } else {
         this.lastBestDamage = target.damage;
         this.diagPlaceAttempt++;
         if (Homovore.placementManager.placeCrystal(target.base, trustBase)) {
            this.diagPlaceSent++;
            this.recordPlace(target.base.method_10084());
         }
      }
   }

   private void recordPlace(class_2338 airPos) {
      this.crystalPlaces.put(airPos.method_10063(), System.currentTimeMillis());
      this.markRender(airPos);
      this.lastPlaceAir = airPos;
   }

   private boolean hasLiveCrystalAt(class_2338 airPos) {
      for (class_1297 e : mc.field_1687.method_8335(null, new class_238(airPos))) {
         if (e instanceof class_1511 && e.method_24515().equals(airPos)) {
            return true;
         }
      }

      return false;
   }

   public boolean preplaceCrystal(class_2338 airPos, boolean snap) {
      if (!nullCheck() && this.place.getValue()) {
         class_2338 base = airPos.method_10074();
         class_2680 baseState = mc.field_1687.method_8320(base);
         if (!baseState.method_27852(class_2246.field_10540) && !baseState.method_27852(class_2246.field_9987)) {
            return false;
         }

         if (this.isBlocked(airPos)) {
            return false;
         }

         long now = System.currentTimeMillis();
         long pendingTs = this.crystalPlaces.get(airPos.method_10063());
         if (pendingTs != 0L && now - pendingTs < 40L) {
            return false;
         }

         this.doPlace(new AutoCrystalModule.PlaceTarget(base, 0.0F), false);
         return this.crystalPlaces.get(airPos.method_10063()) != pendingTs;
      } else {
         return false;
      }
   }

   public boolean isDesirablePlacement(class_2338 airPos) {
      if (nullCheck()) {
         return false;
      }

      class_2338 base = airPos.method_10074();
      class_2680 baseState = mc.field_1687.method_8320(base);
      if (!baseState.method_27852(class_2246.field_10540) && !baseState.method_27852(class_2246.field_9987)) {
         return false;
      }

      int bx = airPos.method_10263();
      int by = airPos.method_10264();
      int bz = airPos.method_10260();
      class_243 eye = mc.field_1724.method_5836(1.0F);
      if (sqDistToBox(eye, bx, by, bz, bx + 1, by + 1, bz + 1) > 36.0) {
         return false;
      }

      double cx = bx + 0.5;
      double cy = by;
      double cz = bz + 0.5;
      if (sqDistToBox(
            this.bestReachEye(new class_238(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0)), cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0
         )
         > 9.0) {
         return false;
      }

      class_243 crystalCenter = new class_243(cx, cy, cz);
      float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      if (!this.selfDamageOk(crystalCenter, null, this.maxSelfDamage.getValue(), playerHealth)) {
         return false;
      }

      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, 18.0);
      int i = 0;

      for (int n = potentialTargets.size(); i < n; i++) {
         AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
         if (!(tc.pos.method_1025(crystalCenter) > 144.0)) {
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

   private boolean isFeetAdjacent(class_243 targetPos, class_2338 airPos) {
      class_2338 feet = class_2338.method_49637(targetPos.field_1352, targetPos.field_1351, targetPos.field_1350);
      if (feet.method_10264() != airPos.method_10264()) {
         return false;
      }

      int dx = airPos.method_10263() - feet.method_10263();
      int dz = airPos.method_10260() - feet.method_10260();
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

      if (mc.field_1724 != null && mc.field_1724.field_6012 % 100 == 0) {
         this.deadIds.clear();
      }
   }

   private float calcDamage(AutoCrystalModule.TargetCache tc, class_243 explosionPos) {
      return this.calcDamage(tc, explosionPos, null);
   }

   private float calcDamage(AutoCrystalModule.TargetCache tc, class_243 explosionPos, class_2338 phantomObsidianPos) {
      double dist = tc.pos.method_1022(explosionPos);
      if (dist > 12.0) {
         return 0.0F;
      }

      double exposure = this.calcExposure(explosionPos, tc.box, phantomObsidianPos);
      return this.targetDamage(tc, dist, exposure);
   }

   private float maxDamage(AutoCrystalModule.TargetCache tc, class_243 explosionPos) {
      double dist = tc.pos.method_1022(explosionPos);
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
      dmg = class_1280.method_5497(dmg, tc.protPoints);
      return Math.max(dmg, 0.0F);
   }

   private float maxDamageSelf(class_243 explosionPos) {
      double dist = mc.field_1724.method_73189().method_1022(explosionPos);
      return dist > 12.0 ? 0.0F : this.selfDamage(dist, 1.0);
   }

   private float calcDamageSelf(class_243 explosionPos, class_2338 phantomObsidianPos) {
      double dist = mc.field_1724.method_73189().method_1022(explosionPos);
      if (dist > 12.0) {
         return 0.0F;
      }

      double exposure = this.calcExposure(explosionPos, mc.field_1724.method_5829(), phantomObsidianPos);
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
      float armor = (float)mc.field_1724.method_45325(class_5134.field_23724);
      float toughness = (float)mc.field_1724.method_45325(class_5134.field_23725);
      dmg = this.armorAbsorb(dmg, armor, toughness);
      dmg = this.selfProtectionAbsorb(dmg);
      return Math.max(dmg, 0.0F);
   }

   private float baseExplosionDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (mc.field_1687.method_8407()) {
         case field_5805:
            dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
            break;
         case field_5807:
            dmg *= 1.5F;
      }

      return dmg;
   }

   private boolean selfDamageOk(class_243 crystalCenter, class_2338 phantomObsidianPos, double maxSelf, float playerHealth) {
      float optimistic = this.maxDamageSelf(crystalCenter);
      if (optimistic <= maxSelf && optimistic + 1.5F < playerHealth) {
         return true;
      }

      float real = this.calcDamageSelf(crystalCenter, phantomObsidianPos);
      return real > maxSelf ? false : !(real + 1.5F >= playerHealth);
   }

   private float armorAbsorb(float dmg, float armor, float toughness) {
      float i = 2.0F + toughness / 4.0F;
      float j = class_3532.method_15363(armor - dmg / i, armor * 0.2F, 20.0F);
      return dmg * (1.0F - j / 25.0F);
   }

   private float selfProtectionAbsorb(float dmg) {
      int epf = 0;

      for (class_1304 slot : new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166}) {
         class_1799 stack = mc.field_1724.method_6118(slot);
         if (!stack.method_7960()) {
            epf += EnchantmentUtil.getLevel(class_1893.field_9111, stack);
            epf += 2 * EnchantmentUtil.getLevel(class_1893.field_9107, stack);
         }
      }

      if (epf > 20) {
         epf = 20;
      }

      return class_1280.method_5497(dmg, epf);
   }

   private double calcExposure(class_243 source, class_238 box, class_2338 phantomObsidianPos) {
      double dx = box.method_17939();
      double dy = box.method_17940();
      double dz = box.method_17941();
      int steps = 2;
      int total = 0;
      int unblocked = 0;
      AutoCrystalModule.ExposureContext ctx = this.exposureCtx;
      BiFunction<AutoCrystalModule.ExposureContext, class_2338, class_3965> tester = (c, pos) -> {
         class_243 from = c.start;
         class_243 to = c.end;
         if (phantomObsidianPos != null && pos.equals(phantomObsidianPos)) {
            return class_259.method_1077().method_1092(from, to, pos);
         }

         if (!this.seeThrough.isEmpty() && this.seeThrough.contains(pos)) {
            return null;
         }

         class_2680 state = this.getStateFast(pos);
         if (state.method_26215()) {
            return null;
         }

         if (state.method_26204().method_9520() < 600.0F) {
            return null;
         }

         class_265 shape = state.method_26220(mc.field_1687, pos);
         return shape.method_1110() ? null : shape.method_1092(from, to, pos);
      };

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               class_243 point = new class_243(box.field_1323 + dx * xi / steps, box.field_1322 + dy * yi / steps, box.field_1321 + dz * zi / steps);
               ctx.set(point, source);
               if (class_1922.method_17744(point, source, ctx, tester, c -> null) == null) {
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

   private boolean isAnyArmorBroken(class_1657 player) {
      int threshold = 20;

      for (class_1304 slot : new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166}) {
         class_1799 stack = player.method_6118(slot);
         if (!stack.method_7960() && stack.method_7936() > 0) {
            int durabilityPct = 100 - stack.method_7919() * 100 / stack.method_7936();
            if (durabilityPct < threshold) {
               return true;
            }
         }
      }

      return false;
   }

   private class_243 getClosestPointToEye(class_243 eye, class_238 box) {
      double x = eye.field_1352;
      double y = eye.field_1351;
      double z = eye.field_1350;
      double VEC = 0.0625;
      double EPS = 1.0E-9;
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

      if (Math.abs(x - box.field_1323) < 1.0E-9) {
         x = Math.min(box.field_1323 + 0.0625, box.field_1320 - 1.0E-9);
      } else if (Math.abs(x - box.field_1320) < 1.0E-9) {
         x = Math.max(box.field_1320 - 0.0625, box.field_1323 + 1.0E-9);
      }

      if (Math.abs(z - box.field_1321) < 1.0E-9) {
         z = Math.min(box.field_1321 + 0.0625, box.field_1324 - 1.0E-9);
      } else if (Math.abs(z - box.field_1324) < 1.0E-9) {
         z = Math.max(box.field_1324 - 0.0625, box.field_1321 + 1.0E-9);
      }

      return new class_243(x, y, z);
   }

   private class_243 getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new class_243(g * h, i, f * h);
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
      if (event.getPacket() instanceof class_2664) {
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

   private class_2680 getStateFast(class_2338 pos) {
      if (mc.field_1687.method_31601(pos.method_10264())) {
         return class_2246.field_10243.method_9564();
      }

      int cx = pos.method_10263() >> 4;
      int cz = pos.method_10260() >> 4;
      if (this.cachedChunkX != cx || this.cachedChunkZ != cz || this.cachedChunk == null) {
         this.cachedChunk = mc.field_1687.method_8497(cx, cz);
         this.cachedChunkX = cx;
         this.cachedChunkZ = cz;
      }

      class_2791 chunk = this.cachedChunk;
      if (chunk == null) {
         return class_2246.field_10124.method_9564();
      } else {
         int sectionIdx = mc.field_1687.method_31602(pos.method_10264());
         class_2826[] sections = chunk.method_12006();
         if (sectionIdx >= 0 && sectionIdx < sections.length) {
            class_2826 section = sections[sectionIdx];
            return section != null && !section.method_38292()
               ? section.method_12254(pos.method_10263() & 15, pos.method_10264() & 15, pos.method_10260() & 15)
               : class_2246.field_10124.method_9564();
         } else {
            return class_2246.field_10124.method_9564();
         }
      }
   }

   private class_243 bestReachEye(class_238 box) {
      class_243 base = mc.field_1724.method_5836(1.0F);
      float scale = (float)mc.field_1724.method_45325(class_5134.field_47760);
      double standing = 1.62 * scale;
      double sneaking = 1.27 * scale;
      double swimming = 0.4 * scale;
      class_243 pos = mc.field_1724.method_73189();
      class_4050 pose = mc.field_1724.method_18376();

      double[] heights = switch (pose) {
         case field_18077, field_18080, field_18079 -> new double[]{swimming, standing, sneaking};
         case field_18081 -> new double[]{sneaking, standing, swimming};
         default -> new double[]{standing, sneaking, swimming};
      };
      class_243 bestEye = base;
      double bestSq = sqDistToBox(base, box.field_1323, box.field_1322, box.field_1321, box.field_1320, box.field_1325, box.field_1324);

      for (double h : heights) {
         class_243 e = new class_243(pos.field_1352, pos.field_1351 + h, pos.field_1350);
         double sq = sqDistToBox(e, box.field_1323, box.field_1322, box.field_1321, box.field_1320, box.field_1325, box.field_1324);
         if (sq < bestSq) {
            bestSq = sq;
            bestEye = e;
         }
      }

      return bestEye;
   }

   private record BasePlaceTarget(class_2338 base, float damage) {
   }

   private static final class ExposureContext {
      private class_243 start = class_243.field_1353;
      private class_243 end = class_243.field_1353;

      void set(class_243 start, class_243 end) {
         this.start = start;
         this.end = end;
      }
   }

   private record PlaceCandidate(class_2338 base, class_2338 airPos, class_243 crystalCenter, float bound) {
   }

   private record PlaceTarget(class_2338 base, float damage) {
   }

   private enum RenderMode {
      NONE,
      GRADIENT,
      GRADIENT_SMOOTH,
      BOX_SMOOTH,
      PULSE;
   }

   private record TargetCache(
      class_1309 entity, class_243 pos, class_238 box, float hp, float abs, boolean armorBroken, float armor, float toughness, int protPoints
   ) {
   }
}
