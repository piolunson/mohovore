package dev.leonetic.event.impl;

import dev.leonetic.event.Event;
import dev.leonetic.feature.setting.Bind;

public final class BindPressEvent extends Event {
   private final int key;

   public BindPressEvent(int key) {
      this.key = key;
   }

   public int key() {
      return this.key;
   }

   public boolean matches(Bind bind) {
      return bind != null && !bind.isEmpty() && bind.getKey() == this.key;
   }
}
