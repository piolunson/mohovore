package dev.leonetic.event.impl.render;

import dev.leonetic.event.Event;
import net.minecraft.class_2396;

public class RenderParticleEvent extends Event {
   private final class_2396<?> type;

   public RenderParticleEvent(class_2396<?> type) {
      this.type = type;
   }

   public class_2396<?> getType() {
      return this.type;
   }
}
