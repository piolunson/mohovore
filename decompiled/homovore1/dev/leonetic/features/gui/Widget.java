package dev.leonetic.features.gui;

import dev.leonetic.features.Feature;
import dev.leonetic.features.gui.items.Item;
import dev.leonetic.features.gui.items.buttons.Button;
import dev.leonetic.features.gui.items.buttons.ModuleButton;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.modules.client.ClickGuiModule;
import dev.leonetic.util.render.GuiFade;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.ScissorUtil;
import dev.leonetic.util.render.font.Fonts;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1109;
import net.minecraft.class_332;
import net.minecraft.class_3417;

public class Widget extends Feature {
   public static Color currentAccent = null;
   public static Module.Category currentCategory = null;
   public static final int PANEL_WIDTH = 110;
   public static final int HEADER_HEIGHT = 15;
   private final Module.Category category;
   protected class_332 context;
   private final List<Item> items = new ArrayList<>();
   public boolean drag;
   private int x;
   private int y;
   private int x2;
   private int y2;
   private int width;
   private int height;
   private boolean open;
   private boolean hidden = false;
   private float animatedHeight = 0.0F;
   private float scrollOffset = 0.0F;

   public Widget(String name, Module.Category category, int x, int y, boolean open) {
      super(name);
      this.category = category;
      this.x = x;
      this.y = y;
      this.width = 110;
      this.height = 15;
      this.open = open;
   }

   public Widget(String name, int x, int y, boolean open) {
      this(name, null, x, y, open);
   }

   public Color accent() {
      return GuiTheme.categoryColor(this.category);
   }

   public Color accentAt(float yOffset) {
      return GuiTheme.categoryColor(this.category, yOffset);
   }

   public Module.Category getCategory() {
      return this.category;
   }

   private void drag(int mouseX, int mouseY) {
      if (this.drag) {
         this.x = this.x2 + mouseX;
         this.y = this.y2 + mouseY;
      }
   }

   public void drawScreen(class_332 context, int mouseX, int mouseY, float partialTicks) {
      this.context = context;
      Item.context = context;
      this.drag(mouseX, mouseY);
      int headerTop = this.y;
      int headerBottom = this.y + this.height;
      int bodyTop = headerBottom;
      int screenHeight = (int)(mc.method_22683().method_4502() / HomovoreGui.getScale());
      int availableHeight = Math.max(0, screenHeight - bodyTop - 6);
      float contentHeight = this.getTotalItemHeight();
      float targetHeight = this.open ? Math.min(contentHeight, availableHeight) : 0.0F;
      this.animatedHeight = this.animatedHeight + (targetHeight - this.animatedHeight) * ClickGuiModule.getInstance().getExpandSpeed();
      if (Math.abs(this.animatedHeight - targetHeight) < 0.5F) {
         this.animatedHeight = targetHeight;
      }

      Color cat = this.accentAt(headerTop);
      int catRGB = cat.getRGB();
      RenderUtil.rect(context, this.x, headerTop, this.x + this.width, headerBottom, catRGB);
      RenderUtil.rect(context, this.x, headerTop, this.x + this.width, headerTop + 1.0F, GuiTheme.HIGHLIGHT_TOP);
      String title = this.truncateText(this.getName(), this.width - 14);
      int textY = headerTop + 3 + 1;
      this.drawString(title, this.x + 4.0F, textY, -1);
      String caret = this.open ? "-" : "+";
      this.drawString(caret, this.x + this.width - 8, textY, -1);
      if (this.animatedHeight > 0.5F) {
         int scissorBottom = (int)(bodyTop + this.animatedHeight);
         float maxScroll = Math.max(0.0F, contentHeight - this.animatedHeight);
         this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset, maxScroll));
         this.scrollSelectionIntoView(this.animatedHeight, maxScroll);
         RenderUtil.rect(context, this.x, bodyTop, this.x + this.width, scissorBottom, GuiTheme.BODY_BG);
         RenderUtil.rect(context, this.x, bodyTop - 1.0F, this.x + this.width, bodyTop, GuiTheme.OUTLINE);
         ScissorUtil.enable(context, this.x, bodyTop, this.x + this.width, scissorBottom);
         currentCategory = this.category;
         float yCursor = bodyTop - this.scrollOffset;

         for (Item item : this.getItems()) {
            if (!item.isHidden()) {
               item.setLocation(this.x, yCursor);
               item.setWidth(this.width);
               currentAccent = GuiTheme.moduleColor(this.category, yCursor);
               if (item.isHovering(mouseX, mouseY)) {
                  ScissorUtil.disable(context);
               }

               item.drawScreen(context, mouseX, mouseY, partialTicks);
               if (item.isHovering(mouseX, mouseY)) {
                  ScissorUtil.enable(context);
               }

               if (GuiNavigator.isSelected(item) && !(item instanceof ModuleButton)) {
                  Button.drawSelectionRing(context, this.x, item.getY(), this.x + this.width, item.getY() + item.getRowHeight() - 1.0F, currentAccent);
               }

               yCursor += item.getHeight();
            }
         }

         currentAccent = null;
         currentCategory = null;
         ScissorUtil.disable(context);
         RenderUtil.rect(context, this.x, headerTop, this.x + this.width, scissorBottom, GuiTheme.OUTLINE, 1.0F);
         boolean anyAnimating = false;

         for (Item item : this.getItems()) {
            if (item instanceof ModuleButton mb && mb.isAnimating()) {
               anyAnimating = true;
               break;
            }
         }

         if (maxScroll > 0.5F && !anyAnimating) {
            float trackTop = bodyTop + 2.0F;
            float trackBottom = scissorBottom - 2.0F;
            float trackH = trackBottom - trackTop;
            float thumbH = Math.max(8.0F, trackH * (this.animatedHeight / contentHeight));
            float thumbY = trackTop + (trackH - thumbH) * (this.scrollOffset / maxScroll);
            int thumbColor = GuiTheme.withAlpha(cat, 220).getRGB();
            RenderUtil.rect(context, this.x + this.width - 2.0F, thumbY, this.x + this.width - 1.0F, thumbY + thumbH, thumbColor);
         }
      } else {
         RenderUtil.rect(context, this.x, headerTop, this.x + this.width, headerBottom, GuiTheme.OUTLINE, 1.0F);
      }
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
         this.x2 = this.x - mouseX;
         this.y2 = this.y - mouseY;
         HomovoreGui.getClickGui().getComponents().forEach(c -> {
            if (c.drag) {
               c.drag = false;
            }
         });
         this.drag = true;
      } else if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
         this.open = !this.open;
         mc.method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
      } else if (this.open) {
         int bodyTop = this.y + this.height;
         int scissorBottom = (int)(bodyTop + this.animatedHeight);
         this.getItems().forEach(item -> {
            if (item.getY() + item.getHeight() > bodyTop && item.getY() < scissorBottom) {
               item.mouseClicked(mouseX, mouseY, mouseButton);
            }
         });
      }
   }

   public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
      if (releaseButton == 0) {
         this.drag = false;
      }

      if (this.open) {
         int bodyTop = this.y + this.height;
         int scissorBottom = (int)(bodyTop + this.animatedHeight);
         this.getItems().forEach(item -> {
            if (item.getY() + item.getHeight() > bodyTop && item.getY() < scissorBottom) {
               item.mouseReleased(mouseX, mouseY, releaseButton);
            }
         });
      }
   }

   public void onKeyTyped(String typedChar, int keyCode) {
      if (this.open) {
         this.getItems().forEach(item -> item.onKeyTyped(typedChar, keyCode));
      }
   }

   public void onKeyPressed(int key) {
      if (this.open) {
         this.getItems().forEach(item -> item.onKeyPressed(key));
      }
   }

   public void addButton(Button button) {
      this.items.add(button);
   }

   public int getX() {
      return this.x;
   }

   public void setX(int x) {
      this.x = x;
   }

   public int getY() {
      return this.y;
   }

   public void setY(int y) {
      this.y = y;
   }

   public int getWidth() {
      return this.width;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   public int getHeight() {
      return this.height;
   }

   public void setHeight(int height) {
      this.height = height;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }

   public boolean isOpen() {
      return this.open;
   }

   public final List<Item> getItems() {
      return this.items;
   }

   public boolean isHoveringBody(int mouseX, int mouseY) {
      if (this.open && !(this.animatedHeight <= 0.0F)) {
         int bodyTop = this.y + this.height;
         int scissorBottom = (int)(bodyTop + this.animatedHeight);
         return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= bodyTop && mouseY <= scissorBottom;
      } else {
         return false;
      }
   }

   public void scroll(double amount) {
      float maxScroll = Math.max(0.0F, this.getTotalItemHeight() - this.animatedHeight);
      this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset - (float)amount * 12.0F, maxScroll));
   }

   private void scrollSelectionIntoView(float visibleHeight, float maxScroll) {
      if (GuiNavigator.isFocusedPanel(this)) {
         Item sel = GuiNavigator.getSelected();
         if (sel != null) {
            Float selTop = null;
            int selHeight = 0;
            float contentTop = 0.0F;

            for (Item item : this.getItems()) {
               if (!item.isHidden()) {
                  if (item == sel) {
                     selTop = contentTop;
                     selHeight = item.getRowHeight();
                  }

                  if (item instanceof ModuleButton mb && mb.isSubOpen()) {
                     float settingTop = contentTop + 13.0F + (mb.hasMultiplePages() ? 10 : 0);

                     for (Item setting : mb.currentPageVisibleSettings()) {
                        if (setting == sel) {
                           selTop = settingTop;
                           selHeight = setting.getRowHeight();
                        }

                        settingTop += setting.getHeight();
                     }
                  }

                  contentTop += item.getHeight();
               }
            }

            if (selTop != null) {
               if (selTop < this.scrollOffset) {
                  this.scrollOffset = selTop;
               } else if (selTop + selHeight > this.scrollOffset + visibleHeight) {
                  this.scrollOffset = selTop + selHeight - visibleHeight;
               }

               this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset, maxScroll));
            }
         }
      }
   }

   public boolean isHovering(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
   }

   private float getTotalItemHeight() {
      float h = 0.0F;

      for (Item item : this.getItems()) {
         if (!item.isHidden()) {
            h += item.getHeight();
         }
      }

      return h;
   }

   private String truncateText(String text, int maxWidth) {
      if (Fonts.width(text) <= maxWidth) {
         return text;
      }

      String ellipsis = "...";
      int ellipsisWidth = Fonts.width(ellipsis);
      if (maxWidth <= ellipsisWidth) {
         return ellipsis;
      }

      int targetWidth = maxWidth - ellipsisWidth;
      StringBuilder sb = new StringBuilder();

      for (char c : text.toCharArray()) {
         if (Fonts.width(sb.toString() + c) > targetWidth) {
            break;
         }

         sb.append(c);
      }

      return sb + ellipsis;
   }

   protected void drawString(String text, double x, double y, int color) {
      Fonts.drawString(this.context, text, (float)x, (float)y, GuiFade.apply(color));
   }
}
