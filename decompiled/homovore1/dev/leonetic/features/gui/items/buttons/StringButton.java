package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.Widget;
import dev.leonetic.features.gui.items.TextBox;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.font.Fonts;
import java.awt.Color;
import net.minecraft.class_124;
import net.minecraft.class_332;

public class StringButton extends SettingButton<String> {
   private final TextBox textBox;

   public StringButton(Setting<String> setting) {
      super(setting);
      this.height = 12;
      this.textBox = new TextBox().placeholder(setting.getName()).onCommit(this::commit).onCancel(() -> this.textBox().setText(setting.getValue()));
   }

   private TextBox textBox() {
      return this.textBox;
   }

   @Override
   public int getHeight() {
      return 12;
   }

   @Override
   public void drawScreen(class_332 context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(70, 75, 82);
      float x1 = this.x;
      float y1 = this.y;
      float x2 = this.x + this.width;
      float y2 = this.y + this.height - 1.0F;
      if (this.textBox.isFocused()) {
         this.textBox.renderPill(context, x1, y1, x2, y2, accent, hovering, 3.0F);
      } else {
         float ty = this.y + (this.height - 8) / 2.0F;
         String val = class_124.field_1080 + this.setting.getValue();
         int w = Fonts.width(val);
         if (hovering) {
            this.textBox.renderPill(context, x1, y1, x2, y2, accent, true, 3.0F);
         }

         this.drawString(val, this.x + this.width - w - 3.0F, ty, -3880238);
         int labelMax = Math.max(0, this.width - w - 8);
         this.drawScrollableString(this.setting.getName(), this.x + 2.0F, ty, -1644050, labelMax, hovering);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
         if (!this.textBox.isFocused()) {
            this.textBox.setText(this.setting.getValue());
            this.textBox.focus();
         }
      } else if (!this.isHovering(mouseX, mouseY) && this.textBox.isFocused()) {
         this.textBox.unfocus();
      }
   }

   @Override
   public void keyActivate() {
      this.textBox.setText(this.setting.getValue());
      this.textBox.focus();
   }

   private void commit() {
      String s = this.textBox.getText();
      if (s.isEmpty()) {
         this.setting.setValue(this.setting.getDefaultValue());
      } else {
         this.setting.setValue(s);
      }
   }
}
