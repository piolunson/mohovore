package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.class_1109;
import net.minecraft.class_124;
import net.minecraft.class_332;
import net.minecraft.class_3417;

public class EnumButton extends SettingButton<Enum<?>> {
   public EnumButton(Setting<Enum<?>> setting) {
      super(setting);
      this.height = 12;
   }

   @Override
   public int getHeight() {
      return 12;
   }

   @Override
   public void drawScreen(class_332 context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      if (hovering) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.SLIDER_TRACK);
      }

      float ty = this.y + (this.height - 8) / 2.0F;
      String val = class_124.field_1080 + this.setting.currentEnumName();
      int w = Fonts.width(val);
      this.drawString(val, this.x + this.width - w - 3.0F, ty, -3880238);
      int labelMax = Math.max(0, this.width - w - 8);
      this.drawScrollableString(this.setting.getName(), this.x + 2.0F, ty, -1644050, labelMax, hovering);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (this.isHovering(mouseX, mouseY)) {
         mc.method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
      }
   }

   @Override
   public void toggle() {
      this.setting.increaseEnum();
   }

   @Override
   public boolean getState() {
      return true;
   }
}
