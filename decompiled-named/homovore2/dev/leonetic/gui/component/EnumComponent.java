package dev.leonetic.gui.component;

import dev.leonetic.feature.setting.Setting;
import dev.leonetic.gui.Theme;
import dev.leonetic.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public class EnumComponent extends SettingComponent<Enum<?>> {
   public EnumComponent(Setting<Enum<?>> setting) {
      super(setting);
      this.height = 12;
   }

   @Override
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering, mouseX);
      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + this.height, Theme.SLIDER_TRACK);
      float textY = this.y + (this.height - 8) / 2.0F;
      String value = this.setting.displayValue();
      int valueWidth = Render2D.width(value);
      this.drawString(graphics, value, this.x + this.width - valueWidth - 3.0F, textY, -3880238);
      this.drawScrollableString(graphics, this.setting.getName(), this.x + 2.0F, textY, -1644050, Math.max(0, this.width - valueWidth - 8), hovering);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.isHovered(mouseX, mouseY)) {
         if (button == 0) {
            this.setting.cycle(1);
         } else {
            if (button != 1) {
               return;
            }

            this.setting.cycle(-1);
         }

         click();
      }
   }

   @Override
   public void keyActivate() {
      this.setting.cycle(1);
      click();
   }
}
