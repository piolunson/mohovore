package dev.leonetic.features.modules.movement;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.class_1802;
import net.minecraft.class_3532;

public class ElytraFlyModule extends Module {
   public static final String ID = "ElytraFly";
   public static final int PRIORITY = 50;
   private final Setting<Boolean> lockPitch = this.bool("LockPitch", true);
   private final Setting<Double> pitch = this.num("Pitch", 0.0, -45.0, 45.0);
   private final Setting<Double> climbPitch = this.num("ClimbPitch", 45.0, 15.0, 89.0);
   private final Setting<Boolean> hover = this.bool("Hover", false);
   private final Setting<Double> hoverPitch = this.num("HoverPitch", -1.3, -45.0, 0.0);
   private final Setting<Boolean> autoRocket = this.bool("AutoRocket", false);
   private boolean steering;
   private boolean hovering;
   private int idlePhase;
   private int ticksSinceRocket = 1073741823;
   private int refireTicks;

   public ElytraFlyModule() {
      super("ElytraFly", "Steer your elytra flight with WASD; auto-fires fireworks for thrust.", Module.Category.MOVEMENT);
      this.pitch.setVisibility(v -> this.lockPitch.getValue());
      this.hoverPitch.setVisibility(v -> this.hover.getValue());
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
         this.hovering = false;
         if (this.autoRocket.getValue()) {
            this.tryAutoRocket();
         }

         boolean forward = mc.field_1690.field_1894.method_1434();
         boolean back = mc.field_1690.field_1881.method_1434();
         boolean left = mc.field_1690.field_1913.method_1434();
         boolean right = mc.field_1690.field_1849.method_1434();
         boolean up = mc.field_1690.field_1903.method_1434();
         boolean down = mc.field_1690.field_1832.method_1434();
         float realYaw = mc.field_1724.method_36454();
         float strafe = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
         float fwd = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
         boolean hasHorizontal = strafe != 0.0F || fwd != 0.0F;
         boolean wantUp = up && !down;
         boolean wantDown = down && !up;
         if (hasHorizontal || wantUp || wantDown) {
            this.idlePhase = 0;
         }

         if (hasHorizontal) {
            float yaw = this.headingFromInput(realYaw, strafe, fwd);
            float targetPitch;
            if (wantUp) {
               targetPitch = -this.climbPitch.getValue().floatValue();
            } else if (wantDown) {
               targetPitch = this.climbPitch.getValue().floatValue();
            } else {
               targetPitch = this.lockPitch.getValue() ? this.pitch.getValue().floatValue() : mc.field_1724.method_36455();
            }

            this.steerTo(yaw, targetPitch);
         } else if (wantUp) {
            this.steerTo(realYaw, -90.0F);
         } else if (wantDown) {
            this.steerTo(realYaw, 90.0F);
         } else {
            if (this.hover.getValue()) {
               boolean lookBack = (this.idlePhase++ & 1) == 1;
               this.steerTo(lookBack ? class_3532.method_15393(realYaw + 180.0F) : realYaw, this.hoverPitch.getValue().floatValue());
               this.hovering = true;
            } else {
               this.stop();
            }
         }
      } else {
         this.stop();
      }
   }

   private void tryAutoRocket() {
      this.ticksSinceRocket++;
      if (this.ticksSinceRocket >= this.refireTicks) {
         Result rocket = InventoryUtil.find(class_1802.field_8639, InventoryUtil.FULL_SCOPE);
         if (rocket.found()) {
            this.ticksSinceRocket = 0;
            this.refireTicks = InventoryUtil.fireworkRefireTicks(rocket.stack());
            Homovore.swapManager.withSwap(rocket, SwapMode.ALTSILENT, 0, () -> mc.field_1761.method_2919(mc.field_1724, rocket.hand()));
         }
      }
   }

   private float headingFromInput(float cameraYaw, float strafe, float fwd) {
      double yawRad = Math.toRadians(cameraYaw);
      double fx = -Math.sin(yawRad);
      double fz = Math.cos(yawRad);
      double rx = -Math.sin(yawRad + (Math.PI / 2));
      double rz = Math.cos(yawRad + (Math.PI / 2));
      double wx = fx * fwd + rx * strafe;
      double wz = fz * fwd + rz * strafe;
      return (float)Math.toDegrees(Math.atan2(wz, wx)) - 90.0F;
   }

   private void steerTo(float yaw, float pitch) {
      Homovore.rotationManager
         .submit(new RotationRequest("ElytraFly", 50, yaw, class_3532.method_15363(pitch, -90.0F, 90.0F), RotationRequest.Mode.MOTION, true, true));
      this.steering = true;
   }

   private void stop() {
      this.hovering = false;
      this.idlePhase = 0;
      this.ticksSinceRocket = 1073741823;
      if (this.steering) {
         Homovore.rotationManager.cancel("ElytraFly");
         this.steering = false;
      }
   }

   @Override
   public String getDisplayInfo() {
      if (this.hovering) {
         return "Hover";
      } else {
         return this.steering ? "Steering" : null;
      }
   }
}
