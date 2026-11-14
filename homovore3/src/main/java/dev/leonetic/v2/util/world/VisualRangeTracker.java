package dev.leonetic.v2.util.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;

public final class VisualRangeTracker {
   private static final int GEAR_GRACE_TICKS = 40;
   private final Set<UUID> inRange = new HashSet<>();
   private final Map<UUID, Integer> awaitingGear = new HashMap<>();
   private ClientWorld level;

   public void forget() {
      this.inRange.clear();
      this.awaitingGear.clear();
      this.level = null;
   }

   public boolean isEmpty() {
      return this.inRange.isEmpty() && this.awaitingGear.isEmpty();
   }

   public void tick(boolean gearedOnly, Predicate<PlayerEntity> accept, Consumer<AbstractClientPlayerEntity> entered) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null || mc.player == null) {
         this.forget();
      } else if (mc.world != this.level) {
         this.forget();
         this.level = mc.world;

         for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player) {
               this.inRange.add(player.getUuid());
            }
         }
      } else {
         Set<UUID> loaded = new HashSet<>();

         for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player) {
               UUID id = player.getUuid();
               loaded.add(id);
               if (!accept.test(player)) {
                  this.inRange.add(id);
                  this.awaitingGear.remove(id);
               } else if (!this.inRange.add(id)) {
                  if (this.awaitingGear.containsKey(id) && PlayerGear.isGeared(player)) {
                     this.awaitingGear.remove(id);
                     entered.accept(player);
                  }
               } else if (gearedOnly && !PlayerGear.isGeared(player)) {
                  this.awaitingGear.put(id, 40);
               } else {
                  entered.accept(player);
               }
            }
         }

         this.inRange.retainAll(loaded);
         Iterator<Entry<UUID, Integer>> it = this.awaitingGear.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0 && loaded.contains(entry.getKey())) {
               entry.setValue(remaining);
            } else {
               it.remove();
            }
         }
      }
   }
}
