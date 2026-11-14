package dev.leonetic.v2.util.network;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

public final class PacketLimitSettings {
   private static final AtomicReference<PacketLimitSettings.Values> VALUES = new AtomicReference<>(PacketLimitSettings.Values.DEFAULTS);
   private static volatile DoubleSupplier tps = () -> 20.0;

   private PacketLimitSettings() {
      throw new AssertionError();
   }

   public static PacketLimitSettings.Values get() {
      return VALUES.get();
   }

   public static void set(PacketLimitSettings.Values values) {
      VALUES.set(values == null ? PacketLimitSettings.Values.DEFAULTS : values);
   }

   public static void bindTps(DoubleSupplier supplier) {
      tps = supplier == null ? () -> 20.0 : supplier;
   }

   static double tps() {
      double value = tps.getAsDouble();
      return value > 0.0 && Double.isFinite(value) ? value : 20.0;
   }

   public record Values(
      boolean enabled,
      long globalWindowNanos,
      int globalLimit,
      long useItemWindowNanos,
      int useItemLimit,
      long clickSlotWindowNanos,
      int clickSlotLimit,
      long offhandWindowNanos,
      int offhandLimit,
      long placeRecipeWindowNanos,
      int placeRecipeLimit,
      boolean grimTickGate,
      boolean tpsSync,
      double tpsSyncThreshold,
      double tpsMaxStretch
   ) {
      public static final PacketLimitSettings.Values DEFAULTS = new PacketLimitSettings.Values(
         true,
         TimeUnit.MILLISECONDS.toNanos(3951L),
         1249,
         TimeUnit.MILLISECONDS.toNanos(300L),
         9,
         TimeUnit.MILLISECONDS.toNanos(4200L),
         72,
         TimeUnit.MILLISECONDS.toNanos(500L),
         151,
         TimeUnit.MILLISECONDS.toNanos(4200L),
         19,
         true,
         true,
         18.0,
         2.0
      );

      public long stretch(long windowNanos) {
         if (!this.tpsSync) {
            return windowNanos;
         }

         double serverTps = PacketLimitSettings.tps();
         if (serverTps > this.tpsSyncThreshold) {
            return windowNanos;
         }

         double factor = Math.min(20.0 / serverTps, Math.max(1.0, this.tpsMaxStretch));
         return (long)(windowNanos * Math.max(1.0, factor));
      }
   }
}
