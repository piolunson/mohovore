package dev.leonetic.v2.gui.component;

import dev.leonetic.v2.feature.setting.ButtonSetting;
import dev.leonetic.v2.gui.Panel;
import dev.leonetic.v2.gui.Theme;
import dev.leonetic.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;

public class ButtonComponent extends SettingComponent<Boolean> {
   private final ButtonSetting button;

   public ButtonComponent(ButtonSetting setting) {
      super(setting);
      this.button = setting;
      this.height = 12;
   }

   @Override
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering, mouseX);
      Color accent = Panel.currentAccent();
      Render2D.fill(graphics, this.x, this.y, this.x + this.width, this.y + this.height, Theme.withAlpha(accent, 90).getRGB());
      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + this.height, Theme.HOVER);
      Render2D.outline(graphics, this.x, this.y, this.x + this.width, this.y + this.height, Theme.withAlpha(Theme.brighten(accent, 0.4F), 170).getRGB(), 1.0F);
      String label = Render2D.trim(this.button.getName(), Math.max(0, this.width - 6));
      Render2D.text(graphics, label, this.x + (this.width - Render2D.width(label)) / 2.0F, Render2D.centeredTextY(this.y, this.height), -1);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (button == 0 && this.isHovered(mouseX, mouseY)) {
         this.keyActivate();
      }
   }

   @Override
   public void keyActivate() {
      click();
      this.button.press();
   }
}
