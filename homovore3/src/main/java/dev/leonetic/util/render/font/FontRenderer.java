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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

public class FontRenderer implements Closeable {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final float REF_TEXT_HEIGHT = 8.0F;
   private static final float TARGET_ASCENT = 7.0F;
   private final Map<Identifier, ObjectList<FontRenderer.DrawEntry>> glyphPages = new HashMap<>();
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

   public void drawString(DrawContext graphics, String text, float x, float y, int color, boolean dropShadow) {
      this.drawText(graphics, parse(text, color).asOrderedText(), x, y, color, dropShadow);
   }

   public void drawText(DrawContext graphics, OrderedText text, float x, float y, int color, boolean dropShadow) {
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
            if (style.getColor() != null) {
               int c = style.getColor().getRgb();
               rgb = dropShadow ? shadow(c) : c & 16777215;
            } else {
               rgb = baseRgb;
            }

            int argb = alpha << 24 | rgb;

            for (char ch : Character.toChars(codePoint)) {
               GlyphMap.Glyph glyph = this.locateGlyph(ch);
               if (glyph != null) {
                  if (glyph.width() > 0 && glyph.height() > 0 && ch != ' ') {
                     Identifier pageIdx = glyph.parent().getTextureId();
                     this.glyphPages.computeIfAbsent(pageIdx, k -> new ObjectArrayList()).add(new FontRenderer.DrawEntry(pen[0], 0.0F, argb, glyph));
                  }

                  pen[0] += glyph.advance();
               }
            }

            return true;
         });
         float blockGui = (float)this.blockPx / this.multiplier;
         float yTop = y + (8.0F - blockGui) / 2.0F;
         Matrix3x2fStack pose = graphics.getMatrices();
         pose.pushMatrix();
         pose.translate(x, yTop);
         float scale = 1.0F / this.multiplier;
         pose.scale(scale, scale);

         for (Entry<Identifier, ObjectList<FontRenderer.DrawEntry>> entry : this.glyphPages.entrySet()) {
            Identifier pageId = entry.getKey();

            for (FontRenderer.DrawEntry de : (List<FontRenderer.DrawEntry>)(List)entry.getValue()) {
               GlyphMap.Glyph g = de.glyph;
               GlyphMap parent = g.parent();
               graphics.drawTexture(
                  RenderPipelines.GUI_TEXTURED,
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

   public float getTextWidth(OrderedText text) {
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
      int current = Math.round(mc.getWindow().getScaleFactor());
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
         int scale = Math.round(mc.getWindow().getScaleFactor());
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

   private static Text parse(String text, int defaultColor) {
      if (text.indexOf(167) < 0) {
         return Text.literal(text).fillStyle(Style.EMPTY.withColor(defaultColor));
      }

      MutableText root = Text.literal("").fillStyle(Style.EMPTY.withColor(defaultColor));
      Style style = Style.EMPTY.withColor(defaultColor);
      StringBuilder run = new StringBuilder();

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == 167 && i + 1 < text.length()) {
            if (run.length() > 0) {
               root.append(Text.literal(run.toString()).fillStyle(style));
               run.setLength(0);
            }

            char code = Character.toLowerCase(text.charAt(++i));
            style = applyCode(style, code, defaultColor);
         } else {
            run.append(c);
         }
      }

      if (run.length() > 0) {
         root.append(Text.literal(run.toString()).fillStyle(style));
      }

      return root;
   }

   private static Style applyCode(Style s, char code, int defaultColor) {
      return switch (code) {
         case '0' -> s.withColor(-16777216);
         case '1' -> s.withColor(-16777046);
         case '2' -> s.withColor(-16733696);
         case '3' -> s.withColor(-16733526);
         case '4' -> s.withColor(-5636096);
         case '5' -> s.withColor(-5635926);
         case '6' -> s.withColor(-22016);
         case '7' -> s.withColor(-5592406);
         case '8' -> s.withColor(-11184811);
         case '9' -> s.withColor(-11184641);
         default -> s;
         case 'a' -> s.withColor(-11141291);
         case 'b' -> s.withColor(-11141121);
         case 'c' -> s.withColor(-43691);
         case 'd' -> s.withColor(-43521);
         case 'e' -> s.withColor(-171);
         case 'f' -> s.withColor(-1);
         case 'r' -> Style.EMPTY.withColor(defaultColor);
      };
   }

   private record DrawEntry(float dx, float dy, int argb, GlyphMap.Glyph glyph) {
   }
}
