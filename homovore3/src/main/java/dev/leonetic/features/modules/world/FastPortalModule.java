package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class FastPortalModule extends Module {
   public Setting<Float> cooldown = this.num("Cooldown", 2.0F, 0.5F, 20.0F);
   private int cooldownTicks = 0;

   public FastPortalModule() {
      super("FastPortal", "Throws an ender pearl when inside nether portals", Module.Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.cooldownTicks = 0;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
         } else {
            Vec3d eye = mc.player.getEyePos();
            BlockPos eyeBlockPos = BlockPos.ofFloored(eye);
            if (mc.world.getBlockState(eyeBlockPos).isOf(Blocks.NETHER_PORTAL)) {
               Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
               if (pearl.found()) {
                  if (!mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
                     Vec3d portalCenter = new Vec3d(eyeBlockPos.getX(), eyeBlockPos.getY() - 1, eyeBlockPos.getZ());
                     float yaw = this.calcYaw(eye, portalCenter);
                     float pitch = this.calcPitch(eye, portalCenter);
                     Homovore.rotationManager.submit(new RotationRequest("fastportal", 20, yaw, pitch, RotationRequest.Mode.SILENT));
                     mc.interactionManager.syncSelectedSlot();
                     boolean sent = Homovore.swapManager.withSwap(pearl, SwapMode.ALTSILENT, 0, () -> {
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
                     if (sent) {
                        this.cooldownTicks = (int)(this.cooldown.getValue() * 20.0F);
                     }
                  }
               }
            }
         }
      }
   }

   private float calcYaw(Vec3d eye, Vec3d target) {
      Vec3d diff = target.subtract(eye);
      return (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
   }

   private float calcPitch(Vec3d eye, Vec3d target) {
      Vec3d diff = target.subtract(eye);
      double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
      return (float)(-Math.toDegrees(Math.atan2(diff.y, horizontalDist)));
   }
}
