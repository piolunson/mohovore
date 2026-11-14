package dev.leonetic.features.modules.hud;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.client.HudModule;
import dev.leonetic.features.modules.client.HudPosition;
import dev.leonetic.features.modules.render.NametagsModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.class_10799;
import net.minecraft.class_1657;
import net.minecraft.class_2960;
import net.minecraft.class_332;

public class RadarHudModule extends HudModule {
   private static final int DEFAULT_POP_COLOR = -44976;
   private static final class_2960 LOGO = class_2960.method_60655("homovore", "textures/font/logo_white.png");
   private static final int LOGO_TEX_W = 128;
   private static final int LOGO_TEX_H = 128;
   private static final int LOGO_ALPHA_WHITE = -1;
   private static final int LOGO_GAP = 3;

   public RadarHudModule() {
      super("Radar");
   }

   public int renderedLineCount() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         int count = 1;
         UUID selfId = mc.field_1724.method_5667();

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (!player.method_5667().equals(selfId) && player.method_5805()) {
               count++;
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   @Override
   public void render(Render2DEvent event) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         List<RadarHudModule.Entry> entries = new ArrayList<>();
         UUID selfId = mc.field_1724.method_5667();

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (!player.method_5667().equals(selfId) && player.method_5805()) {
               double dist = mc.field_1724.method_73189().method_1022(player.method_73189());
               entries.add(new RadarHudModule.Entry(player, dist));
            }
         }

         entries.sort(Comparator.<RadarHudModule.Entry>comparingDouble(e -> e.dist).reversed());
         entries.add(new RadarHudModule.Entry(mc.field_1724, 0.0));
         if (!entries.isEmpty()) {
            class_332 ctx = event.getContext();
            HudClientModule hudClient = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
            int popColor = this.resolvePopColor();
            int lineHeight = 9;
            int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
            int baseY = this.blockTop(HudPosition.BOTTOM_RIGHT, 1, linesBelow, 0);
            int i = entries.size() - 1;

            for (int row = 0; i >= 0; row++) {
               RadarHudModule.Entry entry = entries.get(i);
               String name = entry.player.method_7334().name();
               int pops = Homovore.playerInfoManager.getTotemPops(entry.player.method_5667());
               String popsStr = pops > 0 ? " -" + pops : "";
               int nameW = mc.field_1772.method_1727(name);
               int popsW = mc.field_1772.method_1727(popsStr);
               int totalW = nameW + popsW;
               int y = baseY - row * lineHeight;
               int x = this.lineX(HudPosition.BOTTOM_RIGHT, totalW);
               int nameColor = this.resolveNameColor(hudClient, entry.player);
               if (entry.player == mc.field_1724) {
                  int logoSize = lineHeight;
                  int logoX = x - logoSize - 3;
                  int logoY = y + (lineHeight - logoSize) / 2;
                  ctx.method_25293(class_10799.field_56883, LOGO, logoX, logoY, 0.0F, 0.0F, logoSize, logoSize, 128, 128, 128, 128, -1);
               }

               ctx.method_25303(mc.field_1772, name, x, y, nameColor);
               if (!popsStr.isEmpty()) {
                  ctx.method_25303(mc.field_1772, popsStr, x + nameW, y, popColor);
               }

               i--;
            }
         }
      }
   }

   private int resolveNameColor(HudClientModule hudClient, class_1657 player) {
      if (hudClient == null) {
         return -1;
      } else if (player == mc.field_1724) {
         return hudClient.radarSelfColor.getValue().getRGB();
      } else {
         return Homovore.friendManager.isFriend(player) ? hudClient.radarFriendColor.getValue().getRGB() : hudClient.radarEnemyColor.getValue().getRGB();
      }
   }

   private int resolvePopColor() {
      NametagsModule nametags = Homovore.moduleManager.getModuleByClass(NametagsModule.class);
      return nametags != null && nametags.popColor.getValue() != null ? nametags.popColor.getValue().getRGB() : -44976;
   }

   private record Entry(class_1657 player, double dist) {
   }
}
