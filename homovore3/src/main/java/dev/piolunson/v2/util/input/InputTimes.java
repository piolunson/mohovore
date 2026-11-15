package dev.piolunson.v2.util.input;

import dev.piolunson.v2.feature.setting.Bind;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InputTimes {
   private static final long MAX_AGE_NANOS = 250000000L;
   private static final ConcurrentMap<Integer, Long> PRESSED = new ConcurrentHashMap<>();

   private InputTimes() {
   }

   public static void pressed(int bindKey) {
      PRESSED.put(bindKey, System.nanoTime());
   }

   public static long pressedAt(Bind bind, long fallback) {
      if (bind != null && !bind.isEmpty()) {
         Long stamp = PRESSED.get(bind.getKey());
         return stamp != null && fallback - stamp <= 250000000L && stamp - fallback <= 0L ? stamp : fallback;
      } else {
         return fallback;
      }
   }
}
