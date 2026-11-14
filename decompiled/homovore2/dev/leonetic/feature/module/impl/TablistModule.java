package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.PlayerBadges;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_2561;
import net.minecraft.class_640;

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

   public List<class_640> filter(List<class_640> infos) {
      Map<TablistModule.Group, List<class_640>> buckets = new EnumMap<>(TablistModule.Group.class);

      for (class_640 info : infos) {
         TablistModule.Group group = this.groupOf(info);
         if (group != null) {
            buckets.computeIfAbsent(group, key -> new ArrayList<>()).add(info);
         }
      }

      List<class_640> out = new ArrayList<>(infos.size());

      for (TablistModule.Group group : TablistModule.Group.values()) {
         out.addAll(buckets.getOrDefault(group, List.of()));
      }

      return out;
   }

   public class_2561 decorate(class_640 info, class_2561 name) {
      if (!this.colorNames.getValue()) {
         return name;
      }

      TablistModule.Group group = this.groupOf(info);
      return (class_2561)(group == null ? name : name.method_27661().method_54663(this.colorOf(group).getRGB() & 16777215));
   }

   private TablistModule.Group groupOf(class_640 info) {
      String name = info.method_2966().name();
      if (name == null) {
         return null;
      } else if (name.equals(mc.method_53462().name())) {
         return this.showSelf.getValue() ? TablistModule.Group.SELF : null;
      } else if (Homovore2Client.enemies().isEnemy(name)) {
         return this.showEnemies.getValue() ? TablistModule.Group.ENEMY : null;
      } else if (Homovore2Client.friends().isFriend(name)) {
         return this.showFriends.getValue() ? TablistModule.Group.FRIEND : null;
      } else {
         return this.showBadged.getValue() && PlayerBadges.available() && PlayerBadges.get(info.method_2966().id(), name) != null
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
