package dev.leonetic.features.modules.funny;

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
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2886;
import net.minecraft.class_7202;

public class TrickshotModule extends Module {
   private static final double TARGET_X = 0.5;
   private static final double TARGET_Y = 62.0;
   private static final double TARGET_Z = 0.5;
   private static final class_2338 TARGET_BP = class_2338.method_49637(0.5, 62.0, 0.5);
   private static final double MAX_XZ_DIST = 60.0;
   private static final double PEARL_SPEED = 1.5;
   private static final double DRAG = 0.99;
   private static final double GRAVITY = 0.03;

   public TrickshotModule() {
      super("Trickshot", "Throws an ender pearl to 0 62 0 in the End.", Module.Category.FUNNY);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (!mc.field_1687.method_27983().method_29177().method_12832().equals("the_end")) {
            this.disable();
         } else {
            double px = mc.field_1724.method_23317();
            double pz = mc.field_1724.method_23321();
            if (px * px + pz * pz > 3600.0) {
               this.disable();
            } else {
               Result pearl = InventoryUtil.find(class_1802.field_8634, InventoryUtil.FULL_SCOPE);
               if (!pearl.found()) {
                  this.disable();
               } else if (mc.field_1724.method_7357().method_7904(new class_1799(class_1802.field_8634))) {
                  this.disable();
               } else {
                  class_243 eye = mc.field_1724.method_33571();
                  class_243 spawnPos = eye.method_1023(0.0, 0.1, 0.0);
                  double dX = 0.5 - eye.field_1352;
                  double dZ = 0.5 - eye.field_1350;
                  if (dX * dX + dZ * dZ < 0.001) {
                     this.disable();
                  } else {
                     float yaw = (float)Math.toDegrees(Math.atan2(-dX, dZ));
                     Float pitchResult = this.calculatePitch(yaw, spawnPos);
                     if (pitchResult == null) {
                        this.disable();
                     } else {
                        float pitch = pitchResult;
                        Homovore.rotationManager.submit(new RotationRequest("trickshot", 20, yaw, pitch, RotationRequest.Mode.SILENT));
                        mc.field_1761.method_2911();
                        Homovore.swapManager.withSwap(pearl, SwapMode.ALTSILENT, 0, () -> {
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
                        this.disable();
                     }
                  }
               }
            }
         }
      } else {
         this.disable();
      }
   }

   private Float calculatePitch(float yaw, class_243 spawnPos) {
      Float fallback = null;

      for (float pitch = -80.0F; pitch <= 80.0F; pitch += 0.25F) {
         Double vy = this.tryPitch(yaw, pitch, spawnPos);
         if (vy != null) {
            if (vy < 0.0) {
               return pitch;
            }

            if (fallback == null) {
               fallback = pitch;
            }
         }
      }

      return fallback;
   }

   private Double tryPitch(float yaw, float pitch, class_243 spawnPos) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      double vx = -Math.sin(yawRad) * Math.cos(pitchRad) * 1.5;
      double vy = -Math.sin(pitchRad) * 1.5;
      double vz = Math.cos(yawRad) * Math.cos(pitchRad) * 1.5;
      double x = spawnPos.field_1352;
      double y = spawnPos.field_1351;
      double z = spawnPos.field_1350;

      for (int t = 0; t < 1000; t++) {
         double var22 = vy - 0.03;
         vx *= 0.99;
         vy = var22 * 0.99;
         vz *= 0.99;
         x += vx;
         y += vy;
         z += vz;
         class_2338 bp = class_2338.method_49637(x, y, z);
         if (bp.equals(TARGET_BP)) {
            return vy;
         }

         if (!mc.field_1687.method_8320(bp).method_26220(mc.field_1687, bp).method_1110()) {
            return null;
         }
      }

      return null;
   }
}
