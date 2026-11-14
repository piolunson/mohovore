package dev.leonetic.util.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.class_1657;
import net.minecraft.class_310;
import net.minecraft.class_638;
import net.minecraft.class_742;

public final class VisualRangeTracker {
   private static final int GEAR_GRACE_TICKS = 40;
   private final Set<UUID> inRange = new HashSet<>();
   private final Map<UUID, Integer> awaitingGear = new HashMap<>();
   private class_638 level;

   public void forget() {
      this.inRange.clear();
      this.awaitingGear.clear();
      this.level = null;
   }

   public boolean isEmpty() {
      return this.inRange.isEmpty() && this.awaitingGear.isEmpty();
   }

   public void tick(boolean gearedOnly, Predicate<class_1657> accept, Consumer<class_742> entered) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null) {
         this.forget();
      } else if (mc.field_1687 != this.level) {
         this.forget();
         this.level = mc.field_1687;

         for (class_742 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724) {
               this.inRange.add(player.method_5667());
            }
         }
      } else {
         Set<UUID> loaded = new HashSet<>();

         for (class_742 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724) {
               UUID id = player.method_5667();
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
