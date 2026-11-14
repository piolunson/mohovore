package dev.leonetic.features.modules.player;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.input.MouseInputEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.manager.RotationRequest;
import dev.leonetic.util.inventory.InventoryUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.SwapMode;
import net.minecraft.class_1802;

public class MiddleClickExtraModule extends Module {
   private static final int ROTATION_PRIORITY = 1000;
   private final Setting<Boolean> fireworkInAir = this.bool("FireworkInAir", true);

   public MiddleClickExtraModule() {
      super("MiddleClick", "Throws a pearl or firework on middle click.", Module.Category.PLAYER);
   }

   @Subscribe
   private void onMouse(MouseInputEvent event) {
      if (!nullCheck() && mc.field_1755 == null) {
         if (event.getButton() == 2) {
            if (event.getAction() == 1) {
               if (this.fireworkInAir.getValue() && mc.field_1724.method_6128()) {
                  Result firework = InventoryUtil.find(class_1802.field_8639, InventoryUtil.FULL_SCOPE);
                  if (firework.found()) {
                     Homovore.swapManager.withSwap(firework, SwapMode.ALTSILENT, 150, () -> mc.field_1761.method_2919(mc.field_1724, firework.hand()));
                  }
               } else {
                  Result pearl = InventoryUtil.find(class_1802.field_8634, InventoryUtil.FULL_SCOPE);
                  if (pearl.found()) {
                     Homovore.rotationManager
                        .submit(
                           new RotationRequest(
                              "MiddleClick.pearl",
                              1000,
                              Homovore.rotationManager.getRealYaw(),
                              Homovore.rotationManager.getRealPitch(),
                              RotationRequest.Mode.SILENT
                           )
                        );
                     Homovore.swapManager.withSwap(pearl, SwapMode.ALTSILENT, 150, () -> {
                        Homovore.rotationManager.setBypassUseSpoof(true);

                        try {
                           mc.field_1761.method_2919(mc.field_1724, pearl.hand());
                        } finally {
                           Homovore.rotationManager.setBypassUseSpoof(false);
                        }
                     });
                  }
               }
            }
         }
      }
   }
}
