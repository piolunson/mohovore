package dev.leonetic.mixin.network;

import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class MixinClientCommonPacketListenerImpl {
   @Inject(method = "onDisconnected", at = @At("HEAD"))
   private void onDisconnectHook(DisconnectionInfo details, CallbackInfo ci) {
      Util.EVENT_BUS.post(new DisconnectEvent());
   }
}
