package dev.piolunson.v2.event.impl;

import dev.piolunson.v2.event.Event;

public final class ClickGuiSoundEvent extends Event {
   private final ClickGuiSoundEvent.Type type;

   public ClickGuiSoundEvent(ClickGuiSoundEvent.Type type) {
      this.type = type;
   }

   public ClickGuiSoundEvent.Type getType() {
      return this.type;
   }

   public enum Type {
      PRESS,
      HOVER;
   }
}
