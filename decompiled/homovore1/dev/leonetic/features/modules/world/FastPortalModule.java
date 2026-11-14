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
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2886;
import net.minecraft.class_7202;

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
            class_243 eye = mc.field_1724.method_33571();
            class_2338 eyeBlockPos = class_2338.method_49638(eye);
            if (mc.field_1687.method_8320(eyeBlockPos).method_27852(class_2246.field_10316)) {
               Result pearl = InventoryUtil.find(class_1802.field_8634, InventoryUtil.FULL_SCOPE);
               if (pearl.found()) {
                  if (!mc.field_1724.method_7357().method_7904(new class_1799(class_1802.field_8634))) {
                     class_243 portalCenter = new class_243(eyeBlockPos.method_10263(), eyeBlockPos.method_10264() - 1, eyeBlockPos.method_10260());
                     float yaw = this.calcYaw(eye, portalCenter);
                     float pitch = this.calcPitch(eye, portalCenter);
                     Homovore.rotationManager.submit(new RotationRequest("fastportal", 20, yaw, pitch, RotationRequest.Mode.SILENT));
                     mc.field_1761.method_2911();
                     boolean sent = Homovore.swapManager.withSwap(pearl, SwapMode.ALTSILENT, 0, () -> {
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
                     if (sent) {
                        this.cooldownTicks = (int)(this.cooldown.getValue() * 20.0F);
                     }
                  }
               }
            }
         }
      }
   }

   private float calcYaw(class_243 eye, class_243 target) {
      class_243 diff = target.method_1020(eye);
      return (float)Math.toDegrees(Math.atan2(-diff.field_1352, diff.field_1350));
   }

   private float calcPitch(class_243 eye, class_243 target) {
      class_243 diff = target.method_1020(eye);
      double horizontalDist = Math.sqrt(diff.field_1352 * diff.field_1352 + diff.field_1350 * diff.field_1350);
      return (float)(-Math.toDegrees(Math.atan2(diff.field_1351, horizontalDist)));
   }
}
