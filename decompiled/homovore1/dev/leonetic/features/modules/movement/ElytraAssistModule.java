package dev.leonetic.features.modules.movement;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_1294;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_5134;

public class ElytraAssistModule extends Module {
   public static final String ID = "ElytraAssist";
   public static final int PRIORITY = 90;
   private final Setting<Double> lookahead = this.num("Lookahead", 10.0, 10.0, 60.0);
   private final Setting<Double> clearance = this.num("Clearance", 0.15, 0.0, 1.5);
   private final Setting<Double> maxYaw = this.num("MaxYaw", 90.0, 0.0, 90.0);
   private final Setting<Double> react = this.num("React", 20.0, 4.0, 40.0);
   private final Setting<Double> climbBias = this.num("ClimbBias", 3.0, 0.0, 20.0);
   private static final int RELEASE_HYST = 6;
   private static final int COMMIT_TICKS = 3;
   private static final double DESCEND_EPS = 0.5;
   private static final double DRAG_H = 0.99;
   private static final double DRAG_V = 0.98;
   private static final double DEFAULT_GRAVITY = 0.08;
   private static final double[] PITCH_OFFSETS = new double[]{
      0.0, -2.0, -4.0, -6.0, -9.0, -13.0, -18.0, -25.0, -35.0, -50.0, -70.0, -90.0, 2.0, 4.0, 7.0, 11.0, 16.0, 24.0
   };
   private static final double[] YAW_OFFSETS = new double[]{
      0.0, 2.0, -2.0, 4.0, -4.0, 7.0, -7.0, 11.0, -11.0, 16.0, -16.0, 23.0, -23.0, 32.0, -32.0, 45.0, -45.0, 65.0, -65.0, 90.0, -90.0
   };
   private static final double YAW_COST_WEIGHT = 1.5;
   private static final double W_SURVIVAL = 1000.0;
   private static final double W_CONTINUITY = 2.0;
   private static final double SUBSTEP_SIZE = 0.45;
   private static final int MAX_SUBSTEPS = 12;
   private boolean steering;
   private int clearTicks;
   private float lastYaw;
   private float lastPitch;
   private List<double[]> offsets;

   public ElytraAssistModule() {
      super(
         "ElytraAssist",
         "Aim-assist for elytra: nudges your aim the least amount needed to clear obstacles, always preferring to climb over dive.",
         Module.Category.MOVEMENT
      );
   }

   public boolean isSteering() {
      return this.steering;
   }

   @Override
   public void onDisable() {
      this.stop();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && mc.field_1724.method_6128()) {
         class_238 detect = mc.field_1724.method_5829().method_1014(this.clearance.getValue());
         class_243 startVel = mc.field_1724.method_18798();
         double gravity = this.effectiveGravity(startVel);
         int ticks = this.lookahead.getValue().intValue();
         float lookYaw = mc.field_1724.method_36454();
         float lookPitch = mc.field_1724.method_36455();
         int aimSurvival = this.simulateSurvival(lookYaw, lookPitch, detect, startVel, gravity, ticks);
         int engageWithin = (int)Math.min(this.react.getValue(), ticks);
         int releaseWithin = Math.min(ticks, engageWithin + 6);
         boolean clear = this.steering ? aimSurvival >= releaseWithin : aimSurvival >= engageWithin;
         if (clear) {
            if (this.steering && ++this.clearTicks < 3) {
               this.resubmit();
            } else {
               this.stop();
            }
         } else {
            this.clearTicks = 0;
            if (this.steering && this.simulateSurvival(this.lastYaw, this.lastPitch, detect, startVel, gravity, ticks) >= releaseWithin) {
               this.resubmit();
            } else {
               float[] heading = this.chooseHeading(lookYaw, lookPitch, detect, startVel, gravity, ticks);
               this.steerTo(heading[0], heading[1]);
            }
         }
      } else {
         this.stop();
      }
   }

   private float[] chooseHeading(float lookYaw, float lookPitch, class_238 detect, class_243 startVel, double gravity, int ticks) {
      double yawCap = this.maxYaw.getValue();
      boolean committed = this.steering;
      int bestTier = Integer.MAX_VALUE;
      double bestScore = Double.NEGATIVE_INFINITY;
      float bestYaw = lookYaw;
      float bestPitch = lookPitch;

      for (double[] off : this.offsets()) {
         if (!(Math.abs(off[0]) > yawCap)) {
            float yaw = lookYaw + (float)off[0];
            float pitch = class_3532.method_15363(lookPitch + (float)off[1], -90.0F, 90.0F);
            double[] sim = this.simulate(yaw, pitch, detect, startVel, gravity, ticks);
            int surv = (int)sim[0];
            boolean clears = surv >= ticks;
            boolean descends = sim[1] < -0.5;
            int tier = clears ? 0 : (descends ? 2 : 1);
            double dev = this.deviation(off);
            double cont = committed ? this.headingDelta(yaw, pitch, this.lastYaw, this.lastPitch) : 0.0;
            double descentPenalty = clears ? -sim[1] * this.climbBias.getValue() : 0.0;
            double score = (clears ? 0.0 : surv * 1000.0) - dev - descentPenalty - cont * 2.0;
            if (tier < bestTier || tier == bestTier && score > bestScore) {
               bestTier = tier;
               bestScore = score;
               bestYaw = yaw;
               bestPitch = pitch;
            }
         }
      }

      return new float[]{bestYaw, bestPitch};
   }

   private double deviation(double[] off) {
      double yw = off[0] * 1.5;
      return Math.sqrt(yw * yw + off[1] * off[1]);
   }

   private double headingDelta(float yaw, float pitch, float refYaw, float refPitch) {
      double dy = class_3532.method_15393(yaw - refYaw) * 1.5;
      double dp = pitch - refPitch;
      return Math.sqrt(dy * dy + dp * dp);
   }

   private void resubmit() {
      Homovore.rotationManager.submit(new RotationRequest("ElytraAssist", 90, this.lastYaw, this.lastPitch, RotationRequest.Mode.MOTION, true, true));
   }

   private void steerTo(float yaw, float pitch) {
      this.lastYaw = yaw;
      this.lastPitch = class_3532.method_15363(pitch, -90.0F, 90.0F);
      this.resubmit();
      this.steering = true;
   }

   private void stop() {
      this.clearTicks = 0;
      if (this.steering) {
         Homovore.rotationManager.cancel("ElytraAssist");
         this.steering = false;
      }
   }

   private double[] simulate(float yaw, float pitch, class_238 box, class_243 startVel, double gravity, int horizon) {
      class_243 vel = startVel;
      double ox = 0.0;
      double oy = 0.0;
      double oz = 0.0;
      double lowestY = 0.0;

      for (int t = 0; t < horizon; t++) {
         vel = this.stepFlight(vel, yaw, pitch, gravity);
         double startX = ox;
         double startY = oy;
         double startZ = oz;
         ox += vel.field_1352;
         oy += vel.field_1351;
         oz += vel.field_1350;
         int sub = class_3532.method_15340((int)Math.ceil(vel.method_1033() / 0.45), 1, 12);

         for (int s = 1; s <= sub; s++) {
            double frac = (double)s / sub;
            if (!mc.field_1687.method_18026(box.method_989(startX + vel.field_1352 * frac, startY + vel.field_1351 * frac, startZ + vel.field_1350 * frac))) {
               return new double[]{t, lowestY};
            }
         }

         if (oy < lowestY) {
            lowestY = oy;
         }
      }

      return new double[]{horizon, lowestY};
   }

   private int simulateSurvival(float yaw, float pitch, class_238 box, class_243 startVel, double gravity, int horizon) {
      return (int)this.simulate(yaw, pitch, box, startVel, gravity, horizon)[0];
   }

   private class_243 stepFlight(class_243 vel, float yaw, float pitch, double gravity) {
      class_243 look = this.dirFromAngles(yaw, pitch);
      double f = Math.toRadians(pitch);
      double horizLook = Math.sqrt(look.field_1352 * look.field_1352 + look.field_1350 * look.field_1350);
      double cosSq = Math.cos(f);
      cosSq *= cosSq;
      double sinF = Math.sin(f);
      double vx = vel.field_1352;
      double vy = vel.field_1351;
      double vz = vel.field_1350;
      double horizSpeed = Math.sqrt(vx * vx + vz * vz);
      vy += gravity * (-1.0 + cosSq * 0.75);
      if (vy < 0.0 && horizLook > 0.0) {
         double k = vy * -0.1 * cosSq;
         vx += look.field_1352 * k / horizLook;
         vy += k;
         vz += look.field_1350 * k / horizLook;
      }

      if (f < 0.0 && horizLook > 0.0) {
         double k = horizSpeed * -sinF * 0.04;
         vx += -look.field_1352 * k / horizLook;
         vy += k * 3.2;
         vz += -look.field_1350 * k / horizLook;
      }

      if (horizLook > 0.0) {
         vx += (look.field_1352 / horizLook * horizSpeed - vx) * 0.1;
         vz += (look.field_1350 / horizLook * horizSpeed - vz) * 0.1;
      }

      return new class_243(vx * 0.99, vy * 0.98, vz * 0.99);
   }

   private double effectiveGravity(class_243 velocity) {
      double g = mc.field_1724.method_6127().method_45331(class_5134.field_49078) ? mc.field_1724.method_45325(class_5134.field_49078) : 0.08;
      if (velocity.field_1351 <= 0.0 && mc.field_1724.method_6059(class_1294.field_5906)) {
         g = Math.min(g, 0.01);
      }

      return g;
   }

   private List<double[]> offsets() {
      if (this.offsets != null) {
         return this.offsets;
      }

      List<double[]> list = new ArrayList<>();

      for (double y : YAW_OFFSETS) {
         for (double p : PITCH_OFFSETS) {
            if (y != 0.0 || p != 0.0) {
               list.add(new double[]{y, p});
            }
         }
      }

      list.sort(Comparator.comparingDouble(this::deviation));
      this.offsets = list;
      return list;
   }

   private class_243 dirFromAngles(float yaw, float pitch) {
      double ry = Math.toRadians(yaw);
      double rp = Math.toRadians(pitch);
      double cosP = Math.cos(rp);
      return new class_243(-cosP * Math.sin(ry), -Math.sin(rp), cosP * Math.cos(ry));
   }

   @Override
   public String getDisplayInfo() {
      return this.steering ? "Steering" : null;
   }
}
