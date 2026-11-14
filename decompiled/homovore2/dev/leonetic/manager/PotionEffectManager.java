package dev.leonetic.manager;

import dev.leonetic.event.impl.TickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.util.world.EffectParticles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.class_1292;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1844;
import net.minecraft.class_1847;
import net.minecraft.class_310;

public final class PotionEffectManager {
   private static final class_310 mc = class_310.method_1551();
   private static final long TURTLE_DURATION_MS = 20000L;
   private static final long STRENGTH_DURATION_MS = 90000L;
   private final Map<UUID, PotionEffectManager.Timers> timers = new HashMap<>();
   private final Map<UUID, List<PotionEffectManager.Icon>> icons = new HashMap<>();

   @Subscribe
   public void onTick(TickEvent.Pre event) {
      if (mc.field_1687 == null) {
         this.reset();
      } else {
         long now = System.currentTimeMillis();
         this.icons.clear();

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (!player.method_5805()) {
               this.timers.remove(player.method_5667());
            } else {
               List<PotionEffectManager.Icon> built = this.build(player, now);
               if (!built.isEmpty()) {
                  this.icons.put(player.method_5667(), built);
               }
            }
         }

         this.timers.keySet().removeIf(uuid -> mc.field_1687.method_18470(uuid) == null);
      }
   }

   public List<PotionEffectManager.Icon> icons(class_1657 player) {
      return this.icons.getOrDefault(player.method_5667(), List.of());
   }

   public void reset() {
      this.timers.clear();
      this.icons.clear();
   }

   private List<PotionEffectManager.Icon> build(class_1657 player, long now) {
      class_1293 resistance = player.method_6112(class_1294.field_5907);
      class_1293 slowness = player.method_6112(class_1294.field_5909);
      class_1293 strength = player.method_6112(class_1294.field_5910);
      boolean hasResistance = resistance != null || EffectParticles.hasResistance(player);
      boolean hasSlowness = slowness != null || EffectParticles.hasSlowness(player);
      boolean hasStrength = strength != null || EffectParticles.hasStrength(player);
      boolean turtle = hasResistance && hasSlowness;
      PotionEffectManager.Timers playerTimers = this.timers.computeIfAbsent(player.method_5667(), uuid -> new PotionEffectManager.Timers());
      String turtleLeft = this.remaining(playerTimers, PotionEffectManager.Tracked.TURTLE, turtle, now, shorter(resistance, slowness));
      String strengthLeft = this.remaining(playerTimers, PotionEffectManager.Tracked.STRENGTH, hasStrength, now, strength);
      List<PotionEffectManager.Icon> built = new ArrayList<>(2);
      if (turtleLeft != null) {
         built.add(new PotionEffectManager.Icon(PotionEffectManager.Tracked.TURTLE.stack(), turtleLeft));
      }

      if (strengthLeft != null) {
         built.add(new PotionEffectManager.Icon(PotionEffectManager.Tracked.STRENGTH.stack(), strengthLeft));
      }

      return built;
   }

   private String remaining(PotionEffectManager.Timers playerTimers, PotionEffectManager.Tracked tracked, boolean present, long now, class_1293 known) {
      if (!present) {
         playerTimers.startedAt[tracked.ordinal()] = 0L;
         return null;
      }

      if (known != null) {
         playerTimers.startedAt[tracked.ordinal()] = 0L;
         return class_1292.method_5577(known, 1.0F, mc.field_1687.method_54719().method_54748()).getString();
      }

      long startedAt = playerTimers.startedAt[tracked.ordinal()];
      if (startedAt == 0L || now - startedAt >= tracked.assumedMs) {
         startedAt = now;
         playerTimers.startedAt[tracked.ordinal()] = startedAt;
      }

      long remainingMs = tracked.assumedMs - (now - startedAt);
      long seconds = (remainingMs + 999L) / 1000L;
      return seconds / 60L + ":" + (seconds % 60L < 10L ? "0" : "") + seconds % 60L;
   }

   private static class_1293 shorter(class_1293 first, class_1293 second) {
      if (first != null && second != null) {
         return first.method_5584() <= second.method_5584() ? first : second;
      } else {
         return null;
      }
   }

   public record Icon(class_1799 stack, String duration) {
   }

   private static final class Timers {
      private final long[] startedAt = new long[PotionEffectManager.Tracked.values().length];
   }

   private enum Tracked {
      TURTLE(() -> class_1844.method_57400(class_1802.field_8436, class_1847.field_8990), 20000L),
      STRENGTH(() -> class_1844.method_57400(class_1802.field_8436, class_1847.field_8978), 90000L);

      private final Supplier<class_1799> icon;
      private final long assumedMs;
      private class_1799 stack;

      Tracked(Supplier<class_1799> icon, long assumedMs) {
         this.icon = icon;
         this.assumedMs = assumedMs;
      }

      private class_1799 stack() {
         if (this.stack == null) {
            this.stack = this.icon.get();
         }

         return this.stack;
      }
   }
}
