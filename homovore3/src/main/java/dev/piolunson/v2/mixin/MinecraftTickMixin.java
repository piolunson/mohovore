package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftTickMixin {
   @Inject(
      method = "tick",
      at = @At(value = "FIELD", target = "Lnet/minecraft/network/packet/c2s/play/ClientTickEndC2SPacket;INSTANCE:Lnet/minecraft/network/packet/c2s/play/ClientTickEndC2SPacket;", shift = Shift.BEFORE)
   )
   private void homovore$beforeClientTickEnd(CallbackInfo ci) {
      Homovore2Client.beforeClientTickEnd((MinecraftClient)(Object)this);
   }
}
