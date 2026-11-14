package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_310.class)
public abstract class MinecraftTickMixin {
   @Inject(
      method = "method_1574",
      at = @At(value = "FIELD", target = "Lnet/minecraft/class_9836;field_52333:Lnet/minecraft/class_9836;", shift = Shift.BEFORE)
   )
   private void homovore$beforeClientTickEnd(CallbackInfo ci) {
      Homovore2Client.beforeClientTickEnd((class_310)this);
   }
}
