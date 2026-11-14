package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.settings.Bind;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import net.minecraft.class_1109;
import net.minecraft.class_124;
import net.minecraft.class_332;
import net.minecraft.class_3417;

public class BindButton extends SettingButton<Bind> {
   public boolean isListening;

   public BindButton(Setting<Bind> setting) {
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
      if (hovering || this.isListening) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.SLIDER_TRACK);
      }

      float ty = this.y + (this.height - 8) / 2.0F;
      if (this.isListening) {
         this.drawString("Press a key...", this.x + 2.0F, ty, -1644050);
      } else {
         String str = class_124.field_1080 + this.setting.getValue().toString().toUpperCase().replace("KEY.KEYBOARD", "").replace(".", " ").trim();
         int w = Fonts.width(str);
         this.drawString(str, this.x + this.width - w - 3.0F, ty, -3880238);
         int labelMax = Math.max(0, this.width - w - 8);
         this.drawScrollableString(this.setting.getName(), this.x + 2.0F, ty, -1644050, labelMax, hovering);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (this.isListening) {
         if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            this.onMouseClick();
         } else {
            Bind bind = Bind.fromMouseButton(mouseButton);
            this.setting.setValue(bind);
            this.isListening = false;
            mc.method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
         }
      } else {
         super.mouseClicked(mouseX, mouseY, mouseButton);
         if (this.isHovering(mouseX, mouseY)) {
            mc.method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
         }
      }
   }

   @Override
   public void onKeyPressed(int key) {
      if (this.isListening) {
         Bind bind = new Bind(key);
         if (key == 261 || key == 259 || key == 256) {
            bind = new Bind(-1);
         }

         this.setting.setValue(bind);
         this.onMouseClick();
      }
   }

   @Override
   public void toggle() {
      this.isListening = !this.isListening;
   }

   @Override
   public boolean getState() {
      return !this.isListening;
   }
}
