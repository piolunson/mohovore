package dev.leonetic.render.font;

import dev.leonetic.Homovore2;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_10799;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_1011.class_1012;
import org.joml.Matrix3x2fStack;

public final class GlyphFont implements AutoCloseable {
   private static final int ATLAS_SIZE = 1024;
   private static final int PADDING = 2;
   private static final float VANILLA_TEXT_HEIGHT = 8.0F;
   private static int atlasCounter;
   private final Font awtFont;
   private final int oversample;
   private final Graphics2D scratch;
   private final FontMetrics metrics;
   private final int ascent;
   private final int descent;
   private final int cellHeight;
   private final float topOffset;
   private final float hudTopOffset;
   private final int textHeight;
   private final float capTopOffset;
   private final int capHeight;
   private final class_2960 textureId;
   private final class_1011 image;
   private final class_1043 texture;
   private final Map<Integer, GlyphFont.Glyph> glyphs = new HashMap<>();
   private int shelfX;
   private int shelfY;
   private boolean dirty;
   private boolean closed;

   public GlyphFont(Font baseFont, int size, int oversample) {
      this.oversample = Math.max(1, oversample);
      this.awtFont = baseFont.deriveFont(0, (float)size * this.oversample);
      BufferedImage probe = new BufferedImage(1, 1, 2);
      this.scratch = probe.createGraphics();
      applyHints(this.scratch);
      this.scratch.setFont(this.awtFont);
      this.metrics = this.scratch.getFontMetrics();
      this.ascent = this.metrics.getAscent();
      this.descent = this.metrics.getDescent();
      this.cellHeight = this.ascent + this.descent + 4;
      this.topOffset = (8.0F - (float)(this.ascent + this.descent) / this.oversample) / 2.0F;
      Rectangle textBounds = this.awtFont.createGlyphVector(this.scratch.getFontRenderContext(), "Ag").getPixelBounds(null, 0.0F, 0.0F);
      Rectangle capBounds = this.awtFont.createGlyphVector(this.scratch.getFontRenderContext(), "H").getPixelBounds(null, 0.0F, 0.0F);
      this.hudTopOffset = (float)(this.ascent + textBounds.y) / this.oversample;
      this.textHeight = Math.max(1, (int)Math.ceil((float)textBounds.height / this.oversample));
      this.capTopOffset = (float)(capBounds.y - textBounds.y) / this.oversample;
      this.capHeight = Math.max(1, (int)Math.ceil((float)capBounds.height / this.oversample));
      this.image = new class_1011(class_1012.field_4997, 1024, 1024, true);
      this.textureId = Homovore2.id("font/atlas_" + atlasCounter++);
      this.texture = new class_1043(() -> "Homovore font " + baseFont.getFontName(), this.image);
      class_310.method_1551().method_1531().method_4616(this.textureId, this.texture);
   }

   private static void applyHints(Graphics2D graphics) {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
      graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
   }

   public float width(String text) {
      float total = 0.0F;

      for (int i = 0; i < text.length(); i++) {
         total += this.charWidth(text.charAt(i));
      }

      return total;
   }

   public boolean canDisplay(String text) {
      return this.awtFont.canDisplayUpTo(text) == -1;
   }

   public float charWidth(char c) {
      return (float)this.advanceOf(c) / this.oversample;
   }

   public int textHeight() {
      return this.textHeight;
   }

   public int capHeight() {
      return this.capHeight;
   }

   public float capTopOffset() {
      return this.capTopOffset;
   }

   public int lineHeight() {
      return Math.max(9, this.textHeight() + 1);
   }

   public void draw(class_332 graphics, String text, float x, float y, int color) {
      this.drawAtMetricTop(graphics, text, x, y + this.topOffset, color);
   }

   public void drawTopAligned(class_332 graphics, String text, float x, float y, int color) {
      this.drawAtMetricTop(graphics, text, x, y - this.hudTopOffset, color);
   }

   private void drawAtMetricTop(class_332 graphics, String text, float x, float top, int color) {
      if (!this.closed && !text.isEmpty()) {
         Matrix3x2fStack pose = graphics.method_51448();
         pose.pushMatrix();
         pose.translate(x, top - 2.0F / this.oversample);
         pose.scale(1.0F / this.oversample, 1.0F / this.oversample);
         int pen = 0;

         for (int i = 0; i < text.length(); i++) {
            GlyphFont.Glyph glyph = this.glyph(text.charAt(i));
            if (glyph != null) {
               if (glyph.width() > 0 && glyph.height() > 0) {
                  graphics.method_25291(
                     class_10799.field_56883, this.textureId, pen - 2, 0, glyph.u(), glyph.v(), glyph.width(), glyph.height(), 1024, 1024, color
                  );
               }

               pen += glyph.advance();
            }
         }

         pose.popMatrix();
      }
   }

   public String trim(String text, int maxWidth) {
      if (this.width(text) <= maxWidth) {
         return text;
      }

      float limit = Math.max(0.0F, maxWidth - this.width("..."));
      float used = 0.0F;

      int end;
      for (end = 0; end < text.length(); end++) {
         float next = this.charWidth(text.charAt(end));
         if (used + next > limit) {
            break;
         }

         used += next;
      }

      return text.substring(0, end) + "...";
   }

   public void flush() {
      if (!this.closed && this.dirty) {
         this.texture.method_4524();
         this.dirty = false;
      }
   }

   @Override
   public void close() {
      if (!this.closed) {
         this.closed = true;
         this.scratch.dispose();
         class_310.method_1551().method_1531().method_4615(this.textureId);
      }
   }

   private int advanceOf(char c) {
      GlyphFont.Glyph baked = this.glyphs.get(Integer.valueOf(c));
      return baked != null ? baked.advance() : Math.max(0, this.metrics.charWidth(this.displayable(c)));
   }

   private char displayable(char c) {
      return this.awtFont.canDisplay(c) ? c : '?';
   }

   private GlyphFont.Glyph glyph(char c) {
      GlyphFont.Glyph existing = this.glyphs.get(Integer.valueOf(c));
      if (existing != null) {
         return existing;
      }

      GlyphFont.Glyph baked = this.bake(c);
      this.glyphs.put(Integer.valueOf(c), baked);
      return baked;
   }

   private GlyphFont.Glyph bake(char c) {
      char shown = this.displayable(c);
      int advance = Math.max(0, this.metrics.charWidth(shown));
      int cellWidth = advance + 4;
      if (advance != 0 && !Character.isWhitespace(shown)) {
         if (this.shelfX + cellWidth > 1024) {
            this.shelfX = 0;
            this.shelfY = this.shelfY + this.cellHeight;
         }

         if (this.shelfY + this.cellHeight > 1024) {
            return new GlyphFont.Glyph(0, 0, 0, 0, advance);
         }

         BufferedImage cell = new BufferedImage(cellWidth, this.cellHeight, 2);
         Graphics2D graphics = cell.createGraphics();
         applyHints(graphics);
         graphics.setFont(this.awtFont);
         graphics.setColor(Color.WHITE);
         graphics.drawString(String.valueOf(shown), 2, 2 + this.ascent);
         graphics.dispose();

         for (int row = 0; row < this.cellHeight; row++) {
            for (int column = 0; column < cellWidth; column++) {
               this.image.method_61941(this.shelfX + column, this.shelfY + row, cell.getRGB(column, row));
            }
         }

         GlyphFont.Glyph glyph = new GlyphFont.Glyph(this.shelfX, this.shelfY, cellWidth, this.cellHeight, advance);
         this.shelfX += cellWidth;
         this.dirty = true;
         return glyph;
      } else {
         return new GlyphFont.Glyph(0, 0, 0, 0, advance);
      }
   }

   private record Glyph(int u, int v, int width, int height, int advance) {
   }
}
