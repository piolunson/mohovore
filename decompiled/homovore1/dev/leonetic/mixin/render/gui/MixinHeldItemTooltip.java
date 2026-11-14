package dev.leonetic.mixin.render.gui;

import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(class_329.class)
public abstract class MixinHeldItemTooltip {
   private static final int HOMOVORE$Y_OFFSET = 9;

   @ModifyArg(
      method = "method_1749",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_332;method_60649(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIII)V"),
      index = 3
   )
   private int homovore$shiftHeldItemName(int y) {
      return y - 9;
   }
}
