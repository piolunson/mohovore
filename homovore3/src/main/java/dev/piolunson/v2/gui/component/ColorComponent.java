package dev.piolunson.v2.gui.component;

import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.gui.ClickGuiScreen;
import dev.piolunson.v2.gui.Panel;
import dev.piolunson.v2.gui.Theme;
import dev.piolunson.v2.render.Anim;
import dev.piolunson.v2.render.Hover;
import dev.piolunson.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;

public class ColorComponent extends SettingComponent<Color> {
   private static final int SV_HEIGHT = 44;
   private static final int BAR_HEIGHT = 6;
   private static final int BUTTON_HEIGHT = 11;
   private static final int HEX_HEIGHT = 11;
   private static final int GAP = 3;
   private static final float OPEN_HALF_LIFE = 0.05F;
   private final TextBox hexBox;
   private boolean open;
   private float openAnim;
   private boolean hoveringHue;
   private boolean hoveringColor;
   private boolean hoveringAlpha;
   private boolean hoveringCopy;
   private boolean hoveringPaste;
   private boolean hoveringHex;
   private boolean draggingHue;
   private boolean draggingColor;
   private boolean draggingAlpha;
   private final Hover hexHover = new Hover();
   private final Hover copyHover = new Hover();
   private final Hover pasteHover = new Hover();
   private float[] hsb;

   public ColorComponent(Setting<Color> setting) {
      super(setting);
      this.height = 12;
      Color color = setting.getValue();
      this.hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      this.hexBox = new TextBox().filter(ColorComponent::acceptHex).maxLength(8).onCommit(this::commitHex);
      this.hexBox.onCancel(() -> this.hexBox.setText(hex(setting.getValue())));
   }

   @Override
   public float height() {
      return 12 + (int)Math.ceil(this.openAnim * trayHeight());
   }

   @Override
   public int rowHeight() {
      return 12;
   }

   @Override
   public void update() {
      super.update();
      if (this.open && this.isHidden()) {
         this.close();
      }
   }

   @Override
   public void keyActivate() {
      this.toggle();
   }

   private static float trayHeight() {
      return 96.0F;
   }

   private int pickerWidth() {
      return Math.max(40, this.width - 8);
   }

   @Override
   public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
      this.openAnim = Anim.towards(this.openAnim, this.open ? 1.0F : 0.0F, 0.05F);
      boolean hovering = this.isHovered(mouseX, mouseY);
      this.hoverWeight(hovering, mouseX);
      this.hover.fill(graphics, this.x, this.y, this.x + this.width, this.y + 12.0F, Theme.SLIDER_TRACK);
      this.drawScrollableString(graphics, this.setting.getName(), this.x + 2.0F, this.y + 2.0F, -1644050, this.width - 22, hovering);
      Color current = this.setting.getValue();
      float swatchLeft = this.x + this.width - 17.0F;
      float swatchRight = this.x + this.width - 3.0F;
      float swatchTop = this.y + 2.5F;
      float swatchBottom = this.y + 12.0F - 2.5F;
      Render2D.fill(graphics, swatchLeft, swatchTop, swatchRight, swatchBottom, Theme.SLIDER_TRACK);
      Render2D.fill(graphics, swatchLeft, swatchTop, swatchRight, swatchBottom, current.getRGB());
      float trayHeight = this.openAnim * trayHeight();
      if (trayHeight <= 0.5F) {
         this.hoveringHue = this.hoveringColor = this.hoveringAlpha = this.hoveringCopy = this.hoveringPaste = this.hoveringHex = false;
         this.hexHover.decay();
         this.copyHover.decay();
         this.pasteHover.decay();
      } else {
         float trayTop = this.y + 12.0F;
         boolean animating = this.openAnim != (this.open ? 1.0F : 0.0F);
         if (animating) {
            Render2D.scissor(graphics, this.x, trayTop, this.x + this.width, trayTop + trayHeight);
         }

         this.renderTray(graphics, mouseX, mouseY, current, this.openAnim > 0.99F);
         if (animating) {
            Render2D.unscissor(graphics);
         }
      }
   }

   private void renderTray(DrawContext graphics, int mouseX, int mouseY, Color current, boolean live) {
      Color pureHue = Color.getHSBColor(this.hsb[0], 1.0F, 1.0F);
      Color accent = Panel.currentAccent();
      int pickerWidth = this.pickerWidth();
      float pickerX = this.x + (this.width - pickerWidth) / 2.0F;
      float pickerRight = pickerX + pickerWidth;
      float offset = 15.0F;
      int dragX = Math.max(0, Math.min(pickerWidth, mouseX - (int)pickerX));
      int dragY = Math.max(0, Math.min(44, mouseY - (int)(this.y + offset)));
      float svTop = this.y + offset;
      float svBottom = svTop + 44.0F;
      Render2D.horizontalGradient(graphics, pickerX, svTop, pickerRight, svBottom, Color.WHITE, pureHue);
      Render2D.verticalGradient(graphics, pickerX, svTop, pickerRight, svBottom, new Color(0, 0, 0, 0), Color.BLACK);
      this.hoveringColor = live && this.inArea(mouseX, mouseY, pickerX, svTop, pickerRight, svBottom);
      this.drawKnob(graphics, pickerX + pickerWidth * this.hsb[1], svBottom - 44.0F * this.hsb[2], pickerX, pickerRight);
      if (this.draggingColor) {
         this.hsb[1] = (float)dragX / pickerWidth;
         this.hsb[2] = 1.0F - dragY / 44.0F;
         this.setColor(this.hsb, current.getAlpha());
      }

      offset += 47.0F;
      float alphaTop = this.y + offset;
      Color opaque = new Color(current.getRed(), current.getGreen(), current.getBlue(), 255);
      Render2D.fill(graphics, pickerX, alphaTop, pickerRight, alphaTop + 6.0F, Theme.SLIDER_TRACK);
      Render2D.horizontalGradient(graphics, pickerX, alphaTop, pickerRight, alphaTop + 6.0F, Theme.withAlpha(opaque, 0), opaque);
      this.hoveringAlpha = live && this.inArea(mouseX, mouseY, pickerX, alphaTop, pickerRight, alphaTop + 6.0F);
      this.drawThumb(graphics, pickerX + pickerWidth * (current.getAlpha() / 255.0F), alphaTop, alphaTop + 6.0F, pickerX, pickerRight);
      if (this.draggingAlpha) {
         this.setColor(this.hsb, Math.round(255.0F * dragX / pickerWidth));
      }

      offset += 9.0F;
      float hueTop = this.y + offset;

      for (int i = 0; i < pickerWidth; i++) {
         Render2D.fill(graphics, pickerX + i, hueTop, pickerX + i + 1.0F, hueTop + 6.0F, Color.HSBtoRGB((float)i / pickerWidth, 1.0F, 1.0F));
      }

      this.hoveringHue = live && this.inArea(mouseX, mouseY, pickerX, hueTop, pickerRight, hueTop + 6.0F);
      this.drawThumb(graphics, pickerX + pickerWidth * this.hsb[0], hueTop, hueTop + 6.0F, pickerX, pickerRight);
      if (this.draggingHue) {
         this.hsb[0] = (float)dragX / pickerWidth;
         this.setColor(this.hsb, current.getAlpha());
      }

      offset += 9.0F;
      float hexTop = this.y + offset;
      this.hoveringHex = live && this.inArea(mouseX, mouseY, pickerX, hexTop, pickerRight, hexTop + 11.0F);
      this.hexHover.update(this.hoveringHex, mouseX, pickerX, pickerRight);
      if (!this.hexBox.isFocused()) {
         this.hexBox.setText(hex(current));
      }

      int hexColor = this.hexBox.isFocused() ? -1 : this.hexHover.mix(-3880238, Theme.brighten(accent, 0.55F).getRGB());
      float hash = Render2D.width("#") + 1.0F;
      this.drawString(graphics, "#", pickerX, hexTop + 1.5F, hexColor);
      this.hexBox.renderBare(graphics, pickerX, hexTop, pickerRight, hexTop + 11.0F, hash, hexColor);
      offset += 14.0F;
      float buttonTop = this.y + offset;
      float buttonBottom = buttonTop + 11.0F;
      float middle = pickerX + pickerWidth / 2.0F;
      this.hoveringCopy = live && this.inArea(mouseX, mouseY, pickerX, buttonTop, middle, buttonBottom);
      this.hoveringPaste = live && this.inArea(mouseX, mouseY, middle, buttonTop, pickerRight, buttonBottom);
      this.copyHover.update(this.hoveringCopy, mouseX, pickerX, middle);
      this.pasteHover.update(this.hoveringPaste, mouseX, middle, pickerRight);
      boolean canPaste = ClickGuiScreen.getColorClipboard() != null;
      this.drawTextButton(graphics, "Copy", pickerX, middle, buttonTop, this.copyHover, accent, true);
      this.drawTextButton(graphics, "Paste", middle, pickerRight, buttonTop, this.pasteHover, accent, canPaste);
   }

   private void drawTextButton(DrawContext graphics, String label, float left, float right, float top, Hover state, Color accent, boolean enabled) {
      int resting = enabled ? -3880238 : Render2D.scaleAlpha(-3880238, 0.45F);
      int color = enabled ? state.mix(resting, Theme.brighten(accent, 0.55F).getRGB()) : resting;
      this.drawString(graphics, label, (left + right) / 2.0F - Render2D.width(label) / 2.0F, top + 1.5F, color);
   }

   private void drawKnob(DrawContext graphics, float cx, float cy, float left, float right) {
      cx = Math.max(left + 3.0F, Math.min(right - 3.0F, cx));
      ring(graphics, cx - 3.0F, cy - 3.0F, cx + 3.0F, cy + 3.0F, 1342177280);
      ring(graphics, cx - 2.0F, cy - 2.0F, cx + 2.0F, cy + 2.0F, -1);
   }

   private void drawThumb(DrawContext graphics, float cx, float top, float bottom, float left, float right) {
      cx = Math.max(left + 1.0F, Math.min(right - 1.0F, cx));
      Render2D.fill(graphics, cx - 1.5F, top - 1.0F, cx + 1.5F, bottom + 1.0F, 1342177280);
      Render2D.fill(graphics, cx - 0.5F, top - 1.0F, cx + 0.5F, bottom + 1.0F, -1);
   }

   private static void ring(DrawContext graphics, float x1, float y1, float x2, float y2, int color) {
      Render2D.fill(graphics, x1, y1, x2, y1 + 1.0F, color);
      Render2D.fill(graphics, x1, y2 - 1.0F, x2, y2, color);
      Render2D.fill(graphics, x1, y1 + 1.0F, x1 + 1.0F, y2 - 1.0F, color);
      Render2D.fill(graphics, x2 - 1.0F, y1 + 1.0F, x2, y2 - 1.0F, color);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (this.isHovered(mouseX, mouseY) && button == 1) {
         this.toggle();
      } else if (button == 0) {
         if (this.hoveringHex) {
            this.hexBox.setText(hex(this.setting.getValue()));
            this.hexBox.focus();
         } else {
            if (this.hexBox.isFocused()) {
               this.hexBox.unfocus();
            }

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
               ClickGuiScreen.setColorClipboard(this.setting.getValue());
               click();
            }

            if (this.hoveringPaste && ClickGuiScreen.getColorClipboard() != null) {
               this.setting.setValue(ClickGuiScreen.getColorClipboard());
               Color color = this.setting.getValue();
               this.hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
               click();
            }
         }
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int button) {
      if (button == 0) {
         this.draggingHue = this.draggingColor = this.draggingAlpha = false;
      }
   }

   private void toggle() {
      if (this.open) {
         this.close();
      } else {
         this.open = true;
      }

      click();
   }

   private void close() {
      this.open = false;
      this.draggingHue = this.draggingColor = this.draggingAlpha = false;
      this.hexBox.unfocus();
   }

   private static boolean acceptHex(int c) {
      return Character.digit(c, 16) >= 0;
   }

   private static String hex(Color color) {
      String rgb = String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
      return color.getAlpha() == 255 ? rgb : rgb + String.format("%02X", color.getAlpha());
   }

   private void commitHex() {
      String input = this.hexBox.getText();
      if (input.length() != 6 && input.length() != 8) {
         this.hexBox.setText(hex(this.setting.getValue()));
      } else {
         long value = Long.parseLong(input, 16);
         int alpha = input.length() == 8 ? (int)(value & 255L) : this.setting.getValue().getAlpha();
         int rgb = (int)(input.length() == 8 ? value >> 8 : value);
         Color color = new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha);
         this.setting.setValue(color);
         this.hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      }
   }

   private boolean inArea(int mouseX, int mouseY, float left, float top, float right, float bottom) {
      return !ClickGuiScreen.isDraggingPanel() && left <= mouseX && mouseX < right && top <= mouseY && mouseY < bottom;
   }

   private void setColor(float[] values, int alpha) {
      Color color = new Color(Color.HSBtoRGB(values[0], values[1], values[2]));
      this.setting.setValue(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
   }
}
