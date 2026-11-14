package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.TargetsModule;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.AnchorDamageUtil;
import dev.leonetic.util.DamageSyncTracker;
import dev.leonetic.util.MathUtil;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.player.EatUtil;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2868;
import net.minecraft.class_2879;
import net.minecraft.class_2885;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_634;
import net.minecraft.class_7202;
import net.minecraft.class_2338.class_2339;

public class AutoAnchorModule extends Module {
   private final Setting<Double> minDamage = this.num("MinDamage", 6.0, 0.0, 36.0).setPage("General");
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 4.0, 0.0, 36.0).setPage("General");
   private static final double PLACE_RANGE = 6.0;
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20).setPage("General");
   private final Setting<Boolean> damageSync = this.bool("DamageSync", false).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 130, 80, 255, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 130, 80, 255, 255).setPage("Render");
   private int lastBurstTick = -1;
   private float lastDamage = 0.0F;
   private class_2338 renderPos = null;
   private long renderStart = 0L;

   public AutoAnchorModule() {
      super("AutoAnchor", "Places, charges and detonates respawn anchors in a single burst.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.lastBurstTick = -1;
      this.lastDamage = 0.0F;
      this.renderPos = null;
   }

   @Override
   public void onDisable() {
      this.lastDamage = 0.0F;
      this.renderPos = null;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.field_1724.method_29504()) {
         if (!mc.field_1687.method_27983().equals(class_1937.field_25180)) {
            if (mc.field_1724.field_7512.field_7763 == 0) {
               int sinceLast = mc.field_1724.field_6012 - this.lastBurstTick;
               if (sinceLast < 0 || sinceLast >= this.delay.getValue()) {
                  if (!EatUtil.shouldDefer()) {
                     int anchorSlot = this.hotbarSlotOf(class_1802.field_23141);
                     int glowSlot = this.hotbarSlotOf(class_1802.field_8801);
                     if (anchorSlot >= 0 && glowSlot >= 0) {
                        List<AnchorDamageUtil.Target> targets = this.collectTargets();
                        if (targets.isEmpty()) {
                           this.lastDamage = 0.0F;
                        } else {
                           AutoAnchorModule.Candidate best = this.findBest(targets);
                           if (best == null) {
                              this.lastDamage = 0.0F;
                           } else {
                              if (this.burst(best.pos, best.needPlace, anchorSlot, glowSlot)) {
                                 this.lastBurstTick = mc.field_1724.field_6012;
                                 this.lastDamage = best.damage;
                                 if (this.render.getValue()) {
                                    this.renderPos = best.pos;
                                    this.renderStart = System.currentTimeMillis();
                                 }
                              }
                           }
                        }
                     } else {
                        this.lastDamage = 0.0F;
                     }
                  }
               }
            }
         }
      }
   }

   private boolean burst(class_2338 pos, boolean needPlace, int anchorSlot, int glowSlot) {
      class_634 conn = mc.method_1562();
      if (conn == null) {
         return false;
      }

      class_3965 placeHit = null;
      if (needPlace) {
         placeHit = Homovore.placementManager.prepareAirPlaceHit(pos);
         if (placeHit == null) {
            return false;
         }
      }

      class_243 eye = mc.field_1724.method_5836(1.0F);
      class_3965 hit = this.anchorHit(pos, eye);
      float[] angles = MathUtil.calcAngle(eye, hit.method_17784());
      Homovore.rotationManager.submit(new RotationRequest("AutoAnchor", 62, angles[0], angles[1], RotationRequest.Mode.SILENT));
      int originalSlot = Homovore.swapManager.serverSlot();
      int returnSlot = this.isGlowstone(originalSlot) ? anchorSlot : originalSlot;
      int currentSlot = originalSlot;
      if (needPlace) {
         if (anchorSlot != currentSlot) {
            conn.method_52787(new class_2868(anchorSlot));
            currentSlot = anchorSlot;
         }

         this.sendUseItemOn(conn, placeHit);
         Homovore.placementManager.notePlacement(pos);
      }

      if (glowSlot != currentSlot) {
         conn.method_52787(new class_2868(glowSlot));
         currentSlot = glowSlot;
      }

      this.sendUseItemOn(conn, hit);
      if (returnSlot != currentSlot) {
         conn.method_52787(new class_2868(returnSlot));
         currentSlot = returnSlot;
      }

      this.sendUseItemOn(conn, hit);
      conn.method_52787(new class_2879(class_1268.field_5808));
      if (currentSlot != originalSlot) {
         conn.method_52787(new class_2868(originalSlot));
      }

      return true;
   }

   private void sendUseItemOn(class_634 conn, class_3965 hit) {
      this.sendUseItemOn(conn, hit, class_1268.field_5808);
   }

   private void sendUseItemOn(class_634 conn, class_3965 hit, class_1268 hand) {
      class_7202 handler = ((ClientLevelAccessor)mc.field_1687).homovore$getBlockStatePredictionHandler().method_41937();

      try {
         conn.method_52787(new class_2885(hand, hit, handler.method_41942()));
      } catch (Throwable var8) {
         if (handler != null) {
            try {
               handler.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (handler != null) {
         handler.close();
      }
   }

   private boolean isGlowstone(int slot) {
      if (slot >= 0 && slot <= 8) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
         return stack.method_31574(class_1802.field_8801);
      } else {
         return false;
      }
   }

   private class_3965 anchorHit(class_2338 pos, class_243 eye) {
      class_243 center = class_243.method_24953(pos);
      double dx = eye.field_1352 - center.field_1352;
      double dy = eye.field_1351 - center.field_1351;
      double dz = eye.field_1350 - center.field_1350;
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      class_2350 face;
      if (ay >= ax && ay >= az) {
         face = dy > 0.0 ? class_2350.field_11036 : class_2350.field_11033;
      } else if (ax >= az) {
         face = dx > 0.0 ? class_2350.field_11034 : class_2350.field_11039;
      } else {
         face = dz > 0.0 ? class_2350.field_11035 : class_2350.field_11043;
      }

      class_243 hitVec = center.method_1031(face.method_10148() * 0.5, face.method_10164() * 0.5, face.method_10165() * 0.5);
      return new class_3965(hitVec, face, pos, false);
   }

   private AutoAnchorModule.Candidate findBest(List<AnchorDamageUtil.Target> targets) {
      class_243 eye = mc.field_1724.method_5836(1.0F);
      class_2338 playerPos = mc.field_1724.method_24515();
      double maxSelf = this.maxSelfDamage.getValue();
      double min = this.minDamage.getValue();
      double rangeSq = 36.0;
      float playerHealth = mc.field_1724.method_6032() + mc.field_1724.method_6067();
      int r = (int)Math.ceil(6.0);
      int rr = r * r;
      AutoAnchorModule.Candidate best = null;
      class_2339 cursor = new class_2339();

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.method_10103(playerPos.method_10263() + x, playerPos.method_10264() + y, playerPos.method_10260() + z);
                  class_243 center = class_243.method_24953(cursor);
                  if (!(eye.method_1025(center) > rangeSq)) {
                     class_2680 state = mc.field_1687.method_8320(cursor);
                     boolean existing = state.method_27852(class_2246.field_23152);
                     class_2338 pos = null;
                     boolean bounded = false;
                     int i = 0;

                     for (int n = targets.size(); i < n; i++) {
                        AnchorDamageUtil.Target target = targets.get(i);
                        if (!(target.pos().method_1022(center) > 10.0) && AnchorDamageUtil.maxDamage(target, center) >= min) {
                           bounded = true;
                           break;
                        }
                     }

                     if (bounded) {
                        if (existing) {
                           pos = cursor.method_10062();
                        } else {
                           if (!state.method_45474() || !PlaceUtil.canPlace(cursor)) {
                              continue;
                           }

                           pos = cursor.method_10062();
                        }

                        float total = 0.0F;
                        boolean any = false;
                        int ix = 0;

                        for (int n = targets.size(); ix < n; ix++) {
                           AnchorDamageUtil.Target target = targets.get(ix);
                           if (!(target.pos().method_1022(center) > 10.0) && (!this.damageSync.getValue() || !DamageSyncTracker.shouldHold(target.entity()))) {
                              float dmg = AnchorDamageUtil.damage(target, center, pos);
                              if (!(dmg <= 0.0F)) {
                                 total += dmg;
                                 any = true;
                              }
                           }
                        }

                        if (any && !(total < min)) {
                           float self = AnchorDamageUtil.selfDamage(center, pos);
                           if ((best == null || !(total <= best.damage)) && !(self > maxSelf) && (!(self > 0.0F) || !(self + 1.5F >= playerHealth))) {
                              best = new AutoAnchorModule.Candidate(pos, total, !existing);
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

   private List<AnchorDamageUtil.Target> collectTargets() {
      TargetsModule targets = Homovore.moduleManager.getModuleByClass(TargetsModule.class);
      List<AnchorDamageUtil.Target> out = new ArrayList<>();
      class_238 area = mc.field_1724.method_5829().method_1014(16.0);

      for (class_1297 e : mc.field_1687.method_8335(mc.field_1724, area)) {
         if (e instanceof class_1309 living && !living.method_29504() && (targets == null || targets.isValidPlayerTarget(e))) {
            out.add(AnchorDamageUtil.Target.of(living));
         }
      }

      return out;
   }

   private int hotbarSlotOf(class_1792 item) {
      Result r = InventoryUtil.find(item, InventoryUtil.HOTBAR_SCOPE);
      return r.found() && r.type() != ResultType.OFFHAND ? r.slot() : -1;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderPos != null) {
         long age = System.currentTimeMillis() - this.renderStart;
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;
         if (age > fadeMs) {
            this.renderPos = null;
         } else {
            double t = age / fadeMs;
            Color fc = this.fillColor.getValue();
            Color oc = this.outlineColor.getValue();
            RenderUtil.drawBoxFilled(event.getMatrix(), this.renderPos, withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
            RenderUtil.drawBox(event.getMatrix(), this.renderPos, withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
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

   private record Candidate(class_2338 pos, float damage, boolean needPlace) {
   }
}
