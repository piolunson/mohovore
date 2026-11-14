package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.mixin.client.ClientLevelAccessor;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

public class KeyPotionModule extends Module {
   private final Setting<Boolean> onGround = this.bool("OnGround", false);

   public KeyPotionModule() {
      super("KeyPotion", "Throws a splash potion on keybind press.", Module.Category.PLAYER);
   }

   @Subscribe
   @Override
   public void onEnable() {
      if (!this.onGround.getValue() || mc.player.isOnGround()) {
         Result potion = InventoryUtil.find(Items.SPLASH_POTION, InventoryUtil.FULL_SCOPE);
         if (potion.found()) {
            float yaw = mc.player.getYaw();
            float pitch = 90.0F;
            Homovore.rotationManager.submit(new RotationRequest("KeyPotion", 20, yaw, pitch, RotationRequest.Mode.SILENT));
            mc.interactionManager.syncSelectedSlot();
            Homovore.swapManager.withSwap(potion, SwapMode.ALTSILENT, 150, () -> {
               PendingUpdateManager handler = ((ClientLevelAccessor)mc.world).homovore$getBlockStatePredictionHandler().incrementSequence();

               try {
                  mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(potion.hand(), handler.getSequence(), yaw, pitch));
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
         }

         this.disable();
      }
   }
}
