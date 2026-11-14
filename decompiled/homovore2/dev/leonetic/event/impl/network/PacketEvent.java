package dev.leonetic.event.impl.network;

import dev.leonetic.event.Event;
import java.util.Objects;
import net.minecraft.class_2596;

public abstract class PacketEvent extends Event {
   private final class_2596<?> packet;

   protected PacketEvent(class_2596<?> packet) {
      this.packet = Objects.requireNonNull(packet, "packet");
   }

   public class_2596<?> getPacket() {
      return this.packet;
   }

   public static final class Receive extends PacketEvent {
      public Receive(class_2596<?> packet) {
         super(packet);
      }
   }

   public static final class Send extends PacketEvent {
      public Send(class_2596<?> packet) {
         super(packet);
      }
   }
}
