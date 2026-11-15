package dev.piolunson.features.gui.items.buttons;

import dev.piolunson.features.gui.GuiTheme;
import dev.piolunson.features.gui.Widget;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.render.RenderUtil;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class BooleanButton extends SettingButton<Boolean> {
   public BooleanButton(Setting<Boolean> setting) {
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
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(120, 175, 220);
      if (this.getState()) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, accent.getRGB());
         if (hovering) {
            RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.HIGHLIGHT_TOP);
         }
      } else if (hovering) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.withAlpha(accent, 60).getRGB());
      }

      int textColor = this.getState() ? -1 : -1644050;
      this.drawScrollableString(this.getName(), this.x + 2.0F, this.y + (this.height - 8) / 2.0F, textColor, this.width - 4, hovering);
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
      this.setting.setValue(!this.setting.getValue());
   }

   @Override
   public boolean getState() {
      return this.setting.getValue();
   }
}
