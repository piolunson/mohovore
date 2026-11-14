package dev.leonetic.features.gui.items.buttons;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.features.gui.HomovoreGui;
import dev.leonetic.features.gui.Widget;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

public class ColorButton extends SettingButton<Color> {
   private static final int SV_HEIGHT = 44;
   private static final int BAR_HEIGHT = 6;
   private static final int BTN_HEIGHT = 10;
   private static final int GAP = 2;
   private boolean open = false;
   private boolean hoveringHue;
   private boolean hoveringColor;
   private boolean hoveringAlpha;
   private boolean hoveringCopy;
   private boolean hoveringPaste;
   private boolean draggingHue;
   private boolean draggingColor;
   private boolean draggingAlpha;
   private float[] hsb;

   public ColorButton(Setting<Color> setting) {
      super(setting);
      this.height = 12;
      this.hsb = Color.RGBtoHSB(setting.getValue().getRed(), setting.getValue().getGreen(), setting.getValue().getBlue(), null);
   }

   @Override
   public int getHeight() {
      return !this.open ? 12 : 88;
   }

   @Override
   public int getRowHeight() {
      return 12;
   }

   @Override
   public void keyActivate() {
      this.open = !this.open;
      mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   private int pickerWidth() {
      return Math.max(40, this.width - 4);
   }

   @Override
   public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHoveringRow(mouseX, mouseY);
      if (hovering) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + 12.0F, GuiTheme.SLIDER_TRACK);
      }

      this.drawScrollableString(this.getName(), this.x + 2.0F, this.y + 2.0F, -1644050, this.width - 22, hovering);
      float sw1 = this.x + this.width - 16.0F;
      float sw2 = this.x + this.width - 3.0F;
      float sy1 = this.y + 2.0F;
      float sy2 = this.y + 12.0F - 2.0F;
      RenderUtil.rect(context, sw1, sy1, sw2, sy2, this.setting.getValue().getRGB());
      RenderUtil.rect(context, sw1, sy1, sw2, sy2, GuiTheme.OUTLINE, 1.0F);
      if (this.open) {
         Color currentColor = this.setting.getValue();
         Color realColor = Color.getHSBColor(this.hsb[0], 1.0F, 1.0F);
         int outlineColor = GuiTheme.OUTLINE;
         int pickerWidth = this.pickerWidth();
         float pickerX = this.x + 2.0F;
         float yOffset = 14.0F;
         int dragX = MathHelper.clamp(mouseX - (int)pickerX, 0, pickerWidth);
         int dragY = MathHelper.clamp(mouseY - (int)(this.y + yOffset), 0, 44);
         float dragHue = Math.max(pickerWidth * this.hsb[0] - 0.5F, 1.0F);
         float dragSaturation = Math.max(pickerWidth * this.hsb[1] - 1.0F, 2.0F);
         float dragBrightness = Math.max(44.0F * (1.0F - this.hsb[2]) - 1.0F, 2.0F);
         float dragAlpha = Math.max(pickerWidth * (currentColor.getAlpha() / 255.0F) - 0.5F, 1.0F);
         RenderUtil.horizontalGradient(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 44.0F, Color.WHITE, realColor);
         RenderUtil.verticalGradient(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 44.0F, new Color(0, 0, 0, 0), Color.BLACK);
         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 44.0F, outlineColor, 1.0F);
         this.hoveringColor = this.isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 44.0F);
         if (dragSaturation < pickerWidth && dragBrightness < 44.0F) {
            RenderUtil.rect(
               context,
               pickerX + dragSaturation - 2.0F,
               this.y + yOffset + dragBrightness - 2.0F,
               pickerX + dragSaturation + 1.0F,
               this.y + yOffset + dragBrightness + 1.0F,
               -16777216
            );
            RenderUtil.rect(
               context,
               pickerX + dragSaturation - 1.0F,
               this.y + yOffset + dragBrightness - 1.0F,
               pickerX + dragSaturation,
               this.y + yOffset + dragBrightness,
               -1
            );
         }

         if (this.draggingColor) {
            this.hsb[1] = (float)dragX / pickerWidth;
            this.hsb[2] = 1.0F - dragY / 44.0F;
            this.setColor(this.hsb);
         }

         yOffset += 46.0F;
         RenderUtil.horizontalGradient(
            context,
            pickerX,
            this.y + yOffset,
            pickerX + pickerWidth,
            this.y + yOffset + 6.0F,
            new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 0),
            new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 255)
         );
         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 6.0F, outlineColor, 1.0F);
         this.hoveringAlpha = this.isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 6.0F);
         RenderUtil.rect(context, pickerX + dragAlpha - 1.5F, this.y + yOffset - 1.0F, pickerX + dragAlpha + 1.5F, this.y + yOffset + 6.0F + 1.0F, -16777216);
         RenderUtil.rect(context, pickerX + dragAlpha - 0.5F, this.y + yOffset, pickerX + dragAlpha + 0.5F, this.y + yOffset + 6.0F, -1);
         if (this.draggingAlpha) {
            this.setColor(this.hsb, (int)(255.0F * dragX / pickerWidth));
         }

         yOffset += 8.0F;

         for (float i = 0.0F; i < pickerWidth; i += 0.5F) {
            RenderUtil.rect(
               context, pickerX + i, this.y + yOffset, pickerX + i + 0.5F, this.y + yOffset + 6.0F, Color.getHSBColor(i / pickerWidth, 1.0F, 1.0F).getRGB()
            );
         }

         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 6.0F, outlineColor, 1.0F);
         this.hoveringHue = this.isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 6.0F);
         if (dragHue < pickerWidth) {
            RenderUtil.rect(context, pickerX + dragHue - 1.5F, this.y + yOffset - 1.0F, pickerX + dragHue + 1.5F, this.y + yOffset + 6.0F + 1.0F, -16777216);
            RenderUtil.rect(context, pickerX + dragHue - 0.5F, this.y + yOffset, pickerX + dragHue + 0.5F, this.y + yOffset + 6.0F, -1);
         }

         if (this.draggingHue) {
            this.hsb[0] = (float)dragX / pickerWidth;
            this.setColor(this.hsb);
         }

         yOffset += 8.0F;
         int buttonWidth = (pickerWidth - 2) / 2;
         Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(120, 175, 220);
         int activeBg = accent.getRGB();
         int idleBg = GuiTheme.SLIDER_TRACK;
         float textY = this.y + yOffset + 1.0F + 1.0F;
         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + buttonWidth, this.y + yOffset + 10.0F, this.hoveringCopy ? activeBg : idleBg);
         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + buttonWidth, this.y + yOffset + 10.0F, outlineColor, 1.0F);
         this.drawString("Copy", pickerX + buttonWidth / 2.0 - Fonts.width("Copy") / 2.0, textY, -1644050);
         this.hoveringCopy = this.isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + buttonWidth, this.y + yOffset + 10.0F);
         RenderUtil.rect(
            context,
            pickerX + buttonWidth + 2.0F,
            this.y + yOffset,
            pickerX + buttonWidth * 2 + 2.0F,
            this.y + yOffset + 10.0F,
            this.hoveringPaste ? activeBg : idleBg
         );
         RenderUtil.rect(
            context, pickerX + buttonWidth + 2.0F, this.y + yOffset, pickerX + buttonWidth * 2 + 2.0F, this.y + yOffset + 10.0F, outlineColor, 1.0F
         );
         this.drawString("Paste", pickerX + buttonWidth + 2.0F + buttonWidth / 2.0 - Fonts.width("Paste") / 2.0, textY, -1644050);
         this.hoveringPaste = this.isHoveringArea(
            mouseX, mouseY, pickerX + buttonWidth + 2.0F, this.y + yOffset, pickerX + buttonWidth * 2 + 2.0F, this.y + yOffset + 10.0F
         );
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (this.isHoveringRow(mouseX, mouseY) && mouseButton == 1) {
         this.open = !this.open;
         mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }

      if (mouseButton == 0) {
         if (this.hoveringHue) {
            this.draggingHue = true;
         }

         if (this.hoveringColor) {
            this.draggingColor = true;
         }

         if (this.hoveringAlpha) {
            this.draggingAlpha = true;
         }

         if (this.hoveringCopy) {
            HomovoreGui.setColorClipboard(this.setting.getValue());
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }

         if (this.hoveringPaste && HomovoreGui.getColorClipboard() != null) {
            this.setting.setValue(HomovoreGui.getColorClipboard());
            this.hsb = Color.RGBtoHSB(this.setting.getValue().getRed(), this.setting.getValue().getGreen(), this.setting.getValue().getBlue(), null);
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
      if (releaseButton == 0) {
         this.draggingHue = false;
         this.draggingColor = false;
         this.draggingAlpha = false;
      }
   }

   @Override
   public boolean isHovering(int mouseX, int mouseY) {
      return this.isHoveringRow(mouseX, mouseY);
   }

   private boolean isHoveringRow(int mouseX, int mouseY) {
      for (Widget widget : HomovoreGui.getClickGui().getComponents()) {
         if (widget.drag) {
            return false;
         }
      }

      return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY < this.y + 12.0F;
   }

   private boolean isHoveringArea(int mouseX, int mouseY, float left, float top, float right, float bottom) {
      for (Widget widget : HomovoreGui.getClickGui().getComponents()) {
         if (widget.drag) {
            return false;
         }
      }

      return left <= mouseX && top <= mouseY && right > mouseX && bottom > mouseY;
   }

   private void setColor(float[] hsb) {
      this.setColor(hsb, this.setting.getValue().getAlpha());
   }

   private void setColor(float[] hsb, int alpha) {
      Color color = new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
      this.setting.setValue(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
   }
}
