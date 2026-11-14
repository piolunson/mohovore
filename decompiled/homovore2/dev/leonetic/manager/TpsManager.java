package dev.leonetic.manager;

import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.class_2761;
import net.minecraft.class_3532;

public final class TpsManager {
   public static final float DEFAULT_TPS = 20.0F;
   private static final int SAMPLE_COUNT = 20;
   private final AtomicReference<TpsManager.Samples> samples = new AtomicReference<>(TpsManager.Samples.EMPTY);

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof class_2761) {
         long now = System.nanoTime();
         this.samples.updateAndGet(current -> current.record(now));
      }
   }

   public float latest() {
      return this.samples.get().latest();
   }

   public float average() {
      return this.samples.get().average();
   }

   public float minimum() {
      return this.samples.get().minimum();
   }

   public int sampleCount() {
      return this.samples.get().count();
   }

   public void reset() {
      this.samples.set(TpsManager.Samples.EMPTY);
   }

   private record Samples(long lastNanos, float[] values, int next, int count) {
      private static final TpsManager.Samples EMPTY = new TpsManager.Samples(-1L, new float[20], 0, 0);

      private TpsManager.Samples record(long now) {
         if (this.lastNanos < 0L) {
            return new TpsManager.Samples(now, this.values, this.next, this.count);
         }

         float elapsedSeconds = (float)(now - this.lastNanos) / 1.0E9F;
         float measured = class_3532.method_15363(20.0F / Math.max(elapsedSeconds, 0.001F), 0.0F, 20.0F);
         float[] copy = (float[])this.values.clone();
         copy[this.next % copy.length] = measured;
         return new TpsManager.Samples(now, copy, this.next + 1, Math.min(this.count + 1, copy.length));
      }

      private float latest() {
         return this.count == 0 ? 20.0F : this.values[Math.floorMod(this.next - 1, this.values.length)];
      }

      private float average() {
         if (this.count == 0) {
            return 20.0F;
         }

         float total = 0.0F;

         for (int i = 0; i < this.count; i++) {
            total += this.values[i];
         }

         return class_3532.method_15363(total / this.count, 0.1F, 20.0F);
      }

      private float minimum() {
         if (this.count == 0) {
            return 20.0F;
         }

         float minimum = 20.0F;

         for (int i = 0; i < this.count; i++) {
            if (this.values[i] > 0.0F) {
               minimum = Math.min(minimum, this.values[i]);
            }
         }

         return minimum;
      }
   }
}
