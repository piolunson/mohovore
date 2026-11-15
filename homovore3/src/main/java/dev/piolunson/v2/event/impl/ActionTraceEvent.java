package dev.piolunson.v2.event.impl;

import dev.piolunson.v2.event.Event;

public class ActionTraceEvent extends Event {
   private final String module;
   private final String action;
   private final long pressId;
   private final ActionTraceEvent.Stage stage;
   private final String detail;
   private final int attempt;
   private final long inputNanos;
   private final long detectedNanos;
   private final long nanos;

   public ActionTraceEvent(
      String module, String action, long pressId, ActionTraceEvent.Stage stage, String detail, int attempt, long inputNanos, long detectedNanos, long nanos
   ) {
      this.module = module;
      this.action = action;
      this.pressId = pressId;
      this.stage = stage;
      this.detail = detail;
      this.attempt = attempt;
      this.inputNanos = inputNanos;
      this.detectedNanos = detectedNanos;
      this.nanos = nanos;
   }

   public String getModule() {
      return this.module;
   }

   public String getAction() {
      return this.action;
   }

   public long getPressId() {
      return this.pressId;
   }

   public ActionTraceEvent.Stage getStage() {
      return this.stage;
   }

   public String getDetail() {
      return this.detail;
   }

   public int getAttempt() {
      return this.attempt;
   }

   public long getInputNanos() {
      return this.inputNanos;
   }

   public long getDetectedNanos() {
      return this.detectedNanos;
   }

   public long getNanos() {
      return this.nanos;
   }

   public enum Stage {
      PRESS,
      WAIT,
      TRY,
      REFUSED,
      SENT,
      DROPPED,
      RELEASED;
   }
}
