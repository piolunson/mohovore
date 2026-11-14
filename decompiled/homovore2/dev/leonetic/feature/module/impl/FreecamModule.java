package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.mixin.ClientInputAccessor;
import net.minecraft.class_10185;
import net.minecraft.class_241;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_5498;
import net.minecraft.class_746;

public final class FreecamModule extends Module {
   private static final double STOP_EPSILON = 1.0E-4;
   private static final float TURN_SCALE = 0.15F;
   public final Setting<Double> speed = this.number("Speed", (double)1.0, (double)0.1, (double)3.0);
   public final Setting<Boolean> smooth = this.bool("Smooth", true);
   public final Setting<Double> acceleration = this.number("Acceleration", (double)0.35, (double)0.05, (double)1.0);
   public final Setting<Double> friction = this.number("Friction", (double)0.2, (double)0.02, (double)1.0);
   private class_5498 previousCameraType;
   private class_243 position;
   private class_243 previousPosition;
   private class_243 velocity = class_243.field_1353;
   private float yaw;
   private float previousYaw;
   private float pitch;
   private float previousPitch;

   public FreecamModule() {
      super("Freecam", Category.RENDER, "Detaches the camera from the player while leaving the player in place.");
      this.acceleration.visibleWhen(this.smooth::getValue);
      this.friction.visibleWhen(this.smooth::getValue);
   }

   @Override
   public void onEnable() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         this.previousCameraType = mc.field_1690.method_31044();
         mc.field_1690.method_31043(class_5498.field_26664);
         this.position = this.previousPosition = mc.field_1773.method_19418().method_71156();
         this.velocity = class_243.field_1353;
         this.yaw = this.previousYaw = mc.field_1724.method_36454();
         this.pitch = this.previousPitch = mc.field_1724.method_36455();
      } else {
         this.setEnabled(false);
      }
   }

   @Override
   public void onDisable() {
      if (this.previousCameraType != null) {
         mc.field_1690.method_31043(this.previousCameraType);
      }

      this.previousCameraType = null;
      this.position = null;
      this.previousPosition = null;
      this.velocity = class_243.field_1353;
   }

   @Override
   public void onTick() {
      if (this.position != null && mc.field_1724 != null && mc.field_1687 != null) {
         double forward = axis(mc.field_1690.field_1894.method_1434(), mc.field_1690.field_1881.method_1434());
         double strafe = axis(mc.field_1690.field_1913.method_1434(), mc.field_1690.field_1849.method_1434());
         double vertical = axis(mc.field_1690.field_1903.method_1434(), mc.field_1690.field_1832.method_1434());
         double yawRadians = Math.toRadians(this.yaw);
         class_243 wish = new class_243(
            strafe * Math.cos(yawRadians) - forward * Math.sin(yawRadians), vertical, forward * Math.cos(yawRadians) + strafe * Math.sin(yawRadians)
         );
         if (wish.method_1027() > 0.0) {
            wish = wish.method_1029();
         }

         double maxSpeed = this.speed.getValue();
         if (this.smooth.getValue()) {
            this.velocity = this.applyFriction(this.velocity);
            this.velocity = this.accelerate(this.velocity, wish, maxSpeed);
         } else {
            this.velocity = wish.method_1021(maxSpeed);
         }

         this.previousPosition = this.position;
         this.previousYaw = this.yaw;
         this.previousPitch = this.pitch;
         this.position = this.position.method_1019(this.velocity);
      } else {
         this.setEnabled(false);
      }
   }

   public static void suppressPlayerInput(class_746 player) {
      if (active() != null) {
         player.field_3913.field_54155 = class_10185.field_54098;
         ((ClientInputAccessor)player.field_3913).homovore$setMoveVector(class_241.field_1340);
      }
   }

   private class_243 applyFriction(class_243 current) {
      double length = current.method_1033();
      if (length < 1.0E-4) {
         return class_243.field_1353;
      }

      double drop = Math.max(length, this.speed.getValue() * 0.25) * this.friction.getValue();
      return current.method_1021(Math.max(0.0, length - drop) / length);
   }

   private class_243 accelerate(class_243 current, class_243 wish, double maxSpeed) {
      if (wish.method_1027() == 0.0) {
         return current;
      }

      double addSpeed = maxSpeed - current.method_1026(wish);
      return addSpeed <= 0.0 ? current : current.method_1019(wish.method_1021(Math.min(this.acceleration.getValue() * maxSpeed, addSpeed)));
   }

   public void turnCamera(double yawDelta, double pitchDelta) {
      this.previousYaw = this.yaw;
      this.previousPitch = this.pitch;
      this.yaw += (float)yawDelta * 0.15F;
      this.pitch = class_3532.method_15363(this.pitch + (float)pitchDelta * 0.15F, -90.0F, 90.0F);
   }

   public class_243 getCameraPosition(float partialTick) {
      return new class_243(
         class_3532.method_16436(partialTick, this.previousPosition.field_1352, this.position.field_1352),
         class_3532.method_16436(partialTick, this.previousPosition.field_1351, this.position.field_1351),
         class_3532.method_16436(partialTick, this.previousPosition.field_1350, this.position.field_1350)
      );
   }

   public float getCameraYaw(float partialTick) {
      return class_3532.method_16439(partialTick, this.previousYaw, this.yaw);
   }

   public float getCameraPitch(float partialTick) {
      return class_3532.method_16439(partialTick, this.previousPitch, this.pitch);
   }

   public static FreecamModule active() {
      FreecamModule freecam = Homovore2Client.modules() == null ? null : Homovore2Client.modules().get(FreecamModule.class);
      return freecam != null && freecam.isEnabled() && freecam.position != null ? freecam : null;
   }

   private static int axis(boolean positive, boolean negative) {
      return positive == negative ? 0 : (positive ? 1 : -1);
   }
}
