package dev.leonetic.v2.event.impl;

import dev.leonetic.v2.event.Event;

public class ChatSendEvent extends Event {
   private final String message;

   public ChatSendEvent(String message) {
      this.message = message;
   }

   public String getMessage() {
      return this.message;
   }
}
