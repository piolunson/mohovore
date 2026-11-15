package dev.piolunson.mixin.client;

import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameOptions.class)
public class MixinOptions {
   @ModifyConstant(method = "<init>", constant = @Constant(intValue = 110))
   private int homovore$raiseFovCap(int originalMax) {
      return 160;
   }
}
