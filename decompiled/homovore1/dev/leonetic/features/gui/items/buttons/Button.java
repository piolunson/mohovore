package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.gui.HomovoreGui;
import dev.leonetic.features.gui.Widget;
import dev.leonetic.features.gui.items.Item;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import net.minecraft.class_1109;
import net.minecraft.class_332;
import net.minecraft.class_3417;

public class Button extends Item {
   private boolean state;

   public Button(String name) {
      super(name);
      this.height = 13;
   }

   @Override
   public void drawScreen(class_332 context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      drawRow(context, this.x, this.y, this.x + this.width, this.y + this.height, this.getState(), hovering);
      int textColor = this.getState() ? -1 : -3880238;
      this.drawScrollableString(this.getName(), this.x + 4.0F, this.y + (this.height - 8) / 2.0F, textColor, this.width - 8, hovering);
   }

   public static void drawSelectionRing(class_332 context, float x1, float y1, float x2, float y2, Color accent) {
      if (accent == null) {
         accent = new Color(110, 120, 130);
      }

      int inner = GuiTheme.withAlpha(GuiTheme.brighten(accent, 0.65F), 230).getRGB();
      int outer = GuiTheme.withAlpha(GuiTheme.brighten(accent, 0.45F), 140).getRGB();
      RenderUtil.rect(context, x1, y1, x2, y2, outer, 2.0F);
      RenderUtil.rect(context, x1, y1, x2, y2, inner, 1.0F);
      RenderUtil.rect(context, x1, y1, x2, y1 + 1.0F, GuiTheme.HIGHLIGHT_TOP);
   }

   @Override
   public void keyActivate() {
      this.onMouseClick();
   }

   public static void drawRow(class_332 context, float x1, float y1, float x2, float y2, boolean enabled, boolean hovering) {
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(70, 75, 82);
      float fillBottom = y2 - 1.0F;
      if (enabled) {
         RenderUtil.rect(context, x1, y1, x2, fillBottom, accent.getRGB());
         if (hovering) {
            RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.HIGHLIGHT_TOP);
         }

         RenderUtil.rect(context, x1, y1, x2, y1 + 1.0F, GuiTheme.HIGHLIGHT_TOP);
      } else if (hovering) {
         RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.withAlpha(accent, 60).getRGB());
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
         this.onMouseClick();
      }
   }

   public void onMouseClick() {
      this.state = !this.state;
      this.toggle();
      mc.method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
   }

   public void toggle() {
   }

   public boolean getState() {
      return this.state;
   }

   @Override
   public int getHeight() {
      return 13;
   }

   @Override
   public boolean isHovering(int mouseX, int mouseY) {
      for (Widget widget : HomovoreGui.getClickGui().getComponents()) {
         if (widget.drag) {
            return false;
         }
      }

      return mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth() && mouseY >= this.getY() && mouseY < this.getY() + this.height;
   }
}
