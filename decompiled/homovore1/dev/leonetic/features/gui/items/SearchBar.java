package dev.leonetic.features.gui.items;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.gui.Widget;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import net.minecraft.class_332;

public class SearchBar extends Item {
   public static String QUERY = "";
   private final TextBox textBox = new TextBox().placeholder("Search...").onChange(s -> QUERY = s);

   public SearchBar() {
      super("Search");
      this.height = 13;
   }

   public static boolean isFocused() {
      return TextBox.hasActiveFocus();
   }

   public static boolean hasQuery() {
      return QUERY != null && !QUERY.isEmpty();
   }

   public static boolean matches(String name) {
      return !hasQuery() ? true : name.toLowerCase().contains(QUERY.toLowerCase());
   }

   public static void unfocus() {
      TextBox.unfocusAll();
   }

   @Override
   public int getHeight() {
      return 13;
   }

   @Override
   public void drawScreen(class_332 context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(70, 75, 82);
      float x1 = this.x;
      float y1 = this.y;
      float x2 = this.x + this.width;
      float y2 = this.y + this.height - 1.0F;
      this.textBox.renderPill(context, x1, y1, x2, y2, accent, hovering, 12.0F);
      float iconX = this.x + 4.0F;
      float iconY = this.y + (this.height - 6.0F) / 2.0F;
      int iconColor = !this.textBox.isFocused() && !hasQuery() ? -3880238 : GuiTheme.brighten(accent, 0.55F).getRGB();
      this.drawMagnifier(context, iconX, iconY, iconColor);
   }

   private void drawMagnifier(class_332 context, float x, float y, int color) {
      RenderUtil.rect(context, x, y, x + 5.0F, y + 1.0F, color);
      RenderUtil.rect(context, x, y + 4.0F, x + 5.0F, y + 5.0F, color);
      RenderUtil.rect(context, x, y, x + 1.0F, y + 5.0F, color);
      RenderUtil.rect(context, x + 4.0F, y, x + 5.0F, y + 5.0F, color);
      RenderUtil.rect(context, x + 5.0F, y + 5.0F, x + 7.0F, y + 6.0F, color);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0) {
         if (this.isHovering(mouseX, mouseY)) {
            this.textBox.focus();
         } else {
            this.textBox.unfocus();
         }
      }
   }

   @Override
   public void keyActivate() {
      this.textBox.focus();
   }

   public void clear() {
      this.textBox.setText("");
      this.textBox.unfocus();
   }

   @Override
   public boolean isHovering(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }
}
