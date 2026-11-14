package dev.leonetic.event.impl.render;

import dev.leonetic.event.Event;
import net.minecraft.class_1297;

public class RenderEntityEvent extends Event {
   private final class_1297 entity;

   public RenderEntityEvent(class_1297 entity) {
      this.entity = entity;
   }

   public class_1297 getEntity() {
      return this.entity;
   }
}
