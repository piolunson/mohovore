package dev.piolunson.v2.event.impl.network;

import dev.piolunson.v2.event.Event;
import java.util.Objects;
import net.minecraft.network.packet.Packet;

public abstract class PacketEvent extends Event {
   private final Packet<?> packet;

   protected PacketEvent(Packet<?> packet) {
      this.packet = Objects.requireNonNull(packet, "packet");
   }

   public Packet<?> getPacket() {
      return this.packet;
   }

   public static final class Receive extends PacketEvent {
      public Receive(Packet<?> packet) {
         super(packet);
      }
   }

   public static final class Send extends PacketEvent {
      public Send(Packet<?> packet) {
         super(packet);
      }
   }
}
