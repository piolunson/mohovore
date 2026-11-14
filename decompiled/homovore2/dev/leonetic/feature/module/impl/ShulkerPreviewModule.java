package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.module.ModuleManager;
import dev.leonetic.feature.setting.Setting;
import java.awt.Color;
import java.util.Iterator;
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
   private static final int LIST_CELL = 16;
   private static final int LIST_PAD = 3;
   private static final int LIST_HEADER = 11;
   private static final int LIST_GAP = 4;
   private static final int LIST_MARGIN = 4;
   private static final int LIST_GRID_COLUMNS = 5;
   public final Setting<Color> background = this.color("Background", 16, 16, 20, 240);
   public final Setting<Color> border = this.color("Border", 130, 80, 255, 255);
   public final Setting<Boolean> hideEmpty = this.bool("HideEmpty", false).describe("Shows the vanilla tooltip for empty boxes instead of an empty grid.");
   public final Setting<ShulkerPreviewModule.Icon> icon = this.mode("Icon", ShulkerPreviewModule.Icon.MOST_COMMON)
      .describe("Draws an item from the box over its slot: the largest stack, or the first.");
   public final Setting<Double> iconScale = this.number("IconScale", (double)0.6, (double)0.3, (double)1.0)
      .visibleWhen(() -> this.icon.getValue() != ShulkerPreviewModule.Icon.OFF);
   public final Setting<Boolean> iconCount = this.bool("IconCount", true)
      .describe("Draws the icon's stack count.")
      .visibleWhen(() -> this.icon.getValue() != ShulkerPreviewModule.Icon.OFF);
   public final Setting<Boolean> shulkerList = this.bool("ShulkerList", false)
      .describe("Lays out every shulker in an open ender chest to the left of the screen.");
   public final Setting<Boolean> listNames = this.bool("ListNames", true).visibleWhen(this.shulkerList::getValue);

   public ShulkerPreviewModule() {
      super("ShulkerPreview", Category.RENDER, "Shows the contents of a shulker box when you hover it.");
   }

   public static ShulkerPreviewModule get() {
      ModuleManager modules = Homovore2Client.modules();
      return modules == null ? null : modules.get(ShulkerPreviewModule.class);
   }

   public boolean renderPreview(class_332 graphics, class_1799 stack, int mouseX, int mouseY) {
      class_9288 contents = contentsOf(stack);
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

   public void renderIcon(class_332 graphics, class_1799 stack, int slotX, int slotY) {
      if (this.icon.getValue() != ShulkerPreviewModule.Icon.OFF) {
         class_9288 contents = contentsOf(stack);
         if (contents != null) {
            class_1799 shown = this.selectIcon(contents);
            if (!shown.method_7960()) {
               float scale = this.iconScale.getValue().floatValue();
               float offset = 8.0F * (1.0F - scale);
               Matrix3x2fStack pose = graphics.method_51448();
               pose.pushMatrix();
               pose.translate(slotX + offset, slotY + offset);
               pose.scale(scale, scale);
               graphics.method_51427(shown, 0, 0);
               if (this.iconCount.getValue()) {
                  graphics.method_51431(mc.field_1772, shown, 0, 0);
               }

               pose.popMatrix();
            }
         }
      }
   }

   private class_1799 selectIcon(class_9288 contents) {
      Iterator<class_1799> items = contents.method_59712().iterator();
      if (this.icon.getValue() == ShulkerPreviewModule.Icon.FIRST) {
         return items.hasNext() ? items.next() : class_1799.field_8037;
      }

      class_1799 best = class_1799.field_8037;

      while (items.hasNext()) {
         class_1799 item = items.next();
         if (item.method_7947() > best.method_7947()) {
            best = item;
         }
      }

      return best;
   }

   public void renderShulkerList(class_332 graphics, List<class_1799> shulkers, int containerLeft) {
      if (!shulkers.isEmpty()) {
         int panelWidth = 150;
         int panelHeight = 6 + (this.listNames.getValue() ? 11 : 0) + 48;
         int columns = Math.min(5, shulkers.size());
         int rows = (shulkers.size() + columns - 1) / columns;
         int neededWidth = columns * panelWidth + (columns - 1) * 4;
         int neededHeight = rows * panelHeight + (rows - 1) * 4;
         int availableWidth = containerLeft - 8;
         int availableHeight = graphics.method_51443() - 8;
         if (availableWidth > 0) {
            float scale = Math.min(1.0F, Math.min((float)availableWidth / neededWidth, (float)availableHeight / neededHeight));
            float stepX = (panelWidth + 4) * scale;
            float stepY = (panelHeight + 4) * scale;
            graphics.method_71048();
            Matrix3x2fStack pose = graphics.method_51448();

            for (int i = 0; i < shulkers.size(); i++) {
               pose.pushMatrix();
               pose.translate(4.0F + i % columns * stepX, 4.0F + i / columns * stepY);
               pose.scale(scale, scale);
               this.drawListPanel(graphics, shulkers.get(i), panelWidth, panelHeight);
               pose.popMatrix();
            }
         }
      }
   }

   private void drawListPanel(class_332 graphics, class_1799 shulker, int width, int height) {
      graphics.method_25294(-1, -1, width + 1, height + 1, this.border.getValue().getRGB());
      graphics.method_25294(0, 0, width, height, this.background.getValue().getRGB());
      int gridY = 3;
      if (this.listNames.getValue()) {
         String name = shulker.method_7964().getString();
         graphics.method_25303(mc.field_1772, mc.field_1772.method_27523(name, width - 6), 3, 3, -1);
         gridY += 11;
      }

      class_9288 contents = (class_9288)shulker.method_58694(class_9334.field_49622);
      if (contents != null) {
         class_2371<class_1799> items = class_2371.method_10213(27, class_1799.field_8037);
         contents.method_57492(items);

         for (int i = 0; i < 27; i++) {
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

   private static class_9288 contentsOf(class_1799 stack) {
      return isShulkerBox(stack) ? (class_9288)stack.method_58694(class_9334.field_49622) : null;
   }

   private static int clamp(int value, int min, int max) {
      return max < min ? min : Math.max(min, Math.min(max, value));
   }

   public enum Icon {
      OFF,
      MOST_COMMON,
      FIRST;
   }
}
