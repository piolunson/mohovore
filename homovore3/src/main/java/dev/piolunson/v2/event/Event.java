package dev.piolunson.v2.event;

public class Event {
   private boolean cancelled;

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void cancel() {
      this.cancelled = true;
   }
}
