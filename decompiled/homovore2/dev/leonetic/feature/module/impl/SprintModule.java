package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.PlayerInputEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.manager.RotationManager;
import net.minecraft.class_10185;

public final class SprintModule extends Module {
   private static final int ROTATION_PRIORITY = Integer.MIN_VALUE;
   private int suppressedTicks;

   public SprintModule() {
      super("Sprint", Category.MOVEMENT, "Sprints in all eight WASD directions.");
   }

   @Subscribe
   public void onPlayerInput(PlayerInputEvent event) {
      if (mc.field_1724 != null) {
         if (this.suppressedTicks > 0) {
            mc.field_1724.method_5728(false);
         } else if (mc.field_1724.method_6128()) {
            mc.field_1724.method_5728(false);
         } else {
            class_10185 input = mc.field_1724.field_3913.field_54155;
            int forward = (input.comp_3159() ? 1 : 0) - (input.comp_3160() ? 1 : 0);
            int sideways = (input.comp_3161() ? 1 : 0) - (input.comp_3162() ? 1 : 0);
            if ((forward != 0 || sideways != 0) && !mc.field_1724.method_6115() && !mc.field_1724.method_5765() && !mc.field_1724.method_74025()) {
               float movementYaw = movementYaw(mc.field_1724.method_36454(), forward, sideways);
               Homovore2Client.rotations().motion(new RotationManager.Angles(movementYaw, mc.field_1724.method_36455()), Integer.MIN_VALUE, null);
               mc.field_1724.method_5728(true);
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
      if (!mc.method_18854()) {
         throw new IllegalStateException("Sprint suppression must be requested on the client thread");
      }

      this.suppressedTicks = Math.max(this.suppressedTicks, Math.max(0, ticks));
      if (mc.field_1724 != null) {
         mc.field_1724.method_5728(false);
      }
   }

   @Override
   public void onDisable() {
      this.suppressedTicks = 0;
      if (mc.field_1724 != null) {
         mc.field_1724.method_5728(false);
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
