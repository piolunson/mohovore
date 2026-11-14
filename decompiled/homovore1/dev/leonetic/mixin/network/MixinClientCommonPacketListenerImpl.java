package dev.leonetic.mixin.network;

import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_8673;
import net.minecraft.class_9812;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_8673.class)
public class MixinClientCommonPacketListenerImpl {
   @Inject(method = "method_10839", at = @At("HEAD"))
   private void onDisconnectHook(class_9812 details, CallbackInfo ci) {
      Util.EVENT_BUS.post(new DisconnectEvent());
   }
}
