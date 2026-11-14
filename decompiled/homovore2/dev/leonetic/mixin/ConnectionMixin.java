package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.util.network.NetworkConnectionAccess;
import dev.leonetic.util.network.NetworkThreadExecutor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2535.class)
public abstract class ConnectionMixin implements NetworkConnectionAccess {
   @Shadow
   private Channel field_11651;

   @Unique
   @Override
   public EventLoop homovore$getEventLoop() {
      return this.field_11651 == null ? null : this.field_11651.eventLoop();
   }

   @Inject(method = "method_36942", at = @At("HEAD"), cancellable = true)
   private void homovore$observeOutbound(class_2596<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      class_2535 connection = (class_2535)this;
      if (NetworkThreadExecutor.isActiveConnection(connection)) {
         if (Homovore2Client.EVENT_BUS.post(new PacketEvent.Send(packet))) {
            ci.cancel();
         } else {
            NetworkThreadExecutor.observeOutbound(connection, packet);
         }
      }
   }

   @Inject(method = "method_10770", at = @At("HEAD"), cancellable = true)
   private void homovore$observeInbound(ChannelHandlerContext context, class_2596<?> packet, CallbackInfo ci) {
      class_2535 connection = (class_2535)this;
      if (NetworkThreadExecutor.isActiveConnection(connection)) {
         NetworkThreadExecutor.observeInbound(connection, packet);
         if (Homovore2Client.EVENT_BUS.post(new PacketEvent.Receive(packet))) {
            ci.cancel();
         }
      }
   }
}
