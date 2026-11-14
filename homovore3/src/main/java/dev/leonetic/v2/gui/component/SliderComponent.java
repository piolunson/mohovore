package dev.leonetic.v2.gui.component;

import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.gui.Panel;
import dev.leonetic.v2.gui.Theme;
import dev.leonetic.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;

public class SliderComponent extends SettingComponent<Number> {
   private final Number min;
   private final Number max;
   private final float difference;
   private final TextBox textBox;
   private boolean dragging;

   public SliderComponent(Setting<Number> setting) {
      super(setting);
      this.min = setting.getMin();
      this.max = setting.getMax();
      this.difference = this.max.floatValue() - this.min.floatValue();
      this.height = 12;
      this.textBox = new TextBox().filter(this::acceptNumeric).maxLength(16).onCommit(this::commitInput);
   }

   private boolean acceptNumeric(int c) {
      if (Character.isDigit(c)) {
         return true;
      } else {
         return c == 46 ? !this.textBox.getText().contains(".") : c == 45 && this.textBox.getText().isEmpty();
      }
   }

   @Override
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering, mouseX);
      if (this.dragging && !this.textBox.isFocused()) {
         this.setFromX(mouseX);
      }

      Color accent = Panel.currentAccent();
      float fillX = this.x + this.width * this.progress();
      Render2D.fill(graphics, this.x, this.y, fillX, this.y + this.height, accent.getRGB());
      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + this.height, Theme.HOVER);
      float labelY = this.y + (this.height - 8) / 2.0F;
      float valueRight = this.x + this.width - 3.0F;
      float valueLeft;
      if (this.textBox.isFocused()) {
         valueLeft = this.textBox.renderInlineRight(graphics, valueRight, this.y + this.height / 2.0F, -1);
      } else {
         String value = this.formatValue();
         int valueWidth = Render2D.width(value);
         this.drawString(graphics, value, valueRight - valueWidth, labelY, -3880238);
         valueLeft = valueRight - valueWidth;
      }

      this.drawScrollableString(
         graphics, this.setting.getName(), this.x + 2.0F, labelY, -1644050, Math.max(0, (int)(valueLeft - (this.x + 2.0F) - 4.0F)), hovering
      );
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.textBox.isFocused()) {
         if (!this.isHovered(mouseX, mouseY)) {
            this.textBox.unfocus();
         }
      } else if (button == 1 && this.isHovered(mouseX, mouseY)) {
         this.textBox.setText(this.formatValue());
         this.textBox.focus();
      } else if (button == 0 && this.isHovered(mouseX, mouseY)) {
         this.dragging = true;
         this.setFromX(mouseX);
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int button) {
      if (button == 0) {
         this.dragging = false;
      }
   }

   @Override
   public void keyActivate() {
      this.textBox.setText(this.formatValue());
      this.textBox.focus();
   }

   private void commitInput() {
      String input = this.textBox.getText();
      if (!input.isEmpty() && !input.equals("-")) {
         try {
            if (this.setting.getValue() instanceof Double) {
               this.setNumber(Math.max(this.min.doubleValue(), Math.min(this.max.doubleValue(), Double.parseDouble(input))));
            } else if (this.setting.getValue() instanceof Float) {
               this.setNumber(Math.max(this.min.floatValue(), Math.min(this.max.floatValue(), Float.parseFloat(input))));
            } else if (this.setting.getValue() instanceof Integer) {
               this.setNumber(Math.max(this.min.intValue(), Math.min(this.max.intValue(), Integer.parseInt(input))));
            }
         } catch (NumberFormatException var3) {
         }
      }
   }

   private String formatValue() {
      if (this.setting.getValue() instanceof Integer) {
         return String.valueOf(this.setting.getValue().intValue());
      } else {
         return this.setting.getValue() instanceof Float ? this.setting.getValue().toString() : String.valueOf(this.setting.getValue().doubleValue());
      }
   }

   private void setFromX(int mouseX) {
      float percent = Math.max(0.0F, Math.min(1.0F, (mouseX - this.x) / this.width));
      if (this.setting.getValue() instanceof Double) {
         this.setNumber(Math.round(10.0 * (this.min.doubleValue() + this.difference * percent)) / 10.0);
      } else if (this.setting.getValue() instanceof Float) {
         this.setNumber(Math.round(10.0F * (this.min.floatValue() + this.difference * percent)) / 10.0F);
      } else if (this.setting.getValue() instanceof Integer) {
         this.setNumber(this.min.intValue() + (int)(this.difference * percent));
      }
   }

   private void setNumber(Number value) {
      this.setting.setValue(value);
   }

   private float progress() {
      return this.difference == 0.0F ? 0.0F : (this.setting.getValue().floatValue() - this.min.floatValue()) / this.difference;
   }
}
