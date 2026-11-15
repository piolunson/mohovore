package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.render.PlayerBadges;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

public class TablistModule extends Module {
   public final Setting<Boolean> showSelf = this.bool("Self", true).describe("Keeps your own row at the top of the list.");
   public final Setting<Boolean> showFriends = this.bool("Friends", true).describe("Lists players on the friends list.");
   public final Setting<Boolean> showEnemies = this.bool("Enemies", true).describe("Lists players on the enemies list.");
   public final Setting<Boolean> showBadged = this.bool("Badged", true)
      .describe("Lists players a plugin has badged, such as IRC members.")
      .visibleWhen(PlayerBadges::available);
   public final Setting<Boolean> colorNames = this.bool("Color Names", true)
      .describe("Paints each name in its group's colour instead of leaving the server's.");
   public final Setting<Color> selfColor = this.color("Self Color", 255, 255, 255, 255);
   public final Setting<Color> friendColor = this.color("Friend Color", 0, 105, 255, 255);
   public final Setting<Color> enemyColor = this.color("Enemy Color", 255, 0, 0, 255);
   public final Setting<Color> badgedColor = this.color("Badged Color", 255, 255, 255, 255);

   public TablistModule() {
      super("Tablist", Category.RENDER, "Shows only yourself, friends, badged players and enemies in the tab list.");
      this.badgedColor.visibleWhen(PlayerBadges::available);
   }

   public List<PlayerListEntry> filter(List<PlayerListEntry> infos) {
      Map<TablistModule.Group, List<PlayerListEntry>> buckets = new EnumMap<>(TablistModule.Group.class);

      for (PlayerListEntry info : infos) {
         TablistModule.Group group = this.groupOf(info);
         if (group != null) {
            buckets.computeIfAbsent(group, key -> new ArrayList<>()).add(info);
         }
      }

      List<PlayerListEntry> out = new ArrayList<>(infos.size());

      for (TablistModule.Group group : TablistModule.Group.values()) {
         out.addAll(buckets.getOrDefault(group, List.of()));
      }

      return out;
   }

   public Text decorate(PlayerListEntry info, Text name) {
      if (!this.colorNames.getValue()) {
         return name;
      }

      TablistModule.Group group = this.groupOf(info);
      return (Text)(group == null ? name : name.copy().withColor(this.colorOf(group).getRGB() & 16777215));
   }

   private TablistModule.Group groupOf(PlayerListEntry info) {
      String name = info.getProfile().name();
      if (name == null) {
         return null;
      } else if (name.equals(mc.getGameProfile().name())) {
         return this.showSelf.getValue() ? TablistModule.Group.SELF : null;
      } else if (Homovore2Client.enemies().isEnemy(name)) {
         return this.showEnemies.getValue() ? TablistModule.Group.ENEMY : null;
      } else if (Homovore2Client.friends().isFriend(name)) {
         return this.showFriends.getValue() ? TablistModule.Group.FRIEND : null;
      } else {
         return this.showBadged.getValue() && PlayerBadges.available() && PlayerBadges.get(info.getProfile().id(), name) != null
            ? TablistModule.Group.BADGED
            : null;
      }
   }

   private Color colorOf(TablistModule.Group group) {
      return switch (group) {
         case SELF -> (Color)this.selfColor.getValue();
         case FRIEND -> (Color)this.friendColor.getValue();
         case BADGED -> (Color)this.badgedColor.getValue();
         case ENEMY -> (Color)this.enemyColor.getValue();
      };
   }

   private enum Group {
      SELF,
      FRIEND,
      BADGED,
      ENEMY;
   }
}
