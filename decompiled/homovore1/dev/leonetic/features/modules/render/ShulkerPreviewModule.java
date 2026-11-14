package dev.leonetic.features.modules.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import java.awt.Color;
import java.util.List;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_2371;
import net.minecraft.class_2480;
import net.minecraft.class_332;
import net.minecraft.class_9288;
import net.minecraft.class_9334;
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

   public boolean renderPreview(class_332 graphics, class_1799 stack, int mouseX, int mouseY) {
      if (!isShulkerBox(stack)) {
         return false;
      }

      class_9288 contents = (class_9288)stack.method_58694(class_9334.field_49622);
      if (contents == null) {
         return false;
      }

      class_2371<class_1799> items = class_2371.method_10213(27, class_1799.field_8037);
      contents.method_57492(items);
      if (this.hideEmpty.getValue() && items.stream().allMatch(class_1799::method_7960)) {
         return false;
      }

      int width = 168;
      int height = 60;
      int x = clamp(mouseX + 12, 2, graphics.method_51421() - width - 2);
      int y = clamp(mouseY - 12, 2, graphics.method_51443() - height - 2);
      graphics.method_71048();
      graphics.method_25294(x - 1, y - 1, x + width + 1, y + height + 1, this.border.getValue().getRGB());
      graphics.method_25294(x, y, x + width, y + height, this.background.getValue().getRGB());

      for (int i = 0; i < 27; i++) {
         class_1799 item = (class_1799)items.get(i);
         if (!item.method_7960()) {
            int ix = x + 3 + i % 9 * 18 + 1;
            int iy = y + 3 + i / 9 * 18 + 1;
            graphics.method_51427(item, ix, iy);
            graphics.method_51431(mc.field_1772, item, ix, iy);
         }
      }

      return true;
   }

   public void renderThumbnail(class_332 graphics, class_1799 stack, int slotX, int slotY) {
      if (this.thumbnail.getValue() != ShulkerPreviewModule.Thumbnail.Off) {
         if (isShulkerBox(stack)) {
            class_9288 contents = (class_9288)stack.method_58694(class_9334.field_49622);
            if (contents != null) {
               class_1799 icon = this.selectThumbnail(contents);
               if (!icon.method_7960()) {
                  float scale = this.thumbnailScale.getValue().floatValue();
                  float offset = 8.0F * (1.0F - scale);
                  Matrix3x2fStack pose = graphics.method_51448();
                  pose.pushMatrix();
                  pose.translate(slotX + offset, slotY + offset);
                  pose.scale(scale, scale);
                  graphics.method_51427(icon, 0, 0);
                  if (this.thumbnailCount.getValue()) {
                     graphics.method_51431(mc.field_1772, icon, 0, 0);
                  }

                  pose.popMatrix();
               }
            }
         }
      }
   }

   private class_1799 selectThumbnail(class_9288 contents) {
      class_1799 best = class_1799.field_8037;
      int bestCount = -1;

      for (class_1799 item : contents.method_59714()) {
         if (!item.method_7960()) {
            if (this.thumbnail.getValue() == ShulkerPreviewModule.Thumbnail.First) {
               return item;
            }

            if (item.method_7947() > bestCount) {
               bestCount = item.method_7947();
               best = item;
            }
         }
      }

      return best;
   }

   public void renderShulkerList(class_332 graphics, List<class_1799> shulkers, int containerLeft) {
      if (!shulkers.isEmpty()) {
         int panelW = 150;
         int gridInnerH = 48;
         int panelH = 6 + (this.listNames.getValue() ? 11 : 0) + gridInnerH;
         int cols = Math.min(5, shulkers.size());
         int rows = (shulkers.size() + cols - 1) / cols;
         int neededW = cols * panelW + (cols - 1) * 4;
         int neededH = rows * panelH + (rows - 1) * 4;
         int availW = containerLeft - 8;
         int availH = graphics.method_51443() - 8;
         if (availW > 0) {
            float scale = Math.min(1.0F, Math.min((float)availW / neededW, (float)availH / neededH));
            float stepX = (panelW + 4) * scale;
            float stepY = (panelH + 4) * scale;
            graphics.method_71048();
            Matrix3x2fStack pose = graphics.method_51448();

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

   private void drawListPanel(class_332 graphics, class_1799 shulker, int panelW, int panelH) {
      graphics.method_25294(-1, -1, panelW + 1, panelH + 1, this.border.getValue().getRGB());
      graphics.method_25294(0, 0, panelW, panelH, this.background.getValue().getRGB());
      int gridY = 3;
      if (this.listNames.getValue()) {
         String name = shulker.method_7964().getString();
         graphics.method_25303(mc.field_1772, mc.field_1772.method_27523(name, panelW - 6), 3, 3, -1);
         gridY += 11;
      }

      class_9288 contents = (class_9288)shulker.method_58694(class_9334.field_49622);
      if (contents != null) {
         class_2371<class_1799> items = class_2371.method_10213(27, class_1799.field_8037);
         contents.method_57492(items);

         for (int i = 0; i < items.size(); i++) {
            class_1799 item = (class_1799)items.get(i);
            if (!item.method_7960()) {
               int ix = 3 + i % 9 * 16;
               int iy = gridY + i / 9 * 16;
               graphics.method_51427(item, ix, iy);
               graphics.method_51431(mc.field_1772, item, ix, iy);
            }
         }
      }
   }

   public static boolean isShulkerBox(class_1799 stack) {
      return stack.method_7909() instanceof class_1747 blockItem && blockItem.method_7711() instanceof class_2480;
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
