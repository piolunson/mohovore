package dev.piolunson.features.gui.items.buttons;

import dev.piolunson.features.gui.GuiTheme;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.render.RenderUtil;
import dev.piolunson.util.render.font.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;

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
   public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      if (hovering) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.SLIDER_TRACK);
      }

      float ty = this.y + (this.height - 8) / 2.0F;
      String val = Formatting.GRAY + this.setting.currentEnumName();
      int w = Fonts.width(val);
      this.drawString(val, this.x + this.width - w - 3.0F, ty, -3880238);
      int labelMax = Math.max(0, this.width - w - 8);
      this.drawScrollableString(this.setting.getName(), this.x + 2.0F, ty, -1644050, labelMax, hovering);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (this.isHovering(mouseX, mouseY)) {
         mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
