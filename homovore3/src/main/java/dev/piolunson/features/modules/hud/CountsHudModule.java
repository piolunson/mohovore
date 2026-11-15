package dev.piolunson.features.modules.hud;

import dev.piolunson.event.impl.render.Render2DEvent;
import dev.piolunson.features.modules.client.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;

public class CountsHudModule extends HudModule {
   private static final int ICON = 16;
   private static final float ICON_SCALE = 0.85F;
   private static final float RENDER_ICON = 13.6F;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final int GAP = 2;
   private static final int TEXT_PAD = 2;
   private static final Item[] ITEMS = new Item[]{Items.END_CRYSTAL, Items.OBSIDIAN, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE};
   private static final int GLYPH_HEIGHT = 8;

   public CountsHudModule() {
      super("Counts");
   }

   @Override
   public void render(Render2DEvent event) {
      DrawContext ctx = event.getContext();
      int x = this.screenWidth() / 2 + 91 + 2;
      int yTop = this.screenHeight() - 39 - 1 - 16;
      if (this.showingBubbles()) {
         yTop -= 10;
      }

      for (int i = 0; i < ITEMS.length; i++) {
         int y = yTop + Math.round(i * 13.6F);
         int count = this.countItem(ITEMS[i]);
         ItemStack stack = new ItemStack(ITEMS[i], Math.max(count, 1));
         this.drawIcon(ctx, stack, x, y);
         this.drawCount(ctx, count, x, y);
      }
   }

   private void drawIcon(DrawContext ctx, ItemStack stack, int x, int y) {
      ctx.getMatrices().pushMatrix();
      ctx.getMatrices().scale(0.85F, 0.85F);
      ctx.drawItem(stack, Math.round(x / 0.85F), Math.round(y / 0.85F));
      ctx.getMatrices().popMatrix();
   }

   private void drawCount(DrawContext ctx, int count, int x, int y) {
      String label = String.valueOf(count);
      int drawX = Math.round(x + 13.6F + 2.0F);
      int drawY = Math.round(y + 2.8000002F);
      ctx.drawText(mc.textRenderer, label, drawX, drawY, count > 0 ? -1 : -43691, true);
   }

   private int countItem(Item item) {
      int total = 0;
      PlayerInventory inv = mc.player.getInventory();

      for (int i = 0; i < inv.size(); i++) {
         ItemStack s = inv.getStack(i);
         if (s.isOf(item)) {
            total += s.getCount();
         }
      }

      return total;
   }

   private boolean showingBubbles() {
      return mc.player.isSubmergedIn(FluidTags.WATER) || mc.player.getAir() < mc.player.getMaxAir();
   }
}
