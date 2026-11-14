package dev.leonetic.util.network;

import java.util.Arrays;

public final class RollingWindowCounter {
   private static final int INITIAL_CAPACITY = 16;
   private long intervalNanos;
   private long[] times;
   private long[] counts;
   private int head;
   private int tail;
   private long sum;

   public RollingWindowCounter(long intervalNanos) {
      if (intervalNanos <= 0L) {
         throw new IllegalArgumentException("intervalNanos must be positive");
      }

      this.intervalNanos = intervalNanos;
      this.times = new long[16];
      this.counts = new long[16];
   }

   public void configure(long intervalNanos) {
      if (intervalNanos <= 0L) {
         throw new IllegalArgumentException("intervalNanos must be positive");
      }

      this.intervalNanos = intervalNanos;
   }

   public void trim(long now) {
      long minTime = now - this.intervalNanos;

      while (this.head != this.tail && this.times[this.head] - minTime < 0L) {
         this.sum = this.sum - this.counts[this.head];
         this.counts[this.head] = 0L;
         if (++this.head >= this.times.length) {
            this.head = 0;
         }
      }
   }

   public void add(long now, long count) {
      this.trim(now);
      int nextTail = (this.tail + 1) % this.times.length;
      if (nextTail == this.head) {
         this.grow();
         nextTail = (this.tail + 1) % this.times.length;
      }

      this.times[this.tail] = now;
      this.counts[this.tail] = count;
      this.sum += count;
      this.tail = nextTail;
   }

   public long sum(long now) {
      this.trim(now);
      return this.sum;
   }

   public long headroom(long now, long limit) {
      return Math.max(0L, limit - 1L - this.sum(now));
   }

   public long nanosUntilHeadroom(long now, long limit, long count) {
      this.trim(now);
      long required = this.sum + count - (limit - 1L);
      if (required <= 0L) {
         return 0L;
      }

      long removed = 0L;
      int cursor = this.head;

      while (cursor != this.tail) {
         removed += this.counts[cursor];
         if (removed >= required) {
            return Math.max(0L, this.times[cursor] + this.intervalNanos - now);
         }

         if (++cursor >= this.times.length) {
            cursor = 0;
         }
      }

      return this.intervalNanos;
   }

   public long intervalNanos() {
      return this.intervalNanos;
   }

   public void reset() {
      Arrays.fill(this.times, 0L);
      Arrays.fill(this.counts, 0L);
      this.head = 0;
      this.tail = 0;
      this.sum = 0L;
   }

   private void grow() {
      int size = this.tail >= this.head ? this.tail - this.head : this.tail + this.times.length - this.head;
      long[] newTimes = new long[this.times.length * 2];
      long[] newCounts = new long[this.counts.length * 2];
      if (this.tail >= this.head) {
         System.arraycopy(this.times, this.head, newTimes, 0, size);
         System.arraycopy(this.counts, this.head, newCounts, 0, size);
      } else {
         int upper = this.times.length - this.head;
         System.arraycopy(this.times, this.head, newTimes, 0, upper);
         System.arraycopy(this.times, 0, newTimes, upper, this.tail);
         System.arraycopy(this.counts, this.head, newCounts, 0, upper);
         System.arraycopy(this.counts, 0, newCounts, upper, this.tail);
      }

      this.times = newTimes;
      this.counts = newCounts;
      this.head = 0;
      this.tail = size;
   }
}
