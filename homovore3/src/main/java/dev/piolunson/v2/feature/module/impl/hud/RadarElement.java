package dev.piolunson.v2.feature.module.impl.hud;

import dev.piolunson.v2.Homovore2;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.impl.HudModule;
import dev.piolunson.v2.feature.module.impl.NametagsModule;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.manager.PotionEffectManager;
import dev.piolunson.v2.render.Nametag;
import dev.piolunson.v2.render.PlayerBadges;
import dev.piolunson.v2.render.Render2D;
import dev.piolunson.v2.util.world.PlayerGear;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public final class RadarElement extends StackedElement {
   private static final Identifier LOGO = Homovore2.id("textures/font/logo.png");
   private static final int LOGO_TEXTURE_SIZE = 128;
   private static final int LOGO_GAP = 3;
   private static final int POTION_GAP = 2;
   private static final int DEFAULT_POP_COLOR = -44976;
   public final Setting<Color> selfColor = this.register(new Setting<>("Self", new Color(255, 255, 255, 255))).describe("The player's own row.");
   public final Setting<Color> playerColor = this.register(new Setting<>("Players", new Color(255, 255, 255, 255)))
      .describe("Every other player on the radar.");
   public final Setting<Color> friendColor = this.register(new Setting<>("Friends", new Color(0, 255, 100, 255))).describe("Players on the friends list.");
   public final Setting<Color> enemyColor = this.register(new Setting<>("Enemies", new Color(255, 60, 60, 255))).describe("Players on the enemies list.");
   public final Setting<Boolean> gearedOnly = this.register(new Setting<>("Geared Only", false))
      .describe("Only lists players in a full set of diamond or netherite armour.");
   public final Setting<Boolean> showSelf = this.register(new Setting<>("Show Self", true)).describe("Keeps the player's own row at the bottom of the list.");
   public final Setting<Boolean> badges = this.register(new Setting<>("Badges", true))
      .describe("Marks players a plugin has badged, such as IRC members, with the logo.")
      .visibleWhen(PlayerBadges::available);
   public final Setting<Boolean> potions = this.register(new Setting<>("Potions", true))
      .describe("Shows the combat potions each player is under, as on their nametag.");
   public final Setting<Color> potionColor = this.register(new Setting<>("Potion", new Color(255, 255, 255, 255)))
      .describe("The timer beside each potion icon.")
      .visibleWhen(() -> this.potions.getValue());
   public final Setting<Integer> maxRows = this.register(new Setting<>("Max Rows", 12, 1, 40)).describe("How many players the radar grows to before it stops.");

   private static int potionSize() {
      return Render2D.lineHeight();
   }

   public RadarElement() {
      super("Radar", true, HudPosition.BOTTOM_RIGHT, 0);
   }

   @Override
   public int width() {
      int widest = 0;

      for (PlayerEntity player : this.rows()) {
         widest = Math.max(widest, this.rowWidth(player));
      }

      return widest;
   }

   @Override
   public int height() {
      return this.rows().size() * Render2D.lineHeight();
   }

   @Override
   public void render(DrawContext graphics, HudModule hud, int left, int top, int width) {
      List<PlayerEntity> rows = this.rows();
      if (!rows.isEmpty()) {
         int lineHeight = Render2D.lineHeight();
         int logoSize = Render2D.capHeight();
         float logoTopOffset = Render2D.capTopOffset();
         int popColor = this.popColor();
         int y = top;

         for (PlayerEntity player : rows) {
            boolean self = player == mc.player;
            String name = player.getGameProfile().name();
            String popText = popText(player);
            int nameWidth = Render2D.width(name);
            int x = this.rowLeft(left, width, this.rowWidth(player));
            if (this.hasLogo(player)) {
               Render2D.texture(graphics, LOGO, x, y + logoTopOffset, logoSize, logoSize, 128, 128, -1);
               x += logoSize + 3;
            }

            Render2D.text(graphics, name, x, y, this.nameColor(player, self).getRGB());
            x += nameWidth;
            if (!popText.isEmpty()) {
               Render2D.text(graphics, popText, x, y, popColor);
               x += Render2D.width(popText);
            }

            List<PotionEffectManager.Icon> effects = this.effects(player);
            if (!effects.isEmpty()) {
               Nametag.drawPotions(graphics, effects, x + 2, y, potionSize(), this.potionColor.getValue().getRGB(), false);
            }

            y += lineHeight;
         }
      }
   }

   private List<PlayerEntity> rows() {
      if (mc.player != null && mc.world != null) {
         List<PlayerEntity> players = new ArrayList<>();
         UUID self = mc.player.getUuid();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (!player.getUuid().equals(self) && player.isAlive() && (!this.gearedOnly.getValue() || PlayerGear.isGeared(player))) {
               players.add(player);
            }
         }

         players.sort(Comparator.comparingDouble(playerx -> -mc.player.getEntityPos().distanceTo(playerx.getEntityPos())));
         int room = Math.max(0, this.maxRows.getValue() - (this.showSelf.getValue() ? 1 : 0));
         if (players.size() > room) {
            players = new ArrayList<>(players.subList(players.size() - room, players.size()));
         }

         if (this.showSelf.getValue()) {
            players.add(mc.player);
         }

         return players;
      } else {
         return List.of();
      }
   }

   private Color nameColor(PlayerEntity player, boolean self) {
      if (self) {
         return this.selfColor.getValue();
      } else if (Homovore2Client.enemies().isEnemy(player)) {
         return this.enemyColor.getValue();
      } else {
         return Homovore2Client.friends().isFriend(player) ? this.friendColor.getValue() : this.playerColor.getValue();
      }
   }

   private int rowWidth(PlayerEntity player) {
      int width = Render2D.width(player.getGameProfile().name()) + Render2D.width(popText(player));
      if (this.hasLogo(player)) {
         width += Render2D.capHeight() + 3;
      }

      List<PotionEffectManager.Icon> effects = this.effects(player);
      if (!effects.isEmpty()) {
         width += 2 + Nametag.potionsWidth(effects, potionSize());
      }

      return width;
   }

   private List<PotionEffectManager.Icon> effects(PlayerEntity player) {
      return this.potions.getValue() ? Homovore2Client.potions().icons(player) : List.of();
   }

   private boolean hasLogo(PlayerEntity player) {
      if (player == mc.player) {
         return true;
      } else if (this.badges.getValue() && PlayerBadges.available()) {
         PlayerBadges.Badge badge = PlayerBadges.get(player.getUuid(), player.getGameProfile().name());
         return badge != null && badge.logo();
      } else {
         return false;
      }
   }

   private static String popText(PlayerEntity player) {
      int pops = Homovore2Client.totemPops().getPops(player.getUuid());
      return pops > 0 ? " -" + pops : "";
   }

   private int popColor() {
      NametagsModule nametags = Homovore2Client.modules().get(NametagsModule.class);
      return nametags == null ? -44976 : nametags.totemColor.getValue().getRGB();
   }
}
