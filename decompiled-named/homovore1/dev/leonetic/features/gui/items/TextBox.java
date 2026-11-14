package dev.leonetic.features.gui.items;

import dev.leonetic.features.gui.GuiTheme;
import dev.leonetic.util.render.GuiFade;
import dev.leonetic.util.render.RenderUtil;
import dev.leonetic.util.render.font.Fonts;
import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class TextBox {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static TextBox ACTIVE;
   public static final IntPredicate ANY_PRINTABLE = c -> c >= 32;
   private final StringBuilder buffer = new StringBuilder();
   private String placeholder = "";
   private IntPredicate filter = ANY_PRINTABLE;
   private int maxLength = 256;
   private Consumer<String> onChange;
   private Runnable onCommit;
   private Runnable onCancel;

   public TextBox placeholder(String s) {
      this.placeholder = s == null ? "" : s;
      return this;
   }

   public TextBox filter(IntPredicate f) {
      this.filter = f == null ? ANY_PRINTABLE : f;
      return this;
   }

   public TextBox maxLength(int n) {
      this.maxLength = Math.max(0, n);
      return this;
   }

   public TextBox onChange(Consumer<String> c) {
      this.onChange = c;
      return this;
   }

   public TextBox onCommit(Runnable r) {
      this.onCommit = r;
      return this;
   }

   public TextBox onCancel(Runnable r) {
      this.onCancel = r;
      return this;
   }

   public String getText() {
      return this.buffer.toString();
   }

   public boolean isEmpty() {
      return this.buffer.length() == 0;
   }

   public void setText(String s) {
      this.buffer.setLength(0);
      if (s != null) {
         this.buffer.append(s);
      }

      this.fireChange();
   }

   public void clear() {
      this.buffer.setLength(0);
      this.unfocus();
      this.fireChange();
   }

   public boolean isFocused() {
      return ACTIVE == this;
   }

   public static boolean hasActiveFocus() {
      return ACTIVE != null;
   }

   public static TextBox getActive() {
      return ACTIVE;
   }

   public static void unfocusAll() {
      ACTIVE = null;
   }

   public static void routeKeyPressed(int key) {
      if (ACTIVE != null) {
         ACTIVE.keyPressed(key);
      }
   }

   public static void routeKeyTyped(String typed) {
      if (ACTIVE != null) {
         ACTIVE.keyTyped(typed);
      }
   }

   public void focus() {
      if (ACTIVE != this) {
         if (ACTIVE != null) {
            ACTIVE.unfocus();
         }

         ACTIVE = this;
         mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }
   }

   public void unfocus() {
      if (ACTIVE == this) {
         ACTIVE = null;
      }
   }

   private void fireChange() {
      if (this.onChange != null) {
         this.onChange.accept(this.buffer.toString());
      }
   }

   public void renderPill(DrawContext ctx, float x1, float y1, float x2, float y2, Color accent, boolean hovering, float leftPad) {
      boolean active = this.isFocused() || this.buffer.length() > 0;
      if (active) {
         int wash = GuiTheme.withAlpha(accent, 90).getRGB();
         RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.BODY_BG);
         RenderUtil.rect(ctx, x1, y1, x2, y2, wash);
         RenderUtil.rect(ctx, x1, y1, x2, y1 + 1.0F, GuiTheme.HIGHLIGHT_TOP);
         int border = GuiTheme.brighten(accent, 0.25F).getRGB();
         RenderUtil.rect(ctx, x1, y1, x2, y2, border, 1.0F);
      } else {
         RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.BODY_BG);
         if (hovering) {
            RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.withAlpha(accent, 50).getRGB());
         }

         RenderUtil.rect(ctx, x1, y1, x2, y2, GuiTheme.OUTLINE_INNER, 1.0F);
      }

      float textX = x1 + leftPad;
      float textY = y1 + (y2 - y1 - 8.0F) / 2.0F;
      int textColor = this.isFocused() ? -1 : -1644050;
      if (this.buffer.length() == 0 && !this.isFocused()) {
         this.drawString(ctx, this.placeholder, textX, textY, -3880238);
      } else {
         String shown = this.buffer.toString();
         this.drawString(ctx, shown, textX, textY, textColor);
         this.drawCaret(ctx, textX + Fonts.width(shown), textY, textColor);
      }
   }

   public float renderInlineRight(DrawContext ctx, float rightX, float centerY, int color) {
      String shown = this.buffer.toString();
      int w = Fonts.width(shown);
      float textX = rightX - w;
      float textY = centerY - 4.0F;
      this.drawString(ctx, shown, textX, textY, color);
      this.drawCaret(ctx, rightX, textY, color);
      return textX;
   }

   private void drawCaret(DrawContext ctx, float cx, float textY, int color) {
      if (this.isFocused()) {
         boolean blink = System.currentTimeMillis() / 500L % 2L == 0L;
         if (blink) {
            RenderUtil.rect(ctx, cx, textY - 1.0F, cx + 1.0F, textY + 8.0F, color);
         }
      }
   }

   public void keyPressed(int key) {
      if (this.isFocused()) {
         switch (key) {
            case 256:
               if (this.onCancel != null) {
                  this.onCancel.run();
               }

               this.unfocus();
               break;
            case 257:
            case 335:
               if (this.onCommit != null) {
                  this.onCommit.run();
               }

               this.unfocus();
               break;
            case 259:
               if (this.buffer.length() > 0) {
                  this.buffer.deleteCharAt(this.buffer.length() - 1);
                  this.fireChange();
               }
         }
      }
   }

   public void keyTyped(String typed) {
      if (this.isFocused() && typed != null && !typed.isEmpty()) {
         boolean changed = false;

         for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (this.filter.test(c)) {
               if (this.buffer.length() >= this.maxLength) {
                  break;
               }

               this.buffer.append(c);
               changed = true;
            }
         }

         if (changed) {
            this.fireChange();
         }
      }
   }

   private void drawString(DrawContext ctx, String text, float x, float y, int color) {
      Fonts.drawString(ctx, text, x, y, GuiFade.apply(color));
   }
}
