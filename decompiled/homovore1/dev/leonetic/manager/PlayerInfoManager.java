package dev.leonetic.manager;

import dev.leonetic.event.impl.entity.TotemPopEvent;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.Feature;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_1304;
import net.minecraft.class_1657;
import net.minecraft.class_1799;

public class PlayerInfoManager extends Feature {
   private final Map<UUID, Integer> totemPops = new ConcurrentHashMap<>();
   private final Set<UUID> lastTickAlive = ConcurrentHashMap.newKeySet();

   public void init() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   public void onTotemPop(TotemPopEvent event) {
      this.totemPops.merge(event.getEntity().method_5667(), 1, Integer::sum);
   }

   @Subscribe
   public void onPreTick(PreTickEvent event) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         Set<UUID> currentlyAlive = new HashSet<>();
         Set<UUID> presentIds = new HashSet<>();

         for (class_1657 player : mc.field_1687.method_18456()) {
            UUID id = player.method_5667();
            presentIds.add(id);
            if (player.method_29504()) {
               if (this.lastTickAlive.contains(id)) {
                  this.totemPops.remove(id);
               }
            } else {
               currentlyAlive.add(id);
            }
         }

         this.totemPops.keySet().removeIf(idx -> !presentIds.contains(idx));
         this.lastTickAlive.clear();
         this.lastTickAlive.addAll(currentlyAlive);
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.resetAll();
   }

   public int getTotemPops(UUID uuid) {
      return this.totemPops.getOrDefault(uuid, 0);
   }

   public void resetTotemPops(UUID uuid) {
      this.totemPops.remove(uuid);
   }

   public void resetAll() {
      this.totemPops.clear();
      this.lastTickAlive.clear();
   }

   public class_1799 getEquipment(class_1657 player, class_1304 slot) {
      return player.method_6118(slot);
   }

   public class_1799 getMainHandItem(class_1657 player) {
      return player.method_6047();
   }

   public class_1799 getOffHandItem(class_1657 player) {
      return player.method_6079();
   }
}
