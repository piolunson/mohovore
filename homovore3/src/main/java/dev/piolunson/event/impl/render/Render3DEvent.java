package dev.piolunson.event.impl.render;

import dev.piolunson.event.Event;
import net.minecraft.client.util.math.MatrixStack;

public class Render3DEvent extends Event {
   private final MatrixStack matrix;
   private final float delta;

   public Render3DEvent(MatrixStack matrix, float delta) {
      this.matrix = matrix;
      this.delta = delta;
   }

   public MatrixStack getMatrix() {
      return this.matrix;
   }

   public float getDelta() {
      return this.delta;
   }
}
