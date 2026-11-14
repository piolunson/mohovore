package dev.leonetic.manager;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.TickEvent;
import dev.leonetic.event.impl.TotemPopEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.mixin.ClientboundEntityEventPacketAccessor;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.class_1657;
import net.minecraft.class_2663;
import net.minecraft.class_310;
import net.minecraft.class_638;

public final class TotemPopManager {
   private static final byte TOTEM_POP_EVENT = 35;
   private static final class_310 mc = class_310.method_1551();
   private final Queue<Integer> pending = new ConcurrentLinkedQueue<>();
   private final Map<UUID, Integer> pops = new ConcurrentHashMap<>();
   private final Set<UUID> aliveLastTick = new HashSet<>();
   private class_638 level;

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof class_2663 packet && packet.method_11470() == 35) {
         this.pending.add(((ClientboundEntityEventPacketAccessor)packet).homovore$getEntityId());
      }
   }

   @Subscribe
   public void onTick(TickEvent.Pre event) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (mc.field_1687 != this.level) {
            this.reset();
            this.level = mc.field_1687;
         }

         this.expire();

         Integer entityId;
         while ((entityId = this.pending.poll()) != null) {
            if (mc.field_1687.method_8469(entityId) instanceof class_1657 player) {
               int count = this.pops.merge(player.method_5667(), 1, Integer::sum);
               Homovore2Client.EVENT_BUS.post(new TotemPopEvent(player, count));
            }
         }
      } else {
         this.reset();
      }
   }

   private void expire() {
      Set<UUID> alive = new HashSet<>();
      Set<UUID> loaded = new HashSet<>();

      for (class_1657 player : mc.field_1687.method_18456()) {
         UUID id = player.method_5667();
         loaded.add(id);
         if (player.method_29504()) {
            if (this.aliveLastTick.contains(id)) {
               this.pops.remove(id);
            }
         } else {
            alive.add(id);
         }
      }

      this.pops.keySet().retainAll(loaded);
      this.aliveLastTick.clear();
      this.aliveLastTick.addAll(alive);
   }

   public int getPops(UUID player) {
      return this.pops.getOrDefault(player, 0);
   }

   public void reset() {
      this.pending.clear();
      this.pops.clear();
      this.aliveLastTick.clear();
      this.level = null;
   }
}
