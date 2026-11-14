package dev.leonetic.event.impl;

import dev.leonetic.event.Event;

public class TickEvent extends Event {
   public static class Post extends TickEvent {
   }

   public static class Pre extends TickEvent {
   }
}
