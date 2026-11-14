package dev.leonetic.event.impl.entity;

import dev.leonetic.event.Event;
import net.minecraft.class_1309;

public class TotemPopEvent extends Event {
   private final class_1309 entity;

   public TotemPopEvent(class_1309 entity) {
      this.entity = entity;
   }

   public class_1309 getEntity() {
      return this.entity;
   }
}
