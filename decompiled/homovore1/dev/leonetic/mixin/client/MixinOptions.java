package dev.leonetic.mixin.client;

import net.minecraft.class_315;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(class_315.class)
public class MixinOptions {
   @ModifyConstant(method = "<init>", constant = @Constant(intValue = 110))
   private int homovore$raiseFovCap(int originalMax) {
      return 160;
   }
}
