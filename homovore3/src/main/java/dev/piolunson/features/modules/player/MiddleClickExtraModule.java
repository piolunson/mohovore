package dev.piolunson.features.modules.player;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.input.MouseInputEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.manager.RotationRequest;
import dev.piolunson.util.inventory.InventoryUtil;
import dev.piolunson.util.inventory.Result;
import dev.piolunson.util.inventory.SwapMode;
import net.minecraft.item.Items;

public class MiddleClickExtraModule extends Module {
   private static final int ROTATION_PRIORITY = 1000;
   private final Setting<Boolean> fireworkInAir = this.bool("FireworkInAir", true);

   public MiddleClickExtraModule() {
      super("MiddleClick", "Throws a pearl or firework on middle click.", Module.Category.PLAYER);
   }

   @Subscribe
   private void onMouse(MouseInputEvent event) {
      if (!nullCheck() && mc.currentScreen == null) {
         if (event.getButton() == 2) {
            if (event.getAction() == 1) {
               if (this.fireworkInAir.getValue() && mc.player.isGliding()) {
                  Result firework = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
                  if (firework.found()) {
                     Homovore.swapManager.withSwap(firework, SwapMode.ALTSILENT, 150, () -> mc.interactionManager.interactItem(mc.player, firework.hand()));
                  }
               } else {
                  Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
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
                           mc.interactionManager.interactItem(mc.player, pearl.hand());
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
