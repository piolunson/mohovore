package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_332;

public final class TotemsElement extends AnchoredElement {
   private static final int ICON = 16;
   private static final int XP_BAR_TOP_OFFSET = 32;
   private static final int GAP_ABOVE_XP = 6;
   public final Setting<Color> emptyColor = this.register(new Setting<>("Empty", new Color(255, 85, 85, 255)))
      .describe("The count once there is nothing left to pop.");

   public TotemsElement() {
      super("Totems", true);
   }

   @Override
   public void render(class_332 graphics, HudModule hud) {
      int count = HudItems.count(class_1802.field_8288);
      int x = graphics.method_51421() / 2 - 8;
      int y = graphics.method_51443() - 32 - 6 - 16;
      class_1799 stack = new class_1799(class_1802.field_8288, Math.max(count, 1));
      graphics.method_51427(stack, x, y);
      if (count > 0) {
         graphics.method_51432(mc.field_1772, stack, x, y, String.valueOf(count));
      } else {
         String label = "0";
         Render2D.text(graphics, label, x + 16 + 1 - Render2D.width(label), y + 16 + 1 - Render2D.textHeight(), this.emptyColor.getValue().getRGB());
      }
   }
}
