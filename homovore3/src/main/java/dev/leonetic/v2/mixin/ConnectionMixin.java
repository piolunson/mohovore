package dev.leonetic.v2.mixin;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.network.PacketEvent;
import dev.leonetic.v2.util.network.NetworkConnectionAccess;
import dev.leonetic.v2.util.network.NetworkThreadExecutor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ConnectionMixin implements NetworkConnectionAccess {
   @Shadow
   private Channel channel;

   @Unique
   @Override
   public EventLoop homovore$getEventLoop() {
      return this.channel == null ? null : this.channel.eventLoop();
   }

   @Inject(method = "sendInternal", at = @At("HEAD"), cancellable = true)
   private void homovore$observeOutbound(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      ClientConnection connection = (ClientConnection)(Object)this;
      if (NetworkThreadExecutor.isActiveConnection(connection)) {
         if (Homovore2Client.EVENT_BUS.post(new PacketEvent.Send(packet))) {
            ci.cancel();
         } else {
            NetworkThreadExecutor.observeOutbound(connection, packet);
         }
      }
   }

   @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
   private void homovore$observeInbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
      ClientConnection connection = (ClientConnection)(Object)this;
      if (NetworkThreadExecutor.isActiveConnection(connection)) {
         NetworkThreadExecutor.observeInbound(connection, packet);
         if (Homovore2Client.EVENT_BUS.post(new PacketEvent.Receive(packet))) {
            ci.cancel();
         }
      }
   }
}
