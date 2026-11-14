package dev.leonetic.mixin.network;

import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.util.traits.Util;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.class_1799;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_2598;
import net.minecraft.class_2653;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2535.class)
public class MixinClientConnection {
   @Shadow
   private Channel field_11651;
   @Shadow
   @Final
   private class_2598 field_11643;

   @Inject(method = "method_10770", at = @At("HEAD"), cancellable = true)
   public void channelRead0(ChannelHandlerContext chc, class_2596<?> packet, CallbackInfo ci) {
      if (this.field_11651.isOpen() && packet != null) {
         try {
            if (silentSwapFix(packet)) {
               ci.cancel();
               return;
            }

            if (Util.EVENT_BUS.post(new PacketEvent.Receive(packet))) {
               ci.cancel();
            }
         } catch (Exception var5) {
         }
      }
   }

   private static boolean silentSwapFix(class_2596<?> packet) {
      if (packet instanceof class_2653 slot) {
         if (slot.method_11452() != 0) {
            return false;
         }

         int s = slot.method_11450();
         if (s >= 36 && s <= 44) {
            class_310 mc = class_310.method_1551();
            if (mc.field_1724 == null) {
               return false;
            }

            class_1799 packetStack = slot.method_11449();
            class_1799 handStack = mc.field_1724.method_6047();
            return !packetStack.method_7960() && !handStack.method_7960()
               ? class_1799.method_7984(packetStack, handStack) && packetStack.method_7947() == handStack.method_7947()
               : false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Inject(method = "method_10764", at = @At("HEAD"), cancellable = true)
   private void sendImmediately(class_2596<?> packet, ChannelFutureListener callbacks, boolean flush, CallbackInfo ci) {
      if (this.field_11643 == class_2598.field_11942) {
         try {
            if (Util.EVENT_BUS.post(new PacketEvent.Send(packet))) {
               ci.cancel();
            }
         } catch (Exception var6) {
         }
      }
   }
}
