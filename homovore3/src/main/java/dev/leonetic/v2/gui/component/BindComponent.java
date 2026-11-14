package dev.leonetic.v2.gui.component;

import dev.leonetic.v2.feature.setting.Bind;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.gui.Theme;
import dev.leonetic.v2.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public class BindComponent extends SettingComponent<Bind> {
   private boolean listening;

   public BindComponent(Setting<Bind> setting) {
      super(setting);
      this.height = 12;
   }

   @Override
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering || this.listening, mouseX);
      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + this.height, Theme.SLIDER_TRACK);
      float textY = this.y + (this.height - 8) / 2.0F;
      if (this.listening) {
         this.drawString(graphics, "Press a key...", this.x + 2.0F, textY, -1644050);
      } else {
         String value = this.setting.getValue().toString().toUpperCase();
         int valueWidth = Render2D.width(value);
         this.drawString(graphics, value, this.x + this.width - valueWidth - 3.0F, textY, -3880238);
         this.drawScrollableString(graphics, this.setting.getName(), this.x + 2.0F, textY, -1644050, Math.max(0, this.width - valueWidth - 8), hovering);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.listening) {
         if (button == 0 && this.isHovered(mouseX, mouseY)) {
            this.listening = false;
            click();
         } else {
            this.setting.setValue(Bind.mouse(button));
            this.listening = false;
            click();
         }
      } else if (this.isHovered(mouseX, mouseY)) {
         this.listening = true;
         click();
      }
   }

   @Override
   public void keyPressed(int key) {
      if (this.listening) {
         boolean clear = key == 261 || key == 259 || key == 256;
         this.setting.setValue(clear ? Bind.none() : new Bind(key));
         this.listening = false;
         click();
      }
   }

   @Override
   public void keyActivate() {
      this.listening = !this.listening;
      click();
   }

   public boolean isListening() {
      return this.listening;
   }
}
