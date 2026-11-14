package dev.leonetic.v2.feature.module.impl.hud;

import dev.leonetic.v2.feature.module.impl.HudModule;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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
   public void render(DrawContext graphics, HudModule hud) {
      int count = HudItems.count(Items.TOTEM_OF_UNDYING);
      int x = graphics.getScaledWindowWidth() / 2 - 8;
      int y = graphics.getScaledWindowHeight() - 32 - 6 - 16;
      ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING, Math.max(count, 1));
      graphics.drawItem(stack, x, y);
      if (count > 0) {
         graphics.drawStackOverlay(mc.textRenderer, stack, x, y, String.valueOf(count));
      } else {
         String label = "0";
         Render2D.text(graphics, label, x + 16 + 1 - Render2D.width(label), y + 16 + 1 - Render2D.textHeight(), this.emptyColor.getValue().getRGB());
      }
   }
}
