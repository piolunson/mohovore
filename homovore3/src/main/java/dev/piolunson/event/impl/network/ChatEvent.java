package dev.piolunson.event.impl.network;

import dev.piolunson.event.Event;

public class ChatEvent extends Event {
   private final String content;

   public ChatEvent(String content) {
      this.content = content;
   }

   public String getMessage() {
      return this.content;
   }
}
