package dev.piolunson.v2.event.impl.render;

import dev.piolunson.v2.event.Event;
import net.minecraft.particle.ParticleType;

public class RenderParticleEvent extends Event {
   private final ParticleType<?> type;

   public RenderParticleEvent(ParticleType<?> type) {
      this.type = type;
   }

   public ParticleType<?> getType() {
      return this.type;
   }
}
