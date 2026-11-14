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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class PlayerInfoManager extends Feature {
   private final Map<UUID, Integer> totemPops = new ConcurrentHashMap<>();
   private final Set<UUID> lastTickAlive = ConcurrentHashMap.newKeySet();

   public void init() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   public void onTotemPop(TotemPopEvent event) {
      this.totemPops.merge(event.getEntity().getUuid(), 1, Integer::sum);
   }

   @Subscribe
   public void onPreTick(PreTickEvent event) {
      if (mc.world != null && mc.player != null) {
         Set<UUID> currentlyAlive = new HashSet<>();
         Set<UUID> presentIds = new HashSet<>();

         for (PlayerEntity player : mc.world.getPlayers()) {
            UUID id = player.getUuid();
            presentIds.add(id);
            if (player.isDead()) {
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

   public ItemStack getEquipment(PlayerEntity player, EquipmentSlot slot) {
      return player.getEquippedStack(slot);
   }

   public ItemStack getMainHandItem(PlayerEntity player) {
      return player.getMainHandStack();
   }

   public ItemStack getOffHandItem(PlayerEntity player) {
      return player.getOffHandStack();
   }
}
