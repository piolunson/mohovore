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
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class TrickshotModule extends Module {
   private static final double TARGET_X = 0.5;
   private static final double TARGET_Y = 62.0;
   private static final double TARGET_Z = 0.5;
   private static final BlockPos TARGET_BP = BlockPos.ofFloored(0.5, 62.0, 0.5);
   private static final double MAX_XZ_DIST = 60.0;
   private static final double PEARL_SPEED = 1.5;
   private static final double DRAG = 0.99;
   private static final double GRAVITY = 0.03;

   public TrickshotModule() {
      super("Trickshot", "Throws an ender pearl to 0 62 0 in the End.", Module.Category.FUNNY);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (mc.player != null && mc.world != null) {
         if (!mc.world.getRegistryKey().getValue().getPath().equals("the_end")) {
            this.disable();
         } else {
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            if (px * px + pz * pz > 3600.0) {
               this.disable();
            } else {
               Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
               if (!pearl.found()) {
                  this.disable();
               } else if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
                  this.disable();
               } else {
                  Vec3d eye = mc.player.getEyePos();
                  Vec3d spawnPos = eye.subtract(0.0, 0.1, 0.0);
                  double dX = 0.5 - eye.x;
                  double dZ = 0.5 - eye.z;
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
                        mc.interactionManager.syncSelectedSlot();
                        Homovore.swapManager.withSwap(pearl, SwapMode.ALTSILENT, 0, () -> {
                           PendingUpdateManager handler = ((ClientLevelAccessor)mc.world).homovore$getBlockStatePredictionHandler().incrementSequence();

                           try {
                              mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(pearl.hand(), handler.getSequence(), yaw, pitch));
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

   private Float calculatePitch(float yaw, Vec3d spawnPos) {
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

   private Double tryPitch(float yaw, float pitch, Vec3d spawnPos) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      double vx = -Math.sin(yawRad) * Math.cos(pitchRad) * 1.5;
      double vy = -Math.sin(pitchRad) * 1.5;
      double vz = Math.cos(yawRad) * Math.cos(pitchRad) * 1.5;
      double x = spawnPos.x;
      double y = spawnPos.y;
      double z = spawnPos.z;

      for (int t = 0; t < 1000; t++) {
         double var22 = vy - 0.03;
         vx *= 0.99;
         vy = var22 * 0.99;
         vz *= 0.99;
         x += vx;
         y += vy;
         z += vz;
         BlockPos bp = BlockPos.ofFloored(x, y, z);
         if (bp.equals(TARGET_BP)) {
            return vy;
         }

         if (!mc.world.getBlockState(bp).getCollisionShape(mc.world, bp).isEmpty()) {
            return null;
         }
      }

      return null;
   }
}
