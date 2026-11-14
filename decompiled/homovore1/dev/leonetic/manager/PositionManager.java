package dev.leonetic.manager;

import dev.leonetic.event.Stage;
import dev.leonetic.event.impl.entity.player.UpdateWalkingPlayerEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.Feature;
import net.minecraft.class_2828.class_2829;

public class PositionManager extends Feature {
   private double x;
   private double y;
   private double z;
   private boolean onground;
   private double fallDistance;

   public PositionManager() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   public void onUpdateWalkingPlayer(UpdateWalkingPlayerEvent event) {
      if (event.getStage() != Stage.POST) {
         double diff = mc.field_1724.field_6036 - mc.field_1724.method_23318();
         if (!mc.field_1724.method_24828() && !(diff <= 0.0)) {
            this.fallDistance += diff;
         } else {
            this.fallDistance = 0.0;
         }
      }
   }

   public void updatePosition() {
      this.x = mc.field_1724.method_23317();
      this.y = mc.field_1724.method_23318();
      this.z = mc.field_1724.method_23321();
      this.onground = mc.field_1724.method_24828();
   }

   public void restorePosition() {
      mc.field_1724.method_5814(this.x, this.y, this.z);
      mc.field_1724.method_24830(this.onground);
   }

   public void setPlayerPosition(double x, double y, double z) {
      mc.field_1724.method_5814(x, y, z);
   }

   public void setPlayerPosition(double x, double y, double z, boolean onground) {
      mc.field_1724.method_5814(x, y, z);
      mc.field_1724.method_24830(onground);
   }

   public void setPositionPacket(double x, double y, double z, boolean onGround, boolean setPos, boolean noLagBack) {
      boolean bl = mc.field_1724.field_5976;
      mc.field_1724.field_3944.method_52787(new class_2829(x, y, z, onGround, bl));
      if (setPos) {
         mc.field_1724.method_5814(x, y, z);
         if (noLagBack) {
            this.updatePosition();
         }
      }
   }

   public double getX() {
      return this.x;
   }

   public void setX(double x) {
      this.x = x;
   }

   public double getY() {
      return this.y;
   }

   public void setY(double y) {
      this.y = y;
   }

   public double getZ() {
      return this.z;
   }

   public void setZ(double z) {
      this.z = z;
   }

   public double getFallDistance() {
      return this.fallDistance;
   }
}
