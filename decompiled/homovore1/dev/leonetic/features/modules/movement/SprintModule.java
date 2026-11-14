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
      } else if (mc.field_1724.method_6115()) {
         return false;
      } else {
         return this.mode.getValue() == SprintModule.Mode.OMNI && !mc.field_1724.method_6128()
            ? mc.field_1690.field_1894.method_1434()
               || mc.field_1690.field_1881.method_1434()
               || mc.field_1690.field_1913.method_1434()
               || mc.field_1690.field_1849.method_1434()
            : mc.field_1724.field_3913.method_3128().field_1342 > 0.0F;
      }
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (this.mode.getValue() == SprintModule.Mode.OMNI && !mc.field_1724.method_6128()) {
            this.omni();
         } else {
            Homovore.rotationManager.cancel("Sprint");
            if (mc.field_1724.field_3913.method_3128().field_1342 > 0.0F) {
               mc.field_1724.method_5728(true);
            }
         }
      }
   }

   private void omni() {
      int inputX = (mc.field_1690.field_1849.method_1434() ? 1 : 0) - (mc.field_1690.field_1913.method_1434() ? 1 : 0);
      int inputZ = (mc.field_1690.field_1894.method_1434() ? 1 : 0) - (mc.field_1690.field_1881.method_1434() ? 1 : 0);
      if (inputX == 0 && inputZ == 0) {
         Homovore.rotationManager.cancel("Sprint");
      } else {
         float moveAngle = (float)Math.toDegrees(Math.atan2(inputX, inputZ));
         float targetYaw = mc.field_1724.method_36454() + moveAngle;
         Homovore.rotationManager
            .submit(new RotationRequest("Sprint", Integer.MIN_VALUE, targetYaw, mc.field_1724.method_36455(), RotationRequest.Mode.MOTION, true, true));
         mc.field_1724.method_5728(true);
      }
   }

   public enum Mode {
      NORMAL,
      OMNI;
   }
}
