package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.FreecamModule;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_757;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_757.class)
public abstract class MinecraftPickMixin {
   private static final double FREECAM_REACH = 256.0;

   @Inject(method = "method_3190", at = @At("HEAD"), cancellable = true)
   private void homovore$freecamPick(float partialTick, CallbackInfo ci) {
      class_310 client = class_310.method_1551();
      FreecamModule freecam = FreecamModule.active();
      if (freecam != null && client.field_1687 != null && client.field_1724 != null) {
         class_243 start = freecam.getCameraPosition(partialTick);
         class_243 end = start.method_1019(class_243.method_1030(freecam.getCameraPitch(partialTick), freecam.getCameraYaw(partialTick)).method_1021(256.0));
         client.field_1765 = client.field_1687.method_17742(new class_3959(start, end, class_3960.field_17559, class_242.field_1348, client.field_1724));
         client.field_1692 = null;
         ci.cancel();
      }
   }
}
