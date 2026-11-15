package dev.piolunson.v2.event.impl;

import dev.piolunson.v2.event.Event;

public class TickEvent extends Event {
   public static class Post extends TickEvent {
   }

   public static class Pre extends TickEvent {
   }
}
