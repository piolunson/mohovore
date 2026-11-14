package dev.leonetic.v2.gui;

import dev.leonetic.v2.gui.component.TextBox;
import dev.leonetic.v2.render.Anim;
import dev.leonetic.v2.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public final class SearchOverlay {
   private static final float ICON_WIDTH = 9.0F;
   private static final float TEXT_GAP = 5.0F;
   private static final float BOTTOM_MARGIN = 30.0F;
   private static final int MAX_TEXT_WIDTH = 180;
   private static final float WIDTH_HALF_LIFE = 0.05F;
   private static final float FADE_HALF_LIFE = 0.06F;
   private static final TextBox box = new TextBox().maxLength(64).sticky(true);
   private static String query = "";
   private static boolean open;
   private static float progress;
   private static float width = 9.0F;
   private static long toggleTime;

   private SearchOverlay() {
   }

   public static boolean isOpen() {
      return open;
   }

   public static boolean hasQuery() {
      return !query.isEmpty();
   }

   public static void toggle() {
      toggleTime = System.currentTimeMillis();
      if (open) {
         close();
      } else {
         open();
      }
   }

   public static boolean swallowToggleChar(String typed) {
      return System.currentTimeMillis() - toggleTime < 80L && "f".equalsIgnoreCase(typed);
   }

   public static void open() {
      open = true;
      box.focus();
   }

   public static void close() {
      open = false;
      box.setText("");
      box.unfocus();
   }

   public static void reset() {
      close();
      progress = 0.0F;
      width = 9.0F;
   }

   public static boolean matches(String name) {
      if (!hasQuery()) {
         return true;
      }

      String lower = name.toLowerCase();
      String needle = query.toLowerCase();
      if (lower.contains(needle)) {
         return true;
      }

      int index = 0;

      for (int i = 0; i < lower.length() && index < needle.length(); i++) {
         if (lower.charAt(i) == needle.charAt(index)) {
            index++;
         }
      }

      return index == needle.length();
   }

   public static void render(DrawContext graphics, int screenWidth, int screenHeight) {
      progress = Anim.towards(progress, open ? 1.0F : 0.0F, 0.06F);
      if (progress <= 0.01F && !open) {
         width = 9.0F;
      } else {
         String shown = Render2D.trim(query, 180);
         float textWidth = shown.isEmpty() ? 0.0F : Render2D.width(shown) + 1.0F;
         width = Anim.towards(width, 9.0F + (shown.isEmpty() ? 0.0F : 5.0F + textWidth), 0.05F);
         float left = screenWidth / 2.0F - width / 2.0F;
         float centerY = screenHeight - 30.0F + (1.0F - progress) * 8.0F;
         float previousAlpha = Render2D.alpha;
         Render2D.alpha = previousAlpha * progress;
         drawMagnifier(graphics, left, centerY - 4.5F, hasQuery() ? -1 : -3880238);
         box.renderText(graphics, left + 9.0F + 5.0F, centerY - 4.0F, -1);
         Render2D.alpha = previousAlpha;
      }
   }

   private static void drawMagnifier(DrawContext graphics, float x, float y, int color) {
      float p = 1.8F;
      Render2D.fill(graphics, x, y, x + p * 4.0F, y + p, color);
      Render2D.fill(graphics, x, y + p * 3.0F, x + p * 4.0F, y + p * 4.0F, color);
      Render2D.fill(graphics, x, y, x + p, y + p * 4.0F, color);
      Render2D.fill(graphics, x + p * 3.0F, y, x + p * 4.0F, y + p * 4.0F, color);
      Render2D.fill(graphics, x + p * 3.4F, y + p * 3.4F, x + p * 5.0F, y + p * 5.0F, color);
   }

   static {
      box.onChange(value -> query = value);
      box.onCancel(SearchOverlay::close);
      box.onCommit(SearchOverlay::close);
   }
}
