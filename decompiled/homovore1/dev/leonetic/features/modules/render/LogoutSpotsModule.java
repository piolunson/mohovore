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
import net.minecraft.class_1304;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_243;
import net.minecraft.class_2703;
import net.minecraft.class_332;
import net.minecraft.class_5321;
import net.minecraft.class_638;
import net.minecraft.class_7828;
import net.minecraft.class_2703.class_2705;
import net.minecraft.class_2703.class_5893;

public class LogoutSpotsModule extends Module {
   public Setting<Color> spotColor = this.color("SpotColor", 255, 100, 100, 255);
   public Setting<Color> sideColor = this.color("SideColor", 255, 100, 100, 55);
   public Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F);
   public Setting<Boolean> showTime = this.bool("ShowTime", true);
   public Setting<Color> nameColor = this.color("NameColor", 255, 100, 100, 255);
   private static final class_1304[] ARMOR_SLOTS = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};
   private final Map<UUID, LogoutSpotsModule.SpotData> playerCache = new ConcurrentHashMap<>();
   private final Map<UUID, LogoutSpotsModule.LogoutSpot> loggedPlayers = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> ticksOnPlayerList = new ConcurrentHashMap<>();
   private class_5321<class_1937> lastDimension = null;
   private class_638 lastLevel = null;

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
      if (!nullCheck() && mc.method_1562() != null) {
         class_5321<class_1937> dim = mc.field_1687.method_27983();
         if (mc.field_1687 != this.lastLevel || this.lastDimension != null && !dim.equals(this.lastDimension)) {
            this.playerCache.clear();
            this.ticksOnPlayerList.clear();
         }

         this.lastLevel = mc.field_1687;
         this.lastDimension = dim;

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724) {
               this.playerCache.put(player.method_5667(), new LogoutSpotsModule.SpotData(player, dim));
            }
         }

         this.loggedPlayers.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            if (mc.method_1562().method_2871(uuid) != null) {
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
      if (event.getPacket() instanceof class_7828 pkt) {
         for (UUID id : pkt.comp_1105()) {
            if (!this.loggedPlayers.containsKey(id)) {
               LogoutSpotsModule.SpotData data = this.playerCache.get(id);
               if (data != null) {
                  this.loggedPlayers.put(id, new LogoutSpotsModule.LogoutSpot(data));
               }
            }
         }
      } else if (event.getPacket() instanceof class_2703 pkt && pkt.method_46327().contains(class_5893.field_29136)) {
         for (class_2705 e : pkt.method_46329()) {
            this.loggedPlayers.remove(e.comp_1106());
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      if (nullCheck()) {
         return this.loggedPlayers.isEmpty() ? null : String.valueOf(this.loggedPlayers.size());
      }

      class_5321<class_1937> dim = mc.field_1687.method_27983();
      long n = this.loggedPlayers.values().stream().filter(s -> dim.equals(s.dimension)).count();
      return n == 0L ? null : String.valueOf(n);
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         class_5321<class_1937> dim = mc.field_1687.method_27983();
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
            class_332 graphics = event.getContext();
            class_5321<class_1937> dim = mc.field_1687.method_27983();

            for (LogoutSpotsModule.LogoutSpot spot : this.loggedPlayers.values()) {
               if (dim.equals(spot.dimension)) {
                  double tagY = spot.pos.field_1351 + 1.8 + nametags.gap.getValue().floatValue() * 0.5;
                  double dist = mc.field_1724.method_73189().method_1022(spot.pos);
                  String timeStr = this.showTime.getValue() ? " " + this.formatElapsed(spot.logoutTime) : "";
                  nametags.renderNametag(
                     graphics,
                     spot.pos.field_1352,
                     tagY,
                     spot.pos.field_1350,
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
      public final class_243 pos;
      public final class_1657 entity;
      public final Map<class_1304, class_1799> armor;
      public final class_1799 mainHand;
      public final class_1799 offHand;
      public final int totemPops;
      public final long logoutTime;
      public final class_5321<class_1937> dimension;
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
      final class_243 pos;
      final class_1657 entity;
      final Map<class_1304, class_1799> armor;
      final class_1799 mainHand;
      final class_1799 offHand;
      final int totemPops;
      final class_5321<class_1937> dimension;

      SpotData(class_1657 player, class_5321<class_1937> dimension) {
         this.name = player.method_7334().name();
         this.uuid = player.method_5667();
         this.pos = player.method_73189();
         this.entity = player;
         this.dimension = dimension;
         this.totemPops = Homovore.playerInfoManager.getTotemPops(player.method_5667());
         this.mainHand = player.method_6047().method_7972();
         this.offHand = player.method_6079().method_7972();
         this.armor = new EnumMap<>(class_1304.class);

         for (class_1304 slot : LogoutSpotsModule.ARMOR_SLOTS) {
            this.armor.put(slot, player.method_6118(slot).method_7972());
         }
      }
   }
}
