package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.module.ModuleManager;
import dev.piolunson.v2.feature.setting.Setting;
import java.awt.Color;
import java.util.Iterator;
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

   public boolean renderPreview(DrawContext graphics, ItemStack stack, int mouseX, int mouseY) {
      ContainerComponent contents = contentsOf(stack);
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

   public void renderIcon(DrawContext graphics, ItemStack stack, int slotX, int slotY) {
      if (this.icon.getValue() != ShulkerPreviewModule.Icon.OFF) {
         ContainerComponent contents = contentsOf(stack);
         if (contents != null) {
            ItemStack shown = this.selectIcon(contents);
            if (!shown.isEmpty()) {
               float scale = this.iconScale.getValue().floatValue();
               float offset = 8.0F * (1.0F - scale);
               Matrix3x2fStack pose = graphics.getMatrices();
               pose.pushMatrix();
               pose.translate(slotX + offset, slotY + offset);
               pose.scale(scale, scale);
               graphics.drawItem(shown, 0, 0);
               if (this.iconCount.getValue()) {
                  graphics.drawStackOverlay(mc.textRenderer, shown, 0, 0);
               }

               pose.popMatrix();
            }
         }
      }
   }

   private ItemStack selectIcon(ContainerComponent contents) {
      Iterator<ItemStack> items = contents.streamNonEmpty().iterator();
      if (this.icon.getValue() == ShulkerPreviewModule.Icon.FIRST) {
         return items.hasNext() ? items.next() : ItemStack.EMPTY;
      }

      ItemStack best = ItemStack.EMPTY;

      while (items.hasNext()) {
         ItemStack item = items.next();
         if (item.getCount() > best.getCount()) {
            best = item;
         }
      }

      return best;
   }

   public void renderShulkerList(DrawContext graphics, List<ItemStack> shulkers, int containerLeft) {
      if (!shulkers.isEmpty()) {
         int panelWidth = 150;
         int panelHeight = 6 + (this.listNames.getValue() ? 11 : 0) + 48;
         int columns = Math.min(5, shulkers.size());
         int rows = (shulkers.size() + columns - 1) / columns;
         int neededWidth = columns * panelWidth + (columns - 1) * 4;
         int neededHeight = rows * panelHeight + (rows - 1) * 4;
         int availableWidth = containerLeft - 8;
         int availableHeight = graphics.getScaledWindowHeight() - 8;
         if (availableWidth > 0) {
            float scale = Math.min(1.0F, Math.min((float)availableWidth / neededWidth, (float)availableHeight / neededHeight));
            float stepX = (panelWidth + 4) * scale;
            float stepY = (panelHeight + 4) * scale;
            graphics.createNewRootLayer();
            Matrix3x2fStack pose = graphics.getMatrices();

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

   private void drawListPanel(DrawContext graphics, ItemStack shulker, int width, int height) {
      graphics.fill(-1, -1, width + 1, height + 1, this.border.getValue().getRGB());
      graphics.fill(0, 0, width, height, this.background.getValue().getRGB());
      int gridY = 3;
      if (this.listNames.getValue()) {
         String name = shulker.getName().getString();
         graphics.drawTextWithShadow(mc.textRenderer, mc.textRenderer.trimToWidth(name, width - 6), 3, 3, -1);
         gridY += 11;
      }

      ContainerComponent contents = (ContainerComponent)shulker.get(DataComponentTypes.CONTAINER);
      if (contents != null) {
         DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
         contents.copyTo(items);

         for (int i = 0; i < 27; i++) {
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

   private static ContainerComponent contentsOf(ItemStack stack) {
      return isShulkerBox(stack) ? (ContainerComponent)stack.get(DataComponentTypes.CONTAINER) : null;
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
