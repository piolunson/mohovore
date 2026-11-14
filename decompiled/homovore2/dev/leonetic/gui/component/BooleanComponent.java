package dev.leonetic.gui.component;

import dev.leonetic.feature.setting.Setting;
import dev.leonetic.gui.Panel;
import dev.leonetic.gui.Theme;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import net.minecraft.class_332;

public class BooleanComponent extends SettingComponent<Boolean> {
   public BooleanComponent(Setting<Boolean> setting) {
      super(setting);
      this.height = 12;
   }

   @Override
   public void render(class_332 graphics, int mouseX, int mouseY, float delta) {
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering, mouseX);
      Color accent = Panel.currentAccent();
      if (this.setting.getValue()) {
         Render2D.fill(graphics, this.x, this.y, this.x + this.width, this.y + this.height, accent.getRGB());
      }

      this.hover
         .fill(
            graphics, this.x, this.y, this.x + this.width, this.y + this.height, this.setting.getValue() ? Theme.HOVER : Theme.withAlpha(accent, 60).getRGB()
         );
      int color = this.setting.getValue() ? -1 : -1644050;
      this.drawScrollableString(graphics, this.setting.getName(), this.x + 2.0F, this.y + (this.height - 8) / 2.0F, color, this.width - 4, hovering);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (button == 0 && this.isHovered(mouseX, mouseY)) {
         this.setting.setValue(!this.setting.getValue());
         click();
      }
   }

   @Override
   public void keyActivate() {
      this.setting.setValue(!this.setting.getValue());
      click();
   }
}
