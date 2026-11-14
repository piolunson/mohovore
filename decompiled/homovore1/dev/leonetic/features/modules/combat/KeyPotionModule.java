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
import net.minecraft.class_1802;
import net.minecraft.class_2886;
import net.minecraft.class_7202;

public class KeyPotionModule extends Module {
   private final Setting<Boolean> onGround = this.bool("OnGround", false);

   public KeyPotionModule() {
      super("KeyPotion", "Throws a splash potion on keybind press.", Module.Category.PLAYER);
   }

   @Subscribe
   @Override
   public void onEnable() {
      if (!this.onGround.getValue() || mc.field_1724.method_24828()) {
         Result potion = InventoryUtil.find(class_1802.field_8436, InventoryUtil.FULL_SCOPE);
         if (potion.found()) {
            float yaw = mc.field_1724.method_36454();
            float pitch = 90.0F;
            Homovore.rotationManager.submit(new RotationRequest("KeyPotion", 20, yaw, pitch, RotationRequest.Mode.SILENT));
            mc.field_1761.method_2911();
            Homovore.swapManager.withSwap(potion, SwapMode.ALTSILENT, 150, () -> {
               class_7202 handler = ((ClientLevelAccessor)mc.field_1687).homovore$getBlockStatePredictionHandler().method_41937();

               try {
                  mc.method_1562().method_52787(new class_2886(potion.hand(), handler.method_41942(), yaw, pitch));
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
