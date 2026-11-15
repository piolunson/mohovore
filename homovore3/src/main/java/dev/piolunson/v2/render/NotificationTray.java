package dev.piolunson.v2.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class NotificationTray {
   public static final long DEFAULT_DURATION_MS = 3000L;
   private static final long RISE_MS = 180L;
   private static final long FADE_MS = 350L;
   private static final int MAX_VISIBLE = 5;
   private static final int BOTTOM_CLEARANCE = 58;
   private static final int PADDING_X = 4;
   private static final int PADDING_Y = 2;
   private static final int ICON_GAP = 4;
   private static final int ROW_GAP = 2;
   private static final int TEXT_COLOR = -1;
   private static final int BACKDROP_COLOR = -1442840576;
   private static final List<NotificationTray.Entry> ENTRIES = new CopyOnWriteArrayList<>();

   private NotificationTray() {
   }

   public static void push(String key, NotificationTray.Icon icon, String message) {
      push(key, icon, message, 3000L);
   }

   public static void push(String key, NotificationTray.Icon icon, String message, long durationMs) {
      long now = System.currentTimeMillis();
      if (key != null) {
         for (NotificationTray.Entry entry : ENTRIES) {
            if (key.equals(entry.key)) {
               entry.icon = icon;
               entry.message = message;
               entry.expiry = now + durationMs;
               return;
            }
         }
      }

      ENTRIES.add(new NotificationTray.Entry(key, icon, message, now, now + durationMs));
   }

   public static void clear() {
      ENTRIES.clear();
   }

   public static void render(DrawContext graphics) {
      long now = System.currentTimeMillis();
      ENTRIES.removeIf(entryx -> entryx.expiry <= now);
      if (!ENTRIES.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         int screenWidth = mc.getWindow().getScaledWidth();
         int screenHeight = mc.getWindow().getScaledHeight();
         float previousAlpha = Render2D.alpha;
         Render2D.alpha = 1.0F;
         Render2D.Surface previousSurface = Render2D.surface(Render2D.Surface.HUD);
         int rowHeight = Render2D.lineHeight() + 4;
         int bottom = screenHeight - 58;
         int total = ENTRIES.size();
         int shown = Math.min(total, 5);

         for (int row = 0; row < shown; row++) {
            NotificationTray.Entry entry = ENTRIES.get(total - 1 - row);
            float rise = 1.0F - progress(now - entry.born, 180L);
            float top = bottom - (row + 1) * rowHeight - row * 2 + rise * rowHeight;
            draw(graphics, entry, screenWidth, top, rowHeight, alpha(entry, now));
         }

         Render2D.surface(previousSurface);
         Render2D.alpha = previousAlpha;
      }
   }

   private static void draw(DrawContext graphics, NotificationTray.Entry entry, int screenWidth, float top, int rowHeight, float alpha) {
      int iconWidth = Render2D.width(entry.icon.glyph);
      int textWidth = Render2D.width(entry.message);
      int width = 8 + iconWidth + 4 + textWidth;
      float left = (screenWidth - width) / 2.0F;
      float textY = Render2D.centeredTextY(top, rowHeight);
      Render2D.fill(graphics, left, top, left + width, top + rowHeight, Render2D.scaleAlpha(-1442840576, alpha));
      Render2D.text(graphics, entry.icon.glyph, left + 4.0F, textY, Render2D.scaleAlpha(entry.icon.color, alpha), true);
      Render2D.text(graphics, entry.message, left + 4.0F + iconWidth + 4.0F, textY, Render2D.scaleAlpha(-1, alpha), true);
   }

   private static float alpha(NotificationTray.Entry entry, long now) {
      float in = progress(now - entry.born, 180L);
      float out = progress(entry.expiry - now, 350L);
      return Math.min(in, out);
   }

   private static float progress(long elapsed, long over) {
      return elapsed >= over ? 1.0F : Math.max(0.0F, (float)elapsed / (float)over);
   }

   private static final class Entry {
      private final String key;
      private final long born;
      private NotificationTray.Icon icon;
      private String message;
      private long expiry;

      private Entry(String key, NotificationTray.Icon icon, String message, long born, long expiry) {
         this.key = key;
         this.icon = icon;
         this.message = message;
         this.born = born;
         this.expiry = expiry;
      }
   }

   public enum Icon {
      INFO("ℹ", -171),
      WARNING("⚠", -22016),
      DANGER("☠", -43691);

      private final String glyph;
      private final int color;

      Icon(String glyph, int color) {
         this.glyph = glyph;
         this.color = color;
      }
   }
}
