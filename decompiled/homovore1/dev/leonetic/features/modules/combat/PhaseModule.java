package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_2886;
import net.minecraft.class_3532;
import net.minecraft.class_7202;

public class PhaseModule extends Module {
   private static final double CORNER_THRESHOLD = 0.5;
   private static final double CORNER_OFFSET = 0.5;
   private static final int PRIORITY = 150;

   public PhaseModule() {
      super("Phase", "Phases into walls", Module.Category.COMBAT);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         this.disable();
      } else if (!Homovore.rotationManager.isSilentSyncRequiredAtLeast(150)) {
         Result pearl = InventoryUtil.find(class_1802.field_8634, InventoryUtil.FULL_SCOPE);
         if (!pearl.found()) {
            this.disable();
         } else if (mc.field_1724.method_7357().method_7904(new class_1799(class_1802.field_8634))) {
            this.disable();
         } else if (mc.field_1724.method_18276()) {
            this.disable();
         } else {
            class_243 target = this.calculateTargetPos();
            float yaw = this.calcYaw(target);
            float pitch = mc.field_1724.method_31478() > 4 ? 85.0F : 75.0F;
            Homovore.rotationManager.submit(new RotationRequest("phase", 150, yaw, pitch, RotationRequest.Mode.SILENT));
            mc.field_1761.method_2911();
            boolean thrown = Homovore.swapManager.withSwap(pearl, SwapMode.ALTSILENT, 175, () -> {
               class_7202 handler = ((ClientLevelAccessor)mc.field_1687).homovore$getBlockStatePredictionHandler().method_41937();

               try {
                  mc.method_1562().method_52787(new class_2886(pearl.hand(), handler.method_41942(), yaw, pitch));
               } catch (Throwable t$) {
                  if (handler != null) {
                     try {
                        handler.close();
                     } catch (Throwable x2) {
                        t$.addSuppressed(x2);
                     }
                  }

                  throw t$;
               }

               if (handler != null) {
                  handler.close();
               }
            });
            if (thrown) {
               this.disable();
            }
         }
      }
   }

   private class_243 calculateTargetPos() {
      double playerX = mc.field_1724.method_23317();
      double playerZ = mc.field_1724.method_23321();
      double nearestIntX = Math.round(playerX);
      double nearestIntZ = Math.round(playerZ);
      double dxCorner = nearestIntX - playerX;
      double dzCorner = nearestIntZ - playerZ;
      double threshold = 0.5;
      double offset = 0.5;
      if (Math.abs(dxCorner) <= threshold && Math.abs(dzCorner) <= threshold) {
         return new class_243(
            playerX + class_3532.method_15350(dxCorner, -offset, offset),
            mc.field_1724.method_23318() - 0.5,
            playerZ + class_3532.method_15350(dzCorner, -offset, offset)
         );
      }

      double A = Math.PI / 13;
      double B = Math.PI / 4;
      double x = playerX
         + class_3532.method_15350(this.toClosest(playerX, Math.floor(playerX) + (Math.PI / 13), Math.floor(playerX) + (Math.PI / 4)) - playerX, -0.2, 0.2);
      double z = playerZ
         + class_3532.method_15350(this.toClosest(playerZ, Math.floor(playerZ) + (Math.PI / 13), Math.floor(playerZ) + (Math.PI / 4)) - playerZ, -0.2, 0.2);
      return new class_243(x, mc.field_1724.method_23318() - 0.5, z);
   }

   private double toClosest(double num, double min, double max) {
      return num - min > max - num ? max : min;
   }

   private float calcYaw(class_243 target) {
      class_243 eye = mc.field_1724.method_33571();
      class_243 diff = target.method_1020(eye);
      return (float)Math.toDegrees(Math.atan2(-diff.field_1352, diff.field_1350));
   }
}
