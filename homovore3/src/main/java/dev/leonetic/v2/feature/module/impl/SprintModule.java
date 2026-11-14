package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.PlayerInputEvent;
import dev.leonetic.v2.event.system.Subscribe;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.manager.RotationManager;
import net.minecraft.util.PlayerInput;

public final class SprintModule extends Module {
   private static final int ROTATION_PRIORITY = Integer.MIN_VALUE;
   private int suppressedTicks;

   public SprintModule() {
      super("Sprint", Category.MOVEMENT, "Sprints in all eight WASD directions.");
   }

   @Subscribe
   public void onPlayerInput(PlayerInputEvent event) {
      if (mc.player != null) {
         if (this.suppressedTicks > 0) {
            mc.player.setSprinting(false);
         } else if (mc.player.isGliding()) {
            mc.player.setSprinting(false);
         } else {
            PlayerInput input = mc.player.input.playerInput;
            int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
            int sideways = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
            if ((forward != 0 || sideways != 0) && !mc.player.isUsingItem() && !mc.player.hasVehicle() && !mc.player.hasBlindnessEffect()) {
               float movementYaw = movementYaw(mc.player.getYaw(), forward, sideways);
               Homovore2Client.rotations().motion(new RotationManager.Angles(movementYaw, mc.player.getPitch()), Integer.MIN_VALUE, null);
               mc.player.setSprinting(true);
            }
         }
      }
   }

   @Override
   public void onTick() {
      if (this.suppressedTicks > 0) {
         this.suppressedTicks--;
      }
   }

   public void stopSprinting(int ticks) {
      if (!mc.isOnThread()) {
         throw new IllegalStateException("Sprint suppression must be requested on the client thread");
      }

      this.suppressedTicks = Math.max(this.suppressedTicks, Math.max(0, ticks));
      if (mc.player != null) {
         mc.player.setSprinting(false);
      }
   }

   @Override
   public void onDisable() {
      this.suppressedTicks = 0;
      if (mc.player != null) {
         mc.player.setSprinting(false);
      }
   }

   private static float movementYaw(float yaw, int forward, int sideways) {
      if (forward < 0) {
         yaw += 180.0F;
      }

      float diagonal = forward < 0 ? -0.5F : (forward > 0 ? 0.5F : 1.0F);
      if (sideways > 0) {
         yaw -= 90.0F * diagonal;
      }

      if (sideways < 0) {
         yaw += 90.0F * diagonal;
      }

      return yaw;
   }
}
