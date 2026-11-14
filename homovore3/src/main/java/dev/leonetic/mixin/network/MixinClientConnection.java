package dev.leonetic.mixin.network;

import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.util.traits.Util;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class MixinClientConnection {
   @Shadow
   private Channel channel;
   @Shadow
   @Final
   private NetworkSide side;

   @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
   public void channelRead0(ChannelHandlerContext chc, Packet<?> packet, CallbackInfo ci) {
      if (this.channel.isOpen() && packet != null) {
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

   private static boolean silentSwapFix(Packet<?> packet) {
      if (packet instanceof ScreenHandlerSlotUpdateS2CPacket slot) {
         if (slot.getSyncId() != 0) {
            return false;
         }

         int s = slot.getSlot();
         if (s >= 36 && s <= 44) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) {
               return false;
            }

            ItemStack packetStack = slot.getStack();
            ItemStack handStack = mc.player.getMainHandStack();
            return !packetStack.isEmpty() && !handStack.isEmpty()
               ? ItemStack.areItemsEqual(packetStack, handStack) && packetStack.getCount() == handStack.getCount()
               : false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Inject(method = "sendImmediately", at = @At("HEAD"), cancellable = true)
   private void sendImmediately(Packet<?> packet, ChannelFutureListener callbacks, boolean flush, CallbackInfo ci) {
      if (this.side == NetworkSide.CLIENTBOUND) {
         try {
            if (Util.EVENT_BUS.post(new PacketEvent.Send(packet))) {
               ci.cancel();
            }
         } catch (Exception var6) {
         }
      }
   }
}
