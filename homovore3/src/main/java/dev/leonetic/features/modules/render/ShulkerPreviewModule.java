package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import java.awt.Color;
import java.util.List;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.joml.Matrix3x2fStack;

public class ShulkerPreviewModule extends Module {
   private static final int COLUMNS = 9;
   private static final int ROWS = 3;
   private static final int SLOTS = 27;
   private static final int CELL = 18;
   private static final int PADDING = 3;
   private static final int BORDER = 1;
   private static final int CURSOR_GAP = 12;
   private static final int SCREEN_MARGIN = 2;
   public final Setting<Color> background = this.color("Background", 16, 16, 20, 240);
   public final Setting<Color> border = this.color("Border", 130, 80, 255, 255);
   public final Setting<Boolean> hideEmpty = this.bool("HideEmpty", false);
   public final Setting<ShulkerPreviewModule.Thumbnail> thumbnail = this.mode("Thumbnail", ShulkerPreviewModule.Thumbnail.Off);
   public final Setting<Double> thumbnailScale = this.num("ThumbnailScale", 0.6, 0.3, 1.0)
      .setVisibility(v -> this.thumbnail.getValue() != ShulkerPreviewModule.Thumbnail.Off);
   public final Setting<Boolean> thumbnailCount = this.bool("ThumbnailCount", true)
      .setVisibility(v -> this.thumbnail.getValue() != ShulkerPreviewModule.Thumbnail.Off);
   public final Setting<Boolean> shulkerList = this.bool("ShulkerList", false);
   public final Setting<Boolean> listNames = this.bool("ListNames", true).setVisibility(v -> this.shulkerList.getValue());
   private static final int LIST_COLS = 9;
   private static final int LIST_ROWS = 3;
   private static final int LIST_CELL = 16;
   private static final int LIST_PAD = 3;
   private static final int LIST_HEADER = 11;
   private static final int LIST_GAP = 4;
   private static final int LIST_MARGIN = 4;
   private static final int LIST_GRID_COLS = 5;

   public ShulkerPreviewModule() {
      super("ShulkerPreview", "Shows the contents of a shulker box when you hover it", Module.Category.RENDER);
   }

   public static ShulkerPreviewModule get() {
      return Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(ShulkerPreviewModule.class);
   }

   public boolean renderPreview(DrawContext graphics, ItemStack stack, int mouseX, int mouseY) {
      if (!isShulkerBox(stack)) {
         return false;
      }

      ContainerComponent contents = (ContainerComponent)stack.get(DataComponentTypes.CONTAINER);
      if (contents == null) {
         return false;
      }

      DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
      contents.copyTo(items);
      if (this.hideEmpty.getValue() && items.stream().allMatch(ItemStack::isEmpty)) {
         return false;
      }

      int width = 168;
      int height = 60;
      int x = clamp(mouseX + 12, 2, graphics.getScaledWindowWidth() - width - 2);
      int y = clamp(mouseY - 12, 2, graphics.getScaledWindowHeight() - height - 2);
      graphics.createNewRootLayer();
      graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, this.border.getValue().getRGB());
      graphics.fill(x, y, x + width, y + height, this.background.getValue().getRGB());

      for (int i = 0; i < 27; i++) {
         ItemStack item = (ItemStack)items.get(i);
         if (!item.isEmpty()) {
            int ix = x + 3 + i % 9 * 18 + 1;
            int iy = y + 3 + i / 9 * 18 + 1;
            graphics.drawItem(item, ix, iy);
            graphics.drawStackOverlay(mc.textRenderer, item, ix, iy);
         }
      }

      return true;
   }

   public void renderThumbnail(DrawContext graphics, ItemStack stack, int slotX, int slotY) {
      if (this.thumbnail.getValue() != ShulkerPreviewModule.Thumbnail.Off) {
         if (isShulkerBox(stack)) {
            ContainerComponent contents = (ContainerComponent)stack.get(DataComponentTypes.CONTAINER);
            if (contents != null) {
               ItemStack icon = this.selectThumbnail(contents);
               if (!icon.isEmpty()) {
                  float scale = this.thumbnailScale.getValue().floatValue();
                  float offset = 8.0F * (1.0F - scale);
                  Matrix3x2fStack pose = graphics.getMatrices();
                  pose.pushMatrix();
                  pose.translate(slotX + offset, slotY + offset);
                  pose.scale(scale, scale);
                  graphics.drawItem(icon, 0, 0);
                  if (this.thumbnailCount.getValue()) {
                     graphics.drawStackOverlay(mc.textRenderer, icon, 0, 0);
                  }

                  pose.popMatrix();
               }
            }
         }
      }
   }

   private ItemStack selectThumbnail(ContainerComponent contents) {
      ItemStack best = ItemStack.EMPTY;
      int bestCount = -1;

      for (ItemStack item : contents.iterateNonEmpty()) {
         if (!item.isEmpty()) {
            if (this.thumbnail.getValue() == ShulkerPreviewModule.Thumbnail.First) {
               return item;
            }

            if (item.getCount() > bestCount) {
               bestCount = item.getCount();
               best = item;
            }
         }
      }

      return best;
   }

   public void renderShulkerList(DrawContext graphics, List<ItemStack> shulkers, int containerLeft) {
      if (!shulkers.isEmpty()) {
         int panelW = 150;
         int gridInnerH = 48;
         int panelH = 6 + (this.listNames.getValue() ? 11 : 0) + gridInnerH;
         int cols = Math.min(5, shulkers.size());
         int rows = (shulkers.size() + cols - 1) / cols;
         int neededW = cols * panelW + (cols - 1) * 4;
         int neededH = rows * panelH + (rows - 1) * 4;
         int availW = containerLeft - 8;
         int availH = graphics.getScaledWindowHeight() - 8;
         if (availW > 0) {
            float scale = Math.min(1.0F, Math.min((float)availW / neededW, (float)availH / neededH));
            float stepX = (panelW + 4) * scale;
            float stepY = (panelH + 4) * scale;
            graphics.createNewRootLayer();
            Matrix3x2fStack pose = graphics.getMatrices();

            for (int i = 0; i < shulkers.size(); i++) {
               int col = i % cols;
               int row = i / cols;
               float x = 4.0F + col * stepX;
               float y = 4.0F + row * stepY;
               pose.pushMatrix();
               pose.translate(x, y);
               pose.scale(scale, scale);
               this.drawListPanel(graphics, shulkers.get(i), panelW, panelH);
               pose.popMatrix();
            }
         }
      }
   }

   private void drawListPanel(DrawContext graphics, ItemStack shulker, int panelW, int panelH) {
      graphics.fill(-1, -1, panelW + 1, panelH + 1, this.border.getValue().getRGB());
      graphics.fill(0, 0, panelW, panelH, this.background.getValue().getRGB());
      int gridY = 3;
      if (this.listNames.getValue()) {
         String name = shulker.getName().getString();
         graphics.drawTextWithShadow(mc.textRenderer, mc.textRenderer.trimToWidth(name, panelW - 6), 3, 3, -1);
         gridY += 11;
      }

      ContainerComponent contents = (ContainerComponent)shulker.get(DataComponentTypes.CONTAINER);
      if (contents != null) {
         DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
         contents.copyTo(items);

         for (int i = 0; i < items.size(); i++) {
            ItemStack item = (ItemStack)items.get(i);
            if (!item.isEmpty()) {
               int ix = 3 + i % 9 * 16;
               int iy = gridY + i / 9 * 16;
               graphics.drawItem(item, ix, iy);
               graphics.drawStackOverlay(mc.textRenderer, item, ix, iy);
            }
         }
      }
   }

   public static boolean isShulkerBox(ItemStack stack) {
      return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
   }

   private static int clamp(int value, int min, int max) {
      return max < min ? min : Math.max(min, Math.min(max, value));
   }

   public enum Thumbnail {
      Off,
      MostCommon,
      First;
   }
}
