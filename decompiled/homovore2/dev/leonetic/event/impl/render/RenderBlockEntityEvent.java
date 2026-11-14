package dev.leonetic.event.impl.render;

import dev.leonetic.event.Event;
import net.minecraft.class_2591;

public class RenderBlockEntityEvent extends Event {
   private final class_2591<?> type;

   public RenderBlockEntityEvent(class_2591<?> type) {
      this.type = type;
   }

   public class_2591<?> getType() {
      return this.type;
   }
}
