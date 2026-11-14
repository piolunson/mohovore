package dev.leonetic.v2.feature.module.impl.hud;

import dev.leonetic.v2.feature.module.impl.HudModule;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class CountsElement extends AnchoredElement {
   private static final int ICON = 16;
   private static final float ICON_SCALE = 0.85F;
   private static final float DRAWN_ICON = 13.6F;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final int GAP = 2;
   private static final int TEXT_PAD = 2;
   private static final Item[] ITEMS = new Item[]{Items.END_CRYSTAL, Items.OBSIDIAN, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE};
   public final Setting<Color> emptyColor = this.register(new Setting<>("Empty", new Color(255, 85, 85, 255))).describe("A supply that has run out.");

   public CountsElement() {
      super("Counts", true);
   }

   @Override
   public void render(DrawContext graphics, HudModule hud) {
      int x = graphics.getScaledWindowWidth() / 2 + 91 + 2;
      int top = graphics.getScaledWindowHeight() - 39 - 1 - 16;
      if (showingBubbles()) {
         top -= 10;
      }

      for (int i = 0; i < ITEMS.length; i++) {
         int y = top + Math.round(i * 13.6F);
         int count = HudItems.count(ITEMS[i]);
         ItemStack stack = new ItemStack(ITEMS[i], Math.max(count, 1));
         graphics.getMatrices().pushMatrix();
         graphics.getMatrices().scale(0.85F, 0.85F);
         graphics.drawItem(stack, Math.round(x / 0.85F), Math.round(y / 0.85F));
         graphics.getMatrices().popMatrix();
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
