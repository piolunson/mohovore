package dev.piolunson.features.modules.funny;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.util.MathUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

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
         ClientPlayerEntity player = mc.player;
         if (!player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN) && !player.getAbilities().creativeMode) {
            EndermanEntity stareTarget = null;
            EndermanEntity awayThreat = null;
            double bestStare = Double.MAX_VALUE;
            double bestAway = Double.MAX_VALUE;

            for (Entity entity : mc.world.getEntities()) {
               if (entity instanceof EndermanEntity enderman && enderman.isAlive() && player.canSee(enderman)) {
                  double dist = player.squaredDistanceTo(enderman);
                  switch ((EndermanLookModule.Mode)this.lookMode.getValue()) {
                     case AT:
                        if (!enderman.isAngry() && dist < bestStare) {
                           bestStare = dist;
                           stareTarget = enderman;
                        }
                        break;
                     case AWAY:
                        if (enderman.isAngry() && this.stunHostiles.getValue()) {
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
               float[] angles = MathUtil.calcAngle(player.getEyePos(), stareTarget.getEyePos());
               this.submit(angles[0], angles[1]);
            } else if (awayThreat != null) {
               this.submit(player.getYaw(), 90.0F);
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

   private boolean angleCheck(EndermanEntity enderman) {
      Vec3d view = mc.player.getRotationVec(1.0F).normalize();
      Vec3d diff = new Vec3d(enderman.getX() - mc.player.getX(), enderman.getEyeY() - mc.player.getEyeY(), enderman.getZ() - mc.player.getZ());
      double d = diff.length();
      double dot = view.dotProduct(diff.normalize());
      return dot > 1.0 - 0.025 / d;
   }

   public enum Mode {
      AT,
      AWAY;
   }
}
