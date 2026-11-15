package dev.piolunson.features.modules.hud;

import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.features.modules.client.HudModule;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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
      ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING, Math.max(count, 1));
      event.getContext().drawItem(stack, x, y);
      if (count > 0) {
         event.getContext().drawStackOverlay(mc.textRenderer, stack, x, y, String.valueOf(count));
      } else {
         String label = "0";
         int tx = x + 19 - 2 - mc.textRenderer.getWidth(label);
         int ty = y + 6 + 3;
         event.getContext().drawTextWithShadow(mc.textRenderer, label, tx, ty, -43691);
      }
   }

   private int countTotems() {
      int total = 0;
      PlayerInventory inv = mc.player.getInventory();

      for (int i = 0; i < inv.size(); i++) {
         ItemStack s = inv.getStack(i);
         if (s.isOf(Items.TOTEM_OF_UNDYING)) {
            total += s.getCount();
         }
      }

      return total;
   }
}
