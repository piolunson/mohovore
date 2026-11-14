package dev.leonetic.features.modules.hud;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudModule;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_332;
import net.minecraft.class_3486;

public class CountsHudModule extends HudModule {
   private static final int ICON = 16;
   private static final float ICON_SCALE = 0.85F;
   private static final float RENDER_ICON = 13.6F;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final int GAP = 2;
   private static final int TEXT_PAD = 2;
   private static final class_1792[] ITEMS = new class_1792[]{class_1802.field_8301, class_1802.field_8281, class_1802.field_8634, class_1802.field_8287};
   private static final int GLYPH_HEIGHT = 8;

   public CountsHudModule() {
      super("Counts");
   }

   @Override
   public void render(Render2DEvent event) {
      class_332 ctx = event.getContext();
      int x = this.screenWidth() / 2 + 91 + 2;
      int yTop = this.screenHeight() - 39 - 1 - 16;
      if (this.showingBubbles()) {
         yTop -= 10;
      }

      for (int i = 0; i < ITEMS.length; i++) {
         int y = yTop + Math.round(i * 13.6F);
         int count = this.countItem(ITEMS[i]);
         class_1799 stack = new class_1799(ITEMS[i], Math.max(count, 1));
         this.drawIcon(ctx, stack, x, y);
         this.drawCount(ctx, count, x, y);
      }
   }

   private void drawIcon(class_332 ctx, class_1799 stack, int x, int y) {
      ctx.method_51448().pushMatrix();
      ctx.method_51448().scale(0.85F, 0.85F);
      ctx.method_51427(stack, Math.round(x / 0.85F), Math.round(y / 0.85F));
      ctx.method_51448().popMatrix();
   }

   private void drawCount(class_332 ctx, int count, int x, int y) {
      String label = String.valueOf(count);
      int drawX = Math.round(x + 13.6F + 2.0F);
      int drawY = Math.round(y + 2.8000002F);
      ctx.method_51433(mc.field_1772, label, drawX, drawY, count > 0 ? -1 : -43691, true);
   }

   private int countItem(class_1792 item) {
      int total = 0;
      class_1661 inv = mc.field_1724.method_31548();

      for (int i = 0; i < inv.method_5439(); i++) {
         class_1799 s = inv.method_5438(i);
         if (s.method_31574(item)) {
            total += s.method_7947();
         }
      }

      return total;
   }

   private boolean showingBubbles() {
      return mc.field_1724.method_5777(class_3486.field_15517) || mc.field_1724.method_5669() < mc.field_1724.method_5748();
   }
}
