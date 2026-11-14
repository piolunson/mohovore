package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import java.awt.Color;
import net.minecraft.class_640;

public class TablistModule extends Module {
   public Setting<Boolean> friends = this.bool("Friends", true);
   public Setting<Boolean> enemies = this.bool("Enemies", true);
   public final Setting<Color> friendColor = this.color("Friend Color", 0, 255, 100, 255);
   public final Setting<Color> enemyColor = this.color("Enemy Color", 255, 0, 0, 255);

   public TablistModule() {
      super("Tablist", "Hides players in the tablist who aren't you, a friend, or an enemy", Module.Category.RENDER);
   }

   public boolean shouldShow(class_640 info) {
      String name = info.method_2966().name();
      if (name == null) {
         return false;
      } else if (mc.field_1724 != null && name.equalsIgnoreCase(mc.field_1724.method_7334().name())) {
         return true;
      } else {
         return this.friends.getValue() && Homovore.friendManager.isFriend(name) ? true : this.enemies.getValue() && Homovore.enemyManager.isEnemy(name);
      }
   }
}
