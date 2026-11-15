package dev.piolunson.v2.gui.component;

import dev.piolunson.v2.render.Render2D;
import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

public final class TextBox {
   private static final MinecraftClient MC = MinecraftClient.getInstance();
   private static final int SELECTION = new Color(120, 170, 255, 90).getRGB();
   private static TextBox active;
   private final StringBuilder buffer = new StringBuilder();
   private String placeholder = "";
   private IntPredicate filter = c -> c >= 32;
   private int maxLength = 256;
   private boolean sticky;
   private Consumer<String> onChange;
   private Runnable onCommit;
   private Runnable onCancel;
   private int cursor;
   private int anchor;
   private float boxX1;
   private float boxY1;
   private float boxX2;
   private float boxY2;
   private float textX;
   private float textY;
   private boolean dragSelecting;

   public TextBox placeholder(String value) {
      this.placeholder = value == null ? "" : value;
      return this;
   }

   public TextBox filter(IntPredicate value) {
      this.filter = value == null ? c -> c >= 32 : value;
      return this;
   }

   public TextBox maxLength(int value) {
      this.maxLength = Math.max(0, value);
      return this;
   }

   public TextBox sticky(boolean value) {
      this.sticky = value;
      return this;
   }

   public TextBox onChange(Consumer<String> value) {
      this.onChange = value;
      return this;
   }

   public TextBox onCommit(Runnable value) {
      this.onCommit = value;
      return this;
   }

   public TextBox onCancel(Runnable value) {
      this.onCancel = value;
      return this;
   }

   public String getText() {
      return this.buffer.toString();
   }

   public boolean isFocused() {
      return active == (Object)this;
   }

   public static boolean hasActiveFocus() {
      return active != null;
   }

   public static void unfocusAll() {
      active = null;
   }

   public static void routeKeyPressed(KeyInput event) {
      if (active != null) {
         active.keyPressed(event);
      }
   }

   public static void routeKeyTyped(String typed) {
      if (active != null) {
         active.keyTyped(typed);
      }
   }

   public static boolean routeMouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      return active != null && active.mouseClicked(mouseX, mouseY, button, doubled);
   }

   public static void routeMouseMoved(double mouseX) {
      if (active != null && active.dragSelecting) {
         active.moveTo(active.indexAt(mouseX), true);
      }
   }

   public static void routeMouseReleased() {
      if (active != null) {
         active.dragSelecting = false;
      }
   }

   public static void blurActive() {
      if (active != null && !active.sticky) {
         active.unfocus();
      }
   }

   public void setText(String value) {
      this.buffer.setLength(0);
      if (value != null) {
         this.buffer.append(value);
      }

      this.cursor = this.anchor = this.buffer.length();
      this.fireChange();
   }

   public void focus() {
      if (active != (Object)this) {
         if (active != null) {
            active.unfocus();
         }

         active = this;
         this.cursor = this.anchor = this.buffer.length();
         Component.click();
      }
   }

   public void unfocus() {
      if (active == (Object)this) {
         active = null;
      }
   }

   private void fireChange() {
      if (this.onChange != null) {
         this.onChange.accept(this.buffer.toString());
      }
   }

   public void renderBare(DrawContext graphics, float x1, float y1, float x2, float y2, float leftPad, int color) {
      float left = x1 + leftPad;
      float top = y1 + (y2 - y1 - 8.0F) / 2.0F;
      if (this.buffer.isEmpty() && !this.isFocused()) {
         this.textX = left;
         this.textY = top;
         Render2D.text(graphics, this.placeholder, left, top, -3880238);
      } else {
         this.renderText(graphics, left, top, color);
      }

      this.boxX1 = x1;
      this.boxY1 = y1;
      this.boxX2 = x2;
      this.boxY2 = y2;
   }

   public float renderInlineRight(DrawContext graphics, float rightX, float centerY, int color) {
      float left = rightX - Render2D.width(this.buffer.toString());
      this.renderText(graphics, left, centerY - 4.0F, color);
      return left;
   }

   public float renderText(DrawContext graphics, float x, float y, int color) {
      String shown = this.buffer.toString();
      this.textX = x;
      this.textY = y;
      this.boxX1 = x - 2.0F;
      this.boxY1 = y - 2.0F;
      this.boxX2 = x + Render2D.width(shown) + 2.0F;
      this.boxY2 = y + 10.0F;
      if (this.isFocused() && this.hasSelection()) {
         float from = x + Render2D.width(shown.substring(0, this.selectionStart()));
         float to = x + Render2D.width(shown.substring(0, this.selectionEnd()));
         Render2D.fill(graphics, from, y - 1.0F, to, y + 9.0F, SELECTION);
      }

      Render2D.text(graphics, shown, x, y, color);
      this.drawCaret(graphics, x + Render2D.width(shown.substring(0, Math.min(this.cursor, shown.length()))), y, color);
      return Render2D.width(shown);
   }

   private void drawCaret(DrawContext graphics, float x, float y, int color) {
      if (this.isFocused() && System.currentTimeMillis() / 500L % 2L == 0L) {
         Render2D.fill(graphics, x, y - 1.0F, x + 1.0F, y + 8.0F, color);
      }
   }

   private boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (button != 0 || mouseX < this.boxX1 || mouseX > this.boxX2 || mouseY < this.boxY1 || mouseY > this.boxY2) {
         return false;
      } else if (doubled) {
         this.anchor = 0;
         this.cursor = this.buffer.length();
         return true;
      } else {
         this.moveTo(this.indexAt(mouseX), false);
         this.dragSelecting = true;
         return true;
      }
   }

   private int indexAt(double mouseX) {
      String shown = this.buffer.toString();
      float offset = (float)mouseX - this.textX;
      if (offset <= 0.0F) {
         return 0;
      }

      for (int i = 0; i < shown.length(); i++) {
         float before = Render2D.width(shown.substring(0, i));
         float after = Render2D.width(shown.substring(0, i + 1));
         if (offset < (before + after) / 2.0F) {
            return i;
         }
      }

      return shown.length();
   }

   private boolean hasSelection() {
      return this.cursor != this.anchor;
   }

   private int selectionStart() {
      return Math.min(this.cursor, this.anchor);
   }

   private int selectionEnd() {
      return Math.max(this.cursor, this.anchor);
   }

   private void keyPressed(KeyInput event) {
      if (this.isFocused()) {
         int key = event.getKeycode();
         boolean shift = event.hasShift();
         boolean control = event.hasCtrl() || event.hasCtrlOrCmd();
         if (event.isSelectAll()) {
            this.anchor = 0;
            this.cursor = this.buffer.length();
         } else if (event.isCopy()) {
            this.copySelection();
         } else if (event.isCut()) {
            this.copySelection();
            this.deleteSelection();
         } else if (event.isPaste()) {
            this.insert(MC.keyboard.getClipboard());
         } else {
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
                  if (this.deleteSelection()) {
                     return;
                  }

                  int to = control ? this.wordBoundary(-1) : Math.max(0, this.cursor - 1);
                  if (to != this.cursor) {
                     this.buffer.delete(to, this.cursor);
                     this.cursor = this.anchor = to;
                     this.fireChange();
                  }
                  break;
               case 261:
                  if (this.deleteSelection()) {
                     return;
                  }

                  int destination = control ? this.wordBoundary(1) : Math.min(this.buffer.length(), this.cursor + 1);
                  if (destination != this.cursor) {
                     this.buffer.delete(this.cursor, destination);
                     this.anchor = this.cursor;
                     this.fireChange();
                  }
                  break;
               case 262:
                  this.moveTo(control ? this.wordBoundary(1) : Math.min(this.buffer.length(), this.cursor + 1), shift);
                  break;
               case 263:
                  this.moveTo(control ? this.wordBoundary(-1) : Math.max(0, this.cursor - 1), shift);
                  break;
               case 264:
               case 269:
                  this.moveTo(this.buffer.length(), shift);
                  break;
               case 265:
               case 268:
                  this.moveTo(0, shift);
            }
         }
      }
   }

   private void moveTo(int index, boolean selecting) {
      this.cursor = Math.max(0, Math.min(this.buffer.length(), index));
      if (!selecting) {
         this.anchor = this.cursor;
      }
   }

   private int wordBoundary(int direction) {
      int index = this.cursor;
      if (direction >= 0) {
         int length = this.buffer.length();

         while (index < length && this.buffer.charAt(index) == ' ') {
            index++;
         }

         while (index < length && this.buffer.charAt(index) != ' ') {
            index++;
         }
      } else {
         while (index > 0 && this.buffer.charAt(index - 1) == ' ') {
            index--;
         }

         while (index > 0 && this.buffer.charAt(index - 1) != ' ') {
            index--;
         }
      }

      return index;
   }

   private void copySelection() {
      if (this.hasSelection()) {
         MC.keyboard.setClipboard(this.buffer.substring(this.selectionStart(), this.selectionEnd()));
      }
   }

   private boolean deleteSelection() {
      if (!this.hasSelection()) {
         return false;
      }

      this.buffer.delete(this.selectionStart(), this.selectionEnd());
      this.cursor = this.anchor = this.selectionStart();
      this.fireChange();
      return true;
   }

   private void keyTyped(String typed) {
      if (this.isFocused()) {
         this.insert(typed);
      }
   }

   private void insert(String text) {
      if (this.isFocused() && text != null && !text.isEmpty()) {
         StringBuilder accepted = new StringBuilder();
         int room = this.maxLength - (this.buffer.length() - (this.selectionEnd() - this.selectionStart()));

         for (int i = 0; i < text.length() && accepted.length() < room; i++) {
            char c = text.charAt(i);
            if (this.filter.test(c)) {
               accepted.append(c);
            }
         }

         if (!accepted.isEmpty()) {
            int start = this.selectionStart();
            this.buffer.replace(start, this.selectionEnd(), accepted.toString());
            this.cursor = this.anchor = start + accepted.length();
            this.fireChange();
         }
      }
   }
}
