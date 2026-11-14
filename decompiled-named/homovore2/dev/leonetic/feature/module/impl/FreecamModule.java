package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.mixin.ClientInputAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public final class FreecamModule extends Module {
   private static final double STOP_EPSILON = 1.0E-4;
   private static final float TURN_SCALE = 0.15F;
   public final Setting<Double> speed = this.number("Speed", (double)1.0, (double)0.1, (double)3.0);
   public final Setting<Boolean> smooth = this.bool("Smooth", true);
   public final Setting<Double> acceleration = this.number("Acceleration", (double)0.35, (double)0.05, (double)1.0);
   public final Setting<Double> friction = this.number("Friction", (double)0.2, (double)0.02, (double)1.0);
   private Perspective previousCameraType;
   private Vec3d position;
   private Vec3d previousPosition;
   private Vec3d velocity = Vec3d.ZERO;
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
      if (mc.player != null && mc.world != null) {
         this.previousCameraType = mc.options.getPerspective();
         mc.options.setPerspective(Perspective.FIRST_PERSON);
         this.position = this.previousPosition = mc.gameRenderer.getCamera().getCameraPos();
         this.velocity = Vec3d.ZERO;
         this.yaw = this.previousYaw = mc.player.getYaw();
         this.pitch = this.previousPitch = mc.player.getPitch();
      } else {
         this.setEnabled(false);
      }
   }

   @Override
   public void onDisable() {
      if (this.previousCameraType != null) {
         mc.options.setPerspective(this.previousCameraType);
      }

      this.previousCameraType = null;
      this.position = null;
      this.previousPosition = null;
      this.velocity = Vec3d.ZERO;
   }

   @Override
   public void onTick() {
      if (this.position != null && mc.player != null && mc.world != null) {
         double forward = axis(mc.options.forwardKey.isPressed(), mc.options.backKey.isPressed());
         double strafe = axis(mc.options.leftKey.isPressed(), mc.options.rightKey.isPressed());
         double vertical = axis(mc.options.jumpKey.isPressed(), mc.options.sneakKey.isPressed());
         double yawRadians = Math.toRadians(this.yaw);
         Vec3d wish = new Vec3d(
            strafe * Math.cos(yawRadians) - forward * Math.sin(yawRadians), vertical, forward * Math.cos(yawRadians) + strafe * Math.sin(yawRadians)
         );
         if (wish.lengthSquared() > 0.0) {
            wish = wish.normalize();
         }

         double maxSpeed = this.speed.getValue();
         if (this.smooth.getValue()) {
            this.velocity = this.applyFriction(this.velocity);
            this.velocity = this.accelerate(this.velocity, wish, maxSpeed);
         } else {
            this.velocity = wish.multiply(maxSpeed);
         }

         this.previousPosition = this.position;
         this.previousYaw = this.yaw;
         this.previousPitch = this.pitch;
         this.position = this.position.add(this.velocity);
      } else {
         this.setEnabled(false);
      }
   }

   public static void suppressPlayerInput(ClientPlayerEntity player) {
      if (active() != null) {
         player.input.playerInput = PlayerInput.DEFAULT;
         ((ClientInputAccessor)player.input).homovore$setMoveVector(Vec2f.ZERO);
      }
   }

   private Vec3d applyFriction(Vec3d current) {
      double length = current.length();
      if (length < 1.0E-4) {
         return Vec3d.ZERO;
      }

      double drop = Math.max(length, this.speed.getValue() * 0.25) * this.friction.getValue();
      return current.multiply(Math.max(0.0, length - drop) / length);
   }

   private Vec3d accelerate(Vec3d current, Vec3d wish, double maxSpeed) {
      if (wish.lengthSquared() == 0.0) {
         return current;
      }

      double addSpeed = maxSpeed - current.dotProduct(wish);
      return addSpeed <= 0.0 ? current : current.add(wish.multiply(Math.min(this.acceleration.getValue() * maxSpeed, addSpeed)));
   }

   public void turnCamera(double yawDelta, double pitchDelta) {
      this.previousYaw = this.yaw;
      this.previousPitch = this.pitch;
      this.yaw += (float)yawDelta * 0.15F;
      this.pitch = MathHelper.clamp(this.pitch + (float)pitchDelta * 0.15F, -90.0F, 90.0F);
   }

   public Vec3d getCameraPosition(float partialTick) {
      return new Vec3d(
         MathHelper.lerp(partialTick, this.previousPosition.x, this.position.x),
         MathHelper.lerp(partialTick, this.previousPosition.y, this.position.y),
         MathHelper.lerp(partialTick, this.previousPosition.z, this.position.z)
      );
   }

   public float getCameraYaw(float partialTick) {
      return MathHelper.lerp(partialTick, this.previousYaw, this.yaw);
   }

   public float getCameraPitch(float partialTick) {
      return MathHelper.lerp(partialTick, this.previousPitch, this.pitch);
   }

   public static FreecamModule active() {
      FreecamModule freecam = Homovore2Client.modules() == null ? null : Homovore2Client.modules().get(FreecamModule.class);
      return freecam != null && freecam.isEnabled() && freecam.position != null ? freecam : null;
   }

   private static int axis(boolean positive, boolean negative) {
      return positive == negative ? 0 : (positive ? 1 : -1);
   }
}
