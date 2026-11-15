package dev.piolunson.features.modules.hud;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.features.modules.client.HudClientModule;
import dev.piolunson.features.modules.client.HudModule;
import dev.piolunson.features.modules.client.HudPosition;
import dev.piolunson.features.modules.render.NametagsModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public class RadarHudModule extends HudModule {
   private static final int DEFAULT_POP_COLOR = -44976;
   private static final Identifier LOGO = Identifier.of("homovore", "textures/font/logo_white.png");
   private static final int LOGO_TEX_W = 128;
   private static final int LOGO_TEX_H = 128;
   private static final int LOGO_ALPHA_WHITE = -1;
   private static final int LOGO_GAP = 3;

   public RadarHudModule() {
      super("Radar");
   }

   public int renderedLineCount() {
      if (mc.player != null && mc.world != null) {
         int count = 1;
         UUID selfId = mc.player.getUuid();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (!player.getUuid().equals(selfId) && player.isAlive()) {
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
      if (mc.player != null && mc.world != null) {
         List<RadarHudModule.Entry> entries = new ArrayList<>();
         UUID selfId = mc.player.getUuid();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (!player.getUuid().equals(selfId) && player.isAlive()) {
               double dist = mc.player.getEntityPos().distanceTo(player.getEntityPos());
               entries.add(new RadarHudModule.Entry(player, dist));
            }
         }

         entries.sort(Comparator.<RadarHudModule.Entry>comparingDouble(e -> e.dist).reversed());
         entries.add(new RadarHudModule.Entry(mc.player, 0.0));
         if (!entries.isEmpty()) {
            DrawContext ctx = event.getContext();
            HudClientModule hudClient = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
            int popColor = this.resolvePopColor();
            int lineHeight = 9;
            int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
            int baseY = this.blockTop(HudPosition.BOTTOM_RIGHT, 1, linesBelow, 0);
            int i = entries.size() - 1;

            for (int row = 0; i >= 0; row++) {
               RadarHudModule.Entry entry = entries.get(i);
               String name = entry.player.getGameProfile().name();
               int pops = Homovore.playerInfoManager.getTotemPops(entry.player.getUuid());
               String popsStr = pops > 0 ? " -" + pops : "";
               int nameW = mc.textRenderer.getWidth(name);
               int popsW = mc.textRenderer.getWidth(popsStr);
               int totalW = nameW + popsW;
               int y = baseY - row * lineHeight;
               int x = this.lineX(HudPosition.BOTTOM_RIGHT, totalW);
               int nameColor = this.resolveNameColor(hudClient, entry.player);
               if (entry.player == mc.player) {
                  int logoSize = lineHeight;
                  int logoX = x - logoSize - 3;
                  int logoY = y + (lineHeight - logoSize) / 2;
                  ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0.0F, 0.0F, logoSize, logoSize, 128, 128, 128, 128, -1);
               }

               ctx.drawTextWithShadow(mc.textRenderer, name, x, y, nameColor);
               if (!popsStr.isEmpty()) {
                  ctx.drawTextWithShadow(mc.textRenderer, popsStr, x + nameW, y, popColor);
               }

               i--;
            }
         }
      }
   }

   private int resolveNameColor(HudClientModule hudClient, PlayerEntity player) {
      if (hudClient == null) {
         return -1;
      } else if (player == mc.player) {
         return hudClient.radarSelfColor.getValue().getRGB();
      } else {
         return Homovore.friendManager.isFriend(player) ? hudClient.radarFriendColor.getValue().getRGB() : hudClient.radarEnemyColor.getValue().getRGB();
      }
   }

   private int resolvePopColor() {
      NametagsModule nametags = Homovore.moduleManager.getModuleByClass(NametagsModule.class);
      return nametags != null && nametags.popColor.getValue() != null ? nametags.popColor.getValue().getRGB() : -44976;
   }

   private record Entry(PlayerEntity player, double dist) {
   }
}
