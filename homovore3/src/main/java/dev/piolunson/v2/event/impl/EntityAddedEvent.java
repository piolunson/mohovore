package dev.piolunson.v2.event.impl;

import dev.piolunson.v2.event.Event;
import java.util.Objects;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

public final class EntityAddedEvent extends Event {
   private final EntitySpawnS2CPacket packet;

   public EntityAddedEvent(EntitySpawnS2CPacket packet) {
      this.packet = Objects.requireNonNull(packet, "packet");
   }

   public EntitySpawnS2CPacket getPacket() {
      return this.packet;
   }
}
