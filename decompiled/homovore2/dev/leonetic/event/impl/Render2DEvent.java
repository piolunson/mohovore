package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import net.minecraft.class_332;

public class Render2DEvent extends Event {
   private final class_332 graphics;
   private final float delta;

   public Render2DEvent(class_332 graphics, float delta) {
      this.graphics = graphics;
      this.delta = delta;
   }

   public class_332 getGraphics() {
      return this.graphics;
   }

   public float getDelta() {
      return this.delta;
   }
}
