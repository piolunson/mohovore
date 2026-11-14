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
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;

public final class PotionEffectManager {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final long TURTLE_DURATION_MS = 20000L;
   private static final long STRENGTH_DURATION_MS = 90000L;
   private final Map<UUID, PotionEffectManager.Timers> timers = new HashMap<>();
   private final Map<UUID, List<PotionEffectManager.Icon>> icons = new HashMap<>();

   @Subscribe
   public void onTick(TickEvent.Pre event) {
      if (mc.world == null) {
         this.reset();
      } else {
         long now = System.currentTimeMillis();
         this.icons.clear();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (!player.isAlive()) {
               this.timers.remove(player.getUuid());
            } else {
               List<PotionEffectManager.Icon> built = this.build(player, now);
               if (!built.isEmpty()) {
                  this.icons.put(player.getUuid(), built);
               }
            }
         }

         this.timers.keySet().removeIf(uuid -> mc.world.getPlayerByUuid(uuid) == null);
      }
   }

   public List<PotionEffectManager.Icon> icons(PlayerEntity player) {
      return this.icons.getOrDefault(player.getUuid(), List.of());
   }

   public void reset() {
      this.timers.clear();
      this.icons.clear();
   }

   private List<PotionEffectManager.Icon> build(PlayerEntity player, long now) {
      StatusEffectInstance resistance = player.getStatusEffect(StatusEffects.RESISTANCE);
      StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
      StatusEffectInstance strength = player.getStatusEffect(StatusEffects.STRENGTH);
      boolean hasResistance = resistance != null || EffectParticles.hasResistance(player);
      boolean hasSlowness = slowness != null || EffectParticles.hasSlowness(player);
      boolean hasStrength = strength != null || EffectParticles.hasStrength(player);
      boolean turtle = hasResistance && hasSlowness;
      PotionEffectManager.Timers playerTimers = this.timers.computeIfAbsent(player.getUuid(), uuid -> new PotionEffectManager.Timers());
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

   private String remaining(PotionEffectManager.Timers playerTimers, PotionEffectManager.Tracked tracked, boolean present, long now, StatusEffectInstance known) {
      if (!present) {
         playerTimers.startedAt[tracked.ordinal()] = 0L;
         return null;
      }

      if (known != null) {
         playerTimers.startedAt[tracked.ordinal()] = 0L;
         return StatusEffectUtil.getDurationText(known, 1.0F, mc.world.getTickManager().getTickRate()).getString();
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

   private static StatusEffectInstance shorter(StatusEffectInstance first, StatusEffectInstance second) {
      if (first != null && second != null) {
         return first.getDuration() <= second.getDuration() ? first : second;
      } else {
         return null;
      }
   }

   public record Icon(ItemStack stack, String duration) {
   }

   private static final class Timers {
      private final long[] startedAt = new long[PotionEffectManager.Tracked.values().length];
   }

   private enum Tracked {
      TURTLE(() -> PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.TURTLE_MASTER), 20000L),
      STRENGTH(() -> PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.STRENGTH), 90000L);

      private final Supplier<ItemStack> icon;
      private final long assumedMs;
      private ItemStack stack;

      Tracked(Supplier<ItemStack> icon, long assumedMs) {
         this.icon = icon;
         this.assumedMs = assumedMs;
      }

      private ItemStack stack() {
         if (this.stack == null) {
            this.stack = this.icon.get();
         }

         return this.stack;
      }
   }
}
