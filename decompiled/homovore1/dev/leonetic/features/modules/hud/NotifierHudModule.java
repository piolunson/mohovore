package dev.leonetic.features.modules.hud;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudModule;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_3486;

public class NotifierHudModule extends HudModule {
   private static final long DEFAULT_DURATION_MS = 3000L;
   private static final long FADE_MS = 400L;
   private static final int MAX_VISIBLE = 5;
   private static final int LINE_GAP = 3;
   private static final int STATS_GAP = 4;
   private static final int STATS_TOP_OFFSET = 56;
   private static final int NAME_CLEARANCE = 11;
   private static final int BUBBLE_HEIGHT = 10;
   private static final int TEXT_COLOR = -1;
   private static final String OPEN = "<< ";
   private static final String SEP = " ";
   private static final String CLOSE = " >>";
   private static final List<NotifierHudModule.Entry> ENTRIES = new CopyOnWriteArrayList<>();

   public NotifierHudModule() {
      super("Notifier");
   }

   public static void push(NotifierHudModule.Icon icon, String message) {
      push(null, icon, class_2561.method_43470(message), 3000L);
   }

   public static void push(String key, NotifierHudModule.Icon icon, String message) {
      push(key, icon, class_2561.method_43470(message), 3000L);
   }

   public static void push(String key, NotifierHudModule.Icon icon, class_2561 message) {
      push(key, icon, message, 3000L);
   }

   public static void push(String key, NotifierHudModule.Icon icon, class_2561 message, long durationMs) {
      long expiry = System.currentTimeMillis() + durationMs;
      if (key != null) {
         for (NotifierHudModule.Entry e : ENTRIES) {
            if (key.equals(e.key)) {
               e.icon = icon;
               e.message = message;
               e.expiry = expiry;
               return;
            }
         }
      }

      ENTRIES.add(new NotifierHudModule.Entry(key, icon, message, expiry));
   }

   @Override
   public void render(Render2DEvent event) {
      long now = System.currentTimeMillis();
      ENTRIES.removeIf(ex -> ex.expiry <= now);
      if (!ENTRIES.isEmpty()) {
         class_332 ctx = event.getContext();
         int lineHeight = 9;
         int lineStride = lineHeight + 3;
         int statsTop = this.screenHeight() - 56;
         if (this.showingBubbles()) {
            statsTop -= 10;
         }

         int anchorY = statsTop - 4 - 11;
         int total = ENTRIES.size();
         int shown = Math.min(total, 5);

         for (int k = 0; k < shown; k++) {
            NotifierHudModule.Entry e = ENTRIES.get(total - 1 - k);
            int topY = anchorY - lineHeight - k * lineStride;
            float alpha = this.fade(e, now);
            this.drawLine(ctx, e, topY, alpha);
         }
      }
   }

   private void drawLine(class_332 ctx, NotifierHudModule.Entry e, int y, float alpha) {
      int width = mc.field_1772.method_1727("<< ")
         + mc.field_1772.method_1727(e.icon.glyph)
         + mc.field_1772.method_1727(" ")
         + mc.field_1772.method_27525(e.message)
         + mc.field_1772.method_1727(" >>");
      int x = (this.screenWidth() - width) / 2;
      int text = this.applyAlpha(-1, alpha);
      int iconColor = this.applyAlpha(e.icon.color, alpha);
      ctx.method_51433(mc.field_1772, "<< ", x, y, text, true);
      x += mc.field_1772.method_1727("<< ");
      ctx.method_51433(mc.field_1772, e.icon.glyph, x, y, iconColor, true);
      x += mc.field_1772.method_1727(e.icon.glyph);
      ctx.method_51433(mc.field_1772, " ", x, y, text, true);
      x += mc.field_1772.method_1727(" ");
      ctx.method_51439(mc.field_1772, e.message, x, y, text, true);
      x += mc.field_1772.method_27525(e.message);
      ctx.method_51433(mc.field_1772, " >>", x, y, text, true);
   }

   private float fade(NotifierHudModule.Entry e, long now) {
      long remaining = e.expiry - now;
      return remaining >= 400L ? 1.0F : Math.max(0.0F, (float)remaining / 400.0F);
   }

   private int applyAlpha(int argb, float alpha) {
      int a = Math.round((argb >>> 24 & 0xFF) * alpha);
      return a << 24 | argb & 16777215;
   }

   private boolean showingBubbles() {
      return mc.field_1724.method_5777(class_3486.field_15517) || mc.field_1724.method_5669() < mc.field_1724.method_5748();
   }

   private static final class Entry {
      final String key;
      NotifierHudModule.Icon icon;
      class_2561 message;
      long expiry;

      Entry(String key, NotifierHudModule.Icon icon, class_2561 message, long expiry) {
         this.key = key;
         this.icon = icon;
         this.message = message;
         this.expiry = expiry;
      }
   }

   public enum Icon {
      DANGER("⚠", -22016),
      SKULL("☠", -43691),
      INFO("ℹ", -171);

      private final String glyph;
      private final int color;

      Icon(String glyph, int color) {
         this.glyph = glyph;
         this.color = color;
      }
   }
}
