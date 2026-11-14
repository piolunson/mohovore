package dev.leonetic.v2.gui.component;

import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.gui.Panel;
import dev.leonetic.v2.gui.Theme;
import dev.leonetic.v2.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public class StringComponent extends SettingComponent<String> {
   private final TextBox textBox;

   public StringComponent(Setting<String> setting) {
      super(setting);
      this.height = 12;
      this.textBox = new TextBox().placeholder(setting.getName()).onCommit(this::commit);
      this.textBox.onCancel(() -> this.textBox.setText(setting.getValue()));
   }

   @Override
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      boolean hovering = this.isHovered(mouseX, mouseY);
      if (this.textBox.isFocused()) {
         this.hover.decay();
         this.textBox.renderBare(graphics, this.x, this.y, this.x + this.width, this.y + this.height - 1.0F, 3.0F, -1);
      } else {
         float textY = this.y + (this.height - 8) / 2.0F;
         String value = this.setting.getValue();
         int valueWidth = Render2D.width(value);
         this.hoverWeight(hovering, mouseX);
         this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + this.height - 1.0F, Theme.withAlpha(Panel.currentAccent(), 50).getRGB());
         this.drawString(graphics, value, this.x + this.width - valueWidth - 3.0F, textY, -3880238);
         this.drawScrollableString(graphics, this.setting.getName(), this.x + 2.0F, textY, -1644050, Math.max(0, this.width - valueWidth - 8), hovering);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (button == 0 && this.isHovered(mouseX, mouseY)) {
         if (!this.textBox.isFocused()) {
            this.textBox.setText(this.setting.getValue());
            this.textBox.focus();
         }
      } else if (!this.isHovered(mouseX, mouseY) && this.textBox.isFocused()) {
         this.textBox.unfocus();
      }
   }

   @Override
   public void keyActivate() {
      this.textBox.setText(this.setting.getValue());
      this.textBox.focus();
   }

   private void commit() {
      this.setting.setValue(this.textBox.getText().isEmpty() ? this.setting.getDefaultValue() : this.textBox.getText());
   }
}
