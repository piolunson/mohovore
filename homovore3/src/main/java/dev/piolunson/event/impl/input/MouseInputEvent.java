package dev.piolunson.event.impl.input;

import dev.piolunson.event.Event;

public class MouseInputEvent extends Event {
   private final int button;
   private final int action;

   public MouseInputEvent(int button, int action) {
      this.button = button;
      this.action = action;
   }

   public int getButton() {
      return this.button;
   }

   public int getAction() {
      return this.action;
   }
}
