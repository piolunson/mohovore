package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import java.util.Objects;
import net.minecraft.class_2604;

public final class EntityAddedEvent extends Event {
   private final class_2604 packet;

   public EntityAddedEvent(class_2604 packet) {
      this.packet = Objects.requireNonNull(packet, "packet");
   }

   public class_2604 getPacket() {
      return this.packet;
   }
}
