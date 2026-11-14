package dev.leonetic.util.render.font;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.class_10799;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_5250;
import net.minecraft.class_5481;
import org.joml.Matrix3x2fStack;

public class FontRenderer implements Closeable {
   private static final class_310 mc = class_310.method_1551();
   private static final float REF_TEXT_HEIGHT = 8.0F;
   private static final float TARGET_ASCENT = 7.0F;
   private final Map<class_2960, ObjectList<FontRenderer.DrawEntry>> glyphPages = new HashMap<>();
   private final ObjectList<GlyphMap> maps = new ObjectArrayList();
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private final int charsPerPage;
   private final int padding;
   private Font[] baseFonts;
   private Font[] fittedFonts;
   private int multiplier;
   private int previousGameScale = -1;
   private int blockPx;
   private boolean initialized;

   public FontRenderer(Font[] fonts) {
      this(fonts, 256, 4);
   }

   public FontRenderer(Font[] fonts, int charactersPerPage, int paddingBetweenCharacters) {
      if (fonts.length == 0) {
         throw new IllegalArgumentException("fonts.length == 0");
      }

      if (charactersPerPage <= 4) {
         throw new IllegalArgumentException("charactersPerPage too small");
      }

      if (paddingBetweenCharacters < 0) {
         throw new IllegalArgumentException("padding < 0");
      }

      this.baseFonts = fonts;
      this.charsPerPage = charactersPerPage;
      this.padding = paddingBetweenCharacters;
      this.init();
   }

   public void drawString(class_332 graphics, String text, float x, float y, int color, boolean dropShadow) {
      this.drawText(graphics, parse(text, color).method_30937(), x, y, color, dropShadow);
   }

   public void drawText(class_332 graphics, class_5481 text, float x, float y, int color, boolean dropShadow) {
      this.rebuildIfScaleChanged();
      if ((color & -67108864) == 0) {
         color |= -16777216;
      }

      int alpha = color >>> 24 & 0xFF;
      int baseRgb = dropShadow ? shadow(color) : color & 16777215;
      float[] pen = new float[]{0.0F};
      synchronized (this.glyphPages) {
         text.accept((index, style, codePoint) -> {
            int rgb;
            if (style.method_10973() != null) {
               int c = style.method_10973().method_27716();
               rgb = dropShadow ? shadow(c) : c & 16777215;
            } else {
               rgb = baseRgb;
            }

            int argb = alpha << 24 | rgb;

            for (char ch : Character.toChars(codePoint)) {
               GlyphMap.Glyph glyph = this.locateGlyph(ch);
               if (glyph != null) {
                  if (glyph.width() > 0 && glyph.height() > 0 && ch != ' ') {
                     class_2960 pageIdx = glyph.parent().getTextureId();
                     this.glyphPages.computeIfAbsent(pageIdx, k -> new ObjectArrayList()).add(new FontRenderer.DrawEntry(pen[0], 0.0F, argb, glyph));
                  }

                  pen[0] += glyph.advance();
               }
            }

            return true;
         });
         float blockGui = (float)this.blockPx / this.multiplier;
         float yTop = y + (8.0F - blockGui) / 2.0F;
         Matrix3x2fStack pose = graphics.method_51448();
         pose.pushMatrix();
         pose.translate(x, yTop);
         float scale = 1.0F / this.multiplier;
         pose.scale(scale, scale);

         for (Entry<class_2960, ObjectList<FontRenderer.DrawEntry>> entry : this.glyphPages.entrySet()) {
            class_2960 pageId = entry.getKey();

            for (FontRenderer.DrawEntry de : (List)entry.getValue()) {
               GlyphMap.Glyph g = de.glyph;
               GlyphMap parent = g.parent();
               graphics.method_25291(
                  class_10799.field_56883,
                  pageId,
                  Math.round(de.dx),
                  Math.round(de.dy),
                  g.u(),
                  g.v(),
                  g.width(),
                  g.height(),
                  parent.getWidth(),
                  parent.getHeight(),
                  de.argb
               );
            }
         }

         pose.popMatrix();
         this.glyphPages.clear();
      }
   }

   public static String stripControlCodes(String text) {
      if (text.indexOf(167) < 0) {
         return text;
      }

      StringBuilder sb = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == 167 && i + 1 < text.length()) {
            i++;
         } else {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   public float getTextWidth(String text) {
      this.rebuildIfScaleChanged();
      String stripped = stripControlCodes(text);
      float adv = 0.0F;

      for (int i = 0; i < stripped.length(); i++) {
         GlyphMap.Glyph g = this.locateGlyph(stripped.charAt(i));
         if (g != null) {
            adv += g.advance();
         }
      }

      return adv / this.multiplier;
   }

   public float getTextWidth(class_5481 text) {
      this.rebuildIfScaleChanged();
      float[] adv = new float[]{0.0F};
      text.accept((index, style, codePoint) -> {
         for (char ch : Character.toChars(codePoint)) {
            GlyphMap.Glyph g = this.locateGlyph(ch);
            if (g != null) {
               adv[0] += g.advance();
            }
         }

         return true;
      });
      return adv[0] / this.multiplier;
   }

   public float getHeight() {
      return 9.0F;
   }

   private void rebuildIfScaleChanged() {
      int current = Math.round(mc.method_22683().method_4495());
      if (current != this.previousGameScale) {
         this.close();
         this.init();
      }
   }

   private void init() {
      if (this.initialized) {
         throw new IllegalStateException("Double call to init()");
      }

      this.lock.writeLock().lock();

      try {
         int scale = Math.round(mc.method_22683().method_4495());
         if (scale < 1) {
            scale = 1;
         }

         this.previousGameScale = scale;
         this.multiplier = scale;
         BufferedImage probe = new BufferedImage(1, 1, 2);
         Graphics2D pg = probe.createGraphics();
         float probeSize = 16.0F * this.multiplier;
         pg.setFont(this.baseFonts[0].deriveFont(probeSize));
         FontMetrics probeFm = pg.getFontMetrics();
         float ascentRatio = probeFm.getAscent() / probeSize;
         float fitSize = 7.0F * this.multiplier / ascentRatio;
         Font[] fitted = new Font[this.baseFonts.length];

         for (int i = 0; i < this.baseFonts.length; i++) {
            fitted[i] = this.baseFonts[i].deriveFont(fitSize);
         }

         pg.setFont(fitted[0]);
         FontMetrics fm = pg.getFontMetrics();
         this.blockPx = fm.getAscent() + fm.getDescent();
         pg.dispose();
         this.fittedFonts = fitted;
         this.initialized = true;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   private GlyphMap.Glyph locateGlyph(char ch) {
      this.lock.readLock().lock();

      try {
         ObjectListIterator base = this.maps.iterator();

         while (base.hasNext()) {
            GlyphMap m = (GlyphMap)base.next();
            if (m.contains(ch)) {
               return m.getGlyph(ch);
            }
         }
      } finally {
         this.lock.readLock().unlock();
      }

      int base = this.charsPerPage * (ch / this.charsPerPage);
      int var14 = Math.min(base + this.charsPerPage, 65536);
      GlyphMap map = new GlyphMap(this.fittedFonts, (char)base, (char)var14, this.padding);
      this.lock.writeLock().lock();

      try {
         map.generate();
         this.maps.add(map);
      } finally {
         this.lock.writeLock().unlock();
      }

      return map.getGlyph(ch);
   }

   @Override
   public void close() {
      this.lock.writeLock().lock();

      try {
         ObjectListIterator var1 = this.maps.iterator();

         while (var1.hasNext()) {
            GlyphMap m = (GlyphMap)var1.next();
            m.destroy();
         }

         this.maps.clear();
         this.initialized = false;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   private static int shadow(int rgb) {
      return (rgb & 16579836) >> 2;
   }

   private static class_2561 parse(String text, int defaultColor) {
      if (text.indexOf(167) < 0) {
         return class_2561.method_43470(text).method_27696(class_2583.field_24360.method_36139(defaultColor));
      }

      class_5250 root = class_2561.method_43470("").method_27696(class_2583.field_24360.method_36139(defaultColor));
      class_2583 style = class_2583.field_24360.method_36139(defaultColor);
      StringBuilder run = new StringBuilder();

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == 167 && i + 1 < text.length()) {
            if (run.length() > 0) {
               root.method_10852(class_2561.method_43470(run.toString()).method_27696(style));
               run.setLength(0);
            }

            char code = Character.toLowerCase(text.charAt(++i));
            style = applyCode(style, code, defaultColor);
         } else {
            run.append(c);
         }
      }

      if (run.length() > 0) {
         root.method_10852(class_2561.method_43470(run.toString()).method_27696(style));
      }

      return root;
   }

   private static class_2583 applyCode(class_2583 s, char code, int defaultColor) {
      return switch (code) {
         case '0' -> s.method_36139(-16777216);
         case '1' -> s.method_36139(-16777046);
         case '2' -> s.method_36139(-16733696);
         case '3' -> s.method_36139(-16733526);
         case '4' -> s.method_36139(-5636096);
         case '5' -> s.method_36139(-5635926);
         case '6' -> s.method_36139(-22016);
         case '7' -> s.method_36139(-5592406);
         case '8' -> s.method_36139(-11184811);
         case '9' -> s.method_36139(-11184641);
         default -> s;
         case 'a' -> s.method_36139(-11141291);
         case 'b' -> s.method_36139(-11141121);
         case 'c' -> s.method_36139(-43691);
         case 'd' -> s.method_36139(-43521);
         case 'e' -> s.method_36139(-171);
         case 'f' -> s.method_36139(-1);
         case 'r' -> class_2583.field_24360.method_36139(defaultColor);
      };
   }

   private record DrawEntry(float dx, float dy, int argb, GlyphMap.Glyph glyph) {
   }
}
