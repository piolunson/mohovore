package dev.leonetic.util.trace;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.ActionTraceEvent;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.util.input.InputTimes;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ActionTrace {
   private static final AtomicLong IDS = new AtomicLong();
   private final String module;
   private final String action;
   private final long id;
   private final long inputNanos;
   private final long detectedNanos;
   private final AtomicInteger attempts = new AtomicInteger();
   private final AtomicBoolean finished = new AtomicBoolean();
   private final AtomicBoolean released = new AtomicBoolean();
   private volatile String waiting;

   private ActionTrace(String module, String action, long inputNanos, long detectedNanos) {
      this.module = module;
      this.action = action;
      this.id = IDS.incrementAndGet();
      this.inputNanos = inputNanos;
      this.detectedNanos = detectedNanos;
   }

   public static ActionTrace press(Module module, String action, Bind bind) {
      long now = System.nanoTime();
      ActionTrace trace = new ActionTrace(
         Objects.requireNonNull(module, "module").getName(), Objects.requireNonNull(action, "action"), InputTimes.pressedAt(bind, now), now
      );
      trace.post(ActionTraceEvent.Stage.PRESS, bind == null ? "" : "key=" + bind);
      return trace;
   }

   public void waiting(String reason) {
      if (!Objects.equals(this.waiting, reason)) {
         this.waiting = reason;
         this.post(ActionTraceEvent.Stage.WAIT, reason);
      }
   }

   public int attempt(String detail) {
      this.waiting = null;
      int attempt = this.attempts.incrementAndGet();
      this.post(ActionTraceEvent.Stage.TRY, detail);
      return attempt;
   }

   public void refused(String reason) {
      this.post(ActionTraceEvent.Stage.REFUSED, reason);
   }

   public void sent(int sequence) {
      this.sent(sequence < 0 ? "" : "#" + sequence);
   }

   public void sent(String detail) {
      if (this.finished.compareAndSet(false, true)) {
         this.post(ActionTraceEvent.Stage.SENT, detail);
      }
   }

   public void dropped(String reason) {
      if (this.finished.compareAndSet(false, true)) {
         this.post(ActionTraceEvent.Stage.DROPPED, reason);
      }
   }

   public void released(String detail) {
      this.finished.set(true);
      if (this.released.compareAndSet(false, true)) {
         this.post(ActionTraceEvent.Stage.RELEASED, detail);
      }
   }

   public boolean finished() {
      return this.finished.get();
   }

   private void post(ActionTraceEvent.Stage stage, String detail) {
      if (!this.finished.get() || stage == ActionTraceEvent.Stage.SENT || stage == ActionTraceEvent.Stage.DROPPED || stage == ActionTraceEvent.Stage.RELEASED) {
         Homovore2Client.EVENT_BUS
            .post(
               new ActionTraceEvent(
                  this.module,
                  this.action,
                  this.id,
                  stage,
                  detail == null ? "" : detail,
                  this.attempts.get(),
                  this.inputNanos,
                  this.detectedNanos,
                  System.nanoTime()
               )
            );
      }
   }
}
