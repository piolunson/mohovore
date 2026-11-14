package dev.leonetic.features.modules.hud;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudModule;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_1802;

public class TotemsHudModule extends HudModule {
   private static final int ICON = 16;
   private static final int XP_BAR_TOP_OFFSET = 32;
   private static final int GAP_ABOVE_XP = 6;

   public TotemsHudModule() {
      super("Totems");
   }

   @Override
   public void render(Render2DEvent event) {
      int count = this.countTotems();
      int x = this.screenWidth() / 2 - 8;
      int y = this.screenHeight() - 32 - 6 - 16;
      class_1799 stack = new class_1799(class_1802.field_8288, Math.max(count, 1));
      event.getContext().method_51427(stack, x, y);
      if (count > 0) {
         event.getContext().method_51432(mc.field_1772, stack, x, y, String.valueOf(count));
      } else {
         String label = "0";
         int tx = x + 19 - 2 - mc.field_1772.method_1727(label);
         int ty = y + 6 + 3;
         event.getContext().method_25303(mc.field_1772, label, tx, ty, -43691);
      }
   }

   private int countTotems() {
      int total = 0;
      class_1661 inv = mc.field_1724.method_31548();

      for (int i = 0; i < inv.method_5439(); i++) {
         class_1799 s = inv.method_5438(i);
         if (s.method_31574(class_1802.field_8288)) {
            total += s.method_7947();
         }
      }

      return total;
   }
}
