package dev.piolunson.v2.event.impl;

import dev.piolunson.v2.event.Event;
import net.minecraft.client.render.Camera;

public class Render3DEvent extends Event {
   private final Camera camera;
   private final float delta;

   public Render3DEvent(Camera camera, float delta) {
      this.camera = camera;
      this.delta = delta;
   }

   public Camera getCamera() {
      return this.camera;
   }

   public float getDelta() {
      return this.delta;
   }
}
