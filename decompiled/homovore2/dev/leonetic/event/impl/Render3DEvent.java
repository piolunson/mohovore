package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import net.minecraft.class_4184;

public class Render3DEvent extends Event {
   private final class_4184 camera;
   private final float delta;

   public Render3DEvent(class_4184 camera, float delta) {
      this.camera = camera;
      this.delta = delta;
   }

   public class_4184 getCamera() {
      return this.camera;
   }

   public float getDelta() {
      return this.delta;
   }
}
