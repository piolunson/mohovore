package dev.piolunson.features.modules.render;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import java.awt.Color;
import net.minecraft.client.network.PlayerListEntry;

public class TablistModule extends Module {
   public Setting<Boolean> friends = this.bool("Friends", true);
   public Setting<Boolean> enemies = this.bool("Enemies", true);
   public final Setting<Color> friendColor = this.color("Friend Color", 0, 255, 100, 255);
   public final Setting<Color> enemyColor = this.color("Enemy Color", 255, 0, 0, 255);

   public TablistModule() {
      super("Tablist", "Hides players in the tablist who aren't you, a friend, or an enemy", Module.Category.RENDER);
   }

   public boolean shouldShow(PlayerListEntry info) {
      String name = info.getProfile().name();
      if (name == null) {
         return false;
      } else if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name())) {
         return true;
      } else {
         return this.friends.getValue() && Homovore.friendManager.isFriend(name) ? true : this.enemies.getValue() && Homovore.enemyManager.isEnemy(name);
      }
   }
}
