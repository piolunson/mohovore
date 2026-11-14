package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.DisconnectEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.MatrixCapture;
import dev.leonetic.util.render.WireframeEntityRenderer;
import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Entry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class LogoutSpotsModule extends Module {
   public Setting<Color> spotColor = this.color("SpotColor", 255, 100, 100, 255);
   public Setting<Color> sideColor = this.color("SideColor", 255, 100, 100, 55);
   public Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F);
   public Setting<Boolean> showTime = this.bool("ShowTime", true);
   public Setting<Color> nameColor = this.color("NameColor", 255, 100, 100, 255);
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private final Map<UUID, LogoutSpotsModule.SpotData> playerCache = new ConcurrentHashMap<>();
   private final Map<UUID, LogoutSpotsModule.LogoutSpot> loggedPlayers = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> ticksOnPlayerList = new ConcurrentHashMap<>();
   private RegistryKey<World> lastDimension = null;
   private ClientWorld lastLevel = null;

   public LogoutSpotsModule() {
      super("LogoutSpots", "Shows where players logged out with a wireframe avatar", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   @Override
   public void onTick() {
      if (!nullCheck() && mc.getNetworkHandler() != null) {
         RegistryKey<World> dim = mc.world.getRegistryKey();
         if (mc.world != this.lastLevel || this.lastDimension != null && !dim.equals(this.lastDimension)) {
            this.playerCache.clear();
            this.ticksOnPlayerList.clear();
         }

         this.lastLevel = mc.world;
         this.lastDimension = dim;

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player) {
               this.playerCache.put(player.getUuid(), new LogoutSpotsModule.SpotData(player, dim));
            }
         }

         this.loggedPlayers.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            if (mc.getNetworkHandler().getPlayerListEntry(uuid) != null) {
               int n = this.ticksOnPlayerList.getOrDefault(uuid, 0);
               this.ticksOnPlayerList.put(uuid, n + 1);
               if (n > 1) {
                  this.ticksOnPlayerList.remove(uuid);
                  return true;
               }
            } else {
               this.ticksOnPlayerList.remove(uuid);
            }

            return false;
         });
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof PlayerRemoveS2CPacket pkt) {
         for (UUID id : pkt.profileIds()) {
            if (!this.loggedPlayers.containsKey(id)) {
               LogoutSpotsModule.SpotData data = this.playerCache.get(id);
               if (data != null) {
                  this.loggedPlayers.put(id, new LogoutSpotsModule.LogoutSpot(data));
               }
            }
         }
      } else if (event.getPacket() instanceof PlayerListS2CPacket pkt && pkt.getActions().contains(Action.ADD_PLAYER)) {
         for (Entry e : pkt.getEntries()) {
            this.loggedPlayers.remove(e.profileId());
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      if (nullCheck()) {
         return this.loggedPlayers.isEmpty() ? null : String.valueOf(this.loggedPlayers.size());
      }

      RegistryKey<World> dim = mc.world.getRegistryKey();
      long n = this.loggedPlayers.values().stream().filter(s -> dim.equals(s.dimension)).count();
      return n == 0L ? null : String.valueOf(n);
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         RegistryKey<World> dim = mc.world.getRegistryKey();
         float delta = event.getDelta();

         for (LogoutSpotsModule.LogoutSpot spot : this.loggedPlayers.values()) {
            if (dim.equals(spot.dimension)) {
               spot.capturedGeometry = WireframeEntityRenderer.render(
                  event.getMatrix(),
                  spot.entity,
                  spot.pos,
                  spot.capturedGeometry,
                  delta,
                  this.sideColor.getValue(),
                  this.spotColor.getValue(),
                  this.lineWidth.getValue()
               );
            }
         }
      }
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck()) {
         if (MatrixCapture.projection != null) {
            NametagsModule nametags = Homovore.moduleManager.getModuleByClass(NametagsModule.class);
            DrawContext graphics = event.getContext();
            RegistryKey<World> dim = mc.world.getRegistryKey();

            for (LogoutSpotsModule.LogoutSpot spot : this.loggedPlayers.values()) {
               if (dim.equals(spot.dimension)) {
                  double tagY = spot.pos.y + 1.8 + nametags.gap.getValue().floatValue() * 0.5;
                  double dist = mc.player.getEntityPos().distanceTo(spot.pos);
                  String timeStr = this.showTime.getValue() ? " " + this.formatElapsed(spot.logoutTime) : "";
                  nametags.renderNametag(
                     graphics,
                     spot.pos.x,
                     tagY,
                     spot.pos.z,
                     dist,
                     spot.name,
                     this.nameColor.getValue().getRGB(),
                     timeStr,
                     spot.totemPops,
                     spot.armor,
                     spot.mainHand,
                     spot.offHand,
                     null
                  );
               }
            }
         }
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.resetState();
   }

   private void resetState() {
      this.loggedPlayers.clear();
      this.playerCache.clear();
      this.ticksOnPlayerList.clear();
      this.lastDimension = null;
      this.lastLevel = null;
   }

   private String formatElapsed(long logoutTime) {
      long secs = (System.currentTimeMillis() - logoutTime) / 1000L;
      return String.format("%02d:%02d", secs / 60L, secs % 60L);
   }

   public static class LogoutSpot {
      public final String name;
      public final UUID uuid;
      public final Vec3d pos;
      public final PlayerEntity entity;
      public final Map<EquipmentSlot, ItemStack> armor;
      public final ItemStack mainHand;
      public final ItemStack offHand;
      public final int totemPops;
      public final long logoutTime;
      public final RegistryKey<World> dimension;
      public List<float[][]> capturedGeometry = null;

      LogoutSpot(LogoutSpotsModule.SpotData data) {
         this.name = data.name;
         this.uuid = data.uuid;
         this.pos = data.pos;
         this.entity = data.entity;
         this.armor = data.armor;
         this.mainHand = data.mainHand;
         this.offHand = data.offHand;
         this.totemPops = data.totemPops;
         this.dimension = data.dimension;
         this.logoutTime = System.currentTimeMillis();
      }
   }

   private static class SpotData {
      final String name;
      final UUID uuid;
      final Vec3d pos;
      final PlayerEntity entity;
      final Map<EquipmentSlot, ItemStack> armor;
      final ItemStack mainHand;
      final ItemStack offHand;
      final int totemPops;
      final RegistryKey<World> dimension;

      SpotData(PlayerEntity player, RegistryKey<World> dimension) {
         this.name = player.getGameProfile().name();
         this.uuid = player.getUuid();
         this.pos = player.getEntityPos();
         this.entity = player;
         this.dimension = dimension;
         this.totemPops = Homovore.playerInfoManager.getTotemPops(player.getUuid());
         this.mainHand = player.getMainHandStack().copy();
         this.offHand = player.getOffHandStack().copy();
         this.armor = new EnumMap<>(EquipmentSlot.class);

         for (EquipmentSlot slot : LogoutSpotsModule.ARMOR_SLOTS) {
            this.armor.put(slot, player.getEquippedStack(slot).copy());
         }
      }
   }
}
