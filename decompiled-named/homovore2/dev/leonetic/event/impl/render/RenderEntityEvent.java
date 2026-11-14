package dev.leonetic.event.impl.render;

import dev.leonetic.event.Event;
import net.minecraft.entity.Entity;

public class RenderEntityEvent extends Event {
   private final Entity entity;

   public RenderEntityEvent(Entity entity) {
      this.entity = entity;
   }

   public Entity getEntity() {
      return this.entity;
   }
}
