package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.Homovore2;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.module.impl.NametagsModule;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.PotionEffectManager;
import dev.leonetic.render.Nametag;
import dev.leonetic.render.PlayerBadges;
import dev.leonetic.render.Render2D;
import dev.leonetic.util.world.PlayerGear;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.class_1657;
import net.minecraft.class_2960;
import net.minecraft.class_332;

public final class RadarElement extends StackedElement {
   private static final class_2960 LOGO = Homovore2.id("textures/font/logo.png");
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

      for (class_1657 player : this.rows()) {
         widest = Math.max(widest, this.rowWidth(player));
      }

      return widest;
   }

   @Override
   public int height() {
      return this.rows().size() * Render2D.lineHeight();
   }

   @Override
   public void render(class_332 graphics, HudModule hud, int left, int top, int width) {
      List<class_1657> rows = this.rows();
      if (!rows.isEmpty()) {
         int lineHeight = Render2D.lineHeight();
         int logoSize = Render2D.capHeight();
         float logoTopOffset = Render2D.capTopOffset();
         int popColor = this.popColor();
         int y = top;

         for (class_1657 player : rows) {
            boolean self = player == mc.field_1724;
            String name = player.method_7334().name();
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

   private List<class_1657> rows() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         List<class_1657> players = new ArrayList<>();
         UUID self = mc.field_1724.method_5667();

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (!player.method_5667().equals(self) && player.method_5805() && (!this.gearedOnly.getValue() || PlayerGear.isGeared(player))) {
               players.add(player);
            }
         }

         players.sort(Comparator.comparingDouble(playerx -> -mc.field_1724.method_73189().method_1022(playerx.method_73189())));
         int room = Math.max(0, this.maxRows.getValue() - (this.showSelf.getValue() ? 1 : 0));
         if (players.size() > room) {
            players = new ArrayList<>(players.subList(players.size() - room, players.size()));
         }

         if (this.showSelf.getValue()) {
            players.add(mc.field_1724);
         }

         return players;
      } else {
         return List.of();
      }
   }

   private Color nameColor(class_1657 player, boolean self) {
      if (self) {
         return this.selfColor.getValue();
      } else if (Homovore2Client.enemies().isEnemy(player)) {
         return this.enemyColor.getValue();
      } else {
         return Homovore2Client.friends().isFriend(player) ? this.friendColor.getValue() : this.playerColor.getValue();
      }
   }

   private int rowWidth(class_1657 player) {
      int width = Render2D.width(player.method_7334().name()) + Render2D.width(popText(player));
      if (this.hasLogo(player)) {
         width += Render2D.capHeight() + 3;
      }

      List<PotionEffectManager.Icon> effects = this.effects(player);
      if (!effects.isEmpty()) {
         width += 2 + Nametag.potionsWidth(effects, potionSize());
      }

      return width;
   }

   private List<PotionEffectManager.Icon> effects(class_1657 player) {
      return this.potions.getValue() ? Homovore2Client.potions().icons(player) : List.of();
   }

   private boolean hasLogo(class_1657 player) {
      if (player == mc.field_1724) {
         return true;
      } else if (this.badges.getValue() && PlayerBadges.available()) {
         PlayerBadges.Badge badge = PlayerBadges.get(player.method_5667(), player.method_7334().name());
         return badge != null && badge.logo();
      } else {
         return false;
      }
   }

   private static String popText(class_1657 player) {
      int pops = Homovore2Client.totemPops().getPops(player.method_5667());
      return pops > 0 ? " -" + pops : "";
   }

   private int popColor() {
      NametagsModule nametags = Homovore2Client.modules().get(NametagsModule.class);
      return nametags == null ? -44976 : nametags.totemColor.getValue().getRGB();
   }
}
