package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_332;

public final class CountsElement extends AnchoredElement {
   private static final int ICON = 16;
   private static final float ICON_SCALE = 0.85F;
   private static final float DRAWN_ICON = 13.6F;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final int GAP = 2;
   private static final int TEXT_PAD = 2;
   private static final class_1792[] ITEMS = new class_1792[]{class_1802.field_8301, class_1802.field_8281, class_1802.field_8634, class_1802.field_8287};
   public final Setting<Color> emptyColor = this.register(new Setting<>("Empty", new Color(255, 85, 85, 255))).describe("A supply that has run out.");

   public CountsElement() {
      super("Counts", true);
   }

   @Override
   public void render(class_332 graphics, HudModule hud) {
      int x = graphics.method_51421() / 2 + 91 + 2;
      int top = graphics.method_51443() - 39 - 1 - 16;
      if (showingBubbles()) {
         top -= 10;
      }

      for (int i = 0; i < ITEMS.length; i++) {
         int y = top + Math.round(i * 13.6F);
         int count = HudItems.count(ITEMS[i]);
         class_1799 stack = new class_1799(ITEMS[i], Math.max(count, 1));
         graphics.method_51448().pushMatrix();
         graphics.method_51448().scale(0.85F, 0.85F);
         graphics.method_51427(stack, Math.round(x / 0.85F), Math.round(y / 0.85F));
         graphics.method_51448().popMatrix();
         Render2D.text(
            graphics,
            String.valueOf(count),
            Math.round(x + 13.6F + 2.0F),
            Math.round(y + (13.6F - Render2D.textHeight()) / 2.0F),
            count > 0 ? -1 : this.emptyColor.getValue().getRGB(),
            true
         );
      }
   }
}
