package dev.piolunson.v2.event.impl.render;

import dev.piolunson.v2.event.Event;
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
