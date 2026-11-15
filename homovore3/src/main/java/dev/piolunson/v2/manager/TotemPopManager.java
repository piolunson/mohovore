package dev.piolunson.v2.manager;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.TickEvent;
import dev.piolunson.v2.event.impl.TotemPopEvent;
import dev.piolunson.v2.event.impl.network.PacketEvent;
import dev.piolunson.v2.event.system.Subscribe;
import dev.piolunson.v2.mixin.ClientboundEntityEventPacketAccessor;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public final class TotemPopManager {
   private static final byte TOTEM_POP_EVENT = 35;
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private final Queue<Integer> pending = new ConcurrentLinkedQueue<>();
   private final Map<UUID, Integer> pops = new ConcurrentHashMap<>();
   private final Set<UUID> aliveLastTick = new HashSet<>();
   private ClientWorld level;

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
         this.pending.add(((ClientboundEntityEventPacketAccessor)packet).homovore$getEntityId());
      }
   }

   @Subscribe
   public void onTick(TickEvent.Pre event) {
      if (mc.world != null && mc.player != null) {
         if (mc.world != this.level) {
            this.reset();
            this.level = mc.world;
         }

         this.expire();

         Integer entityId;
         while ((entityId = this.pending.poll()) != null) {
            if (mc.world.getEntityById(entityId) instanceof PlayerEntity player) {
               int count = this.pops.merge(player.getUuid(), 1, Integer::sum);
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

      for (PlayerEntity player : mc.world.getPlayers()) {
         UUID id = player.getUuid();
         loaded.add(id);
         if (player.isDead()) {
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
