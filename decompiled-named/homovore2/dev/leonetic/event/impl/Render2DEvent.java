package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import net.minecraft.client.gui.DrawContext;

public class Render2DEvent extends Event {
   private final DrawContext graphics;
   private final float delta;

   public Render2DEvent(DrawContext graphics, float delta) {
      this.graphics = graphics;
      this.delta = delta;
   }

   public DrawContext getGraphics() {
      return this.graphics;
   }

   public float getDelta() {
      return this.delta;
   }
}
