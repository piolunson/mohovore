package dev.leonetic.features.modules.movement;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;

public class SprintModule extends Module {
   private final Setting<SprintModule.Mode> mode = this.mode("Mode", SprintModule.Mode.NORMAL);
   private static final String ROTATION_ID = "Sprint";

   public SprintModule() {
      super("Sprint", "Automatically sprints whenever you move. Omni sprints in any direction.", Module.Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      Homovore.rotationManager.cancel("Sprint");
   }

   public boolean wantsSprint() {
      if (!this.isEnabled() || nullCheck()) {
         return false;
      } else if (mc.player.isUsingItem()) {
         return false;
      } else {
         return this.mode.getValue() == SprintModule.Mode.OMNI && !mc.player.isGliding()
            ? mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()
            : mc.player.input.getMovementInput().y > 0.0F;
      }
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (this.mode.getValue() == SprintModule.Mode.OMNI && !mc.player.isGliding()) {
            this.omni();
         } else {
            Homovore.rotationManager.cancel("Sprint");
            if (mc.player.input.getMovementInput().y > 0.0F) {
               mc.player.setSprinting(true);
            }
         }
      }
   }

   private void omni() {
      int inputX = (mc.options.rightKey.isPressed() ? 1 : 0) - (mc.options.leftKey.isPressed() ? 1 : 0);
      int inputZ = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
      if (inputX == 0 && inputZ == 0) {
         Homovore.rotationManager.cancel("Sprint");
      } else {
         float moveAngle = (float)Math.toDegrees(Math.atan2(inputX, inputZ));
         float targetYaw = mc.player.getYaw() + moveAngle;
         Homovore.rotationManager
            .submit(new RotationRequest("Sprint", Integer.MIN_VALUE, targetYaw, mc.player.getPitch(), RotationRequest.Mode.MOTION, true, true));
         mc.player.setSprinting(true);
      }
   }

   public enum Mode {
      NORMAL,
      OMNI;
   }
}
