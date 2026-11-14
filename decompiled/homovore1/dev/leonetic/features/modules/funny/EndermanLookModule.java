package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.MathUtil;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1560;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_746;

public class EndermanLookModule extends Module {
   private static final String ROTATION_ID = "EndermanLook";
   private static final int ROTATION_PRIORITY = 1;
   private final Setting<EndermanLookModule.Mode> lookMode = this.mode("LookMode", EndermanLookModule.Mode.AWAY);
   private final Setting<Boolean> stunHostiles = this.bool("StunHostiles", true).setVisibility(v -> this.lookMode.getValue() == EndermanLookModule.Mode.AWAY);

   public EndermanLookModule() {
      super("EndermanLook", "Either stares at every Enderman or stops you from staring at them.", Module.Category.FUNNY);
   }

   @Override
   public void onDisable() {
      if (Homovore.rotationManager != null) {
         Homovore.rotationManager.cancel("EndermanLook");
      }
   }

   @Override
   public void onTick() {
      if (nullCheck()) {
         this.cancel();
      } else {
         class_746 player = mc.field_1724;
         if (!player.method_6118(class_1304.field_6169).method_31574(class_1802.field_17519) && !player.method_31549().field_7477) {
            class_1560 stareTarget = null;
            class_1560 awayThreat = null;
            double bestStare = Double.MAX_VALUE;
            double bestAway = Double.MAX_VALUE;

            for (class_1297 entity : mc.field_1687.method_18112()) {
               if (entity instanceof class_1560 enderman && enderman.method_5805() && player.method_6057(enderman)) {
                  double dist = player.method_5858(enderman);
                  switch ((EndermanLookModule.Mode)this.lookMode.getValue()) {
                     case AT:
                        if (!enderman.method_7028() && dist < bestStare) {
                           bestStare = dist;
                           stareTarget = enderman;
                        }
                        break;
                     case AWAY:
                        if (enderman.method_7028() && this.stunHostiles.getValue()) {
                           if (dist < bestStare) {
                              bestStare = dist;
                              stareTarget = enderman;
                           }
                        } else if (this.angleCheck(enderman) && dist < bestAway) {
                           bestAway = dist;
                           awayThreat = enderman;
                        }
                  }
               }
            }

            if (stareTarget != null) {
               float[] angles = MathUtil.calcAngle(player.method_33571(), stareTarget.method_33571());
               this.submit(angles[0], angles[1]);
            } else if (awayThreat != null) {
               this.submit(player.method_36454(), 90.0F);
            } else {
               this.cancel();
            }
         } else {
            this.cancel();
         }
      }
   }

   private void submit(float yaw, float pitch) {
      Homovore.rotationManager.submit(new RotationRequest("EndermanLook", 1, yaw, pitch, RotationRequest.Mode.MOTION, true));
   }

   private void cancel() {
      if (Homovore.rotationManager != null) {
         Homovore.rotationManager.cancel("EndermanLook");
      }
   }

   private boolean angleCheck(class_1560 enderman) {
      class_243 view = mc.field_1724.method_5828(1.0F).method_1029();
      class_243 diff = new class_243(
         enderman.method_23317() - mc.field_1724.method_23317(),
         enderman.method_23320() - mc.field_1724.method_23320(),
         enderman.method_23321() - mc.field_1724.method_23321()
      );
      double d = diff.method_1033();
      double dot = view.method_1026(diff.method_1029());
      return dot > 1.0 - 0.025 / d;
   }

   public enum Mode {
      AT,
      AWAY;
   }
}
