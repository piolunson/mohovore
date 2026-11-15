package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.TickEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.mixin.client.ClientLevelAccessor;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.SwapMode;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PhaseModule extends Module {
   private static final double CORNER_THRESHOLD = 0.5;
   private static final double CORNER_OFFSET = 0.5;
   private static final int PRIORITY = 150;

   public PhaseModule() {
      super("Phase", "Phases into walls", Module.Category.COMBAT);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (mc.player == null || mc.world == null) {
         this.disable();
      } else if (!Homovore.rotationManager.isSilentSyncRequiredAtLeast(150)) {
         Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
         if (!pearl.found()) {
            this.disable();
         } else if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
            this.disable();
         } else if (mc.player.isInSneakingPose()) {
            this.disable();
         } else {
            Vec3d target = this.calculateTargetPos();
            float yaw = this.calcYaw(target);
            float pitch = mc.player.getBlockY() > 4 ? 85.0F : 75.0F;
            Homovore.rotationManager.submit(new RotationRequest("phase", 150, yaw, pitch, RotationRequest.Mode.SILENT));
            mc.interactionManager.syncSelectedSlot();
            boolean thrown = Homovore.swapManager.withSwap(pearl, SwapMode.ALTSILENT, 175, () -> {
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
            if (thrown) {
               this.disable();
            }
         }
      }
   }

   private Vec3d calculateTargetPos() {
      double playerX = mc.player.getX();
      double playerZ = mc.player.getZ();
      double nearestIntX = Math.round(playerX);
      double nearestIntZ = Math.round(playerZ);
      double dxCorner = nearestIntX - playerX;
      double dzCorner = nearestIntZ - playerZ;
      double threshold = 0.5;
      double offset = 0.5;
      if (Math.abs(dxCorner) <= threshold && Math.abs(dzCorner) <= threshold) {
         return new Vec3d(playerX + MathHelper.clamp(dxCorner, -offset, offset), mc.player.getY() - 0.5, playerZ + MathHelper.clamp(dzCorner, -offset, offset));
      }

      double A = Math.PI / 13;
      double B = Math.PI / 4;
      double x = playerX
         + MathHelper.clamp(this.toClosest(playerX, Math.floor(playerX) + (Math.PI / 13), Math.floor(playerX) + (Math.PI / 4)) - playerX, -0.2, 0.2);
      double z = playerZ
         + MathHelper.clamp(this.toClosest(playerZ, Math.floor(playerZ) + (Math.PI / 13), Math.floor(playerZ) + (Math.PI / 4)) - playerZ, -0.2, 0.2);
      return new Vec3d(x, mc.player.getY() - 0.5, z);
   }

   private double toClosest(double num, double min, double max) {
      return num - min > max - num ? max : min;
   }

   private float calcYaw(Vec3d target) {
      Vec3d eye = mc.player.getEyePos();
      Vec3d diff = target.subtract(eye);
      return (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
   }
}
