package dev.leonetic.event.impl;

import dev.leonetic.event.Event;

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
