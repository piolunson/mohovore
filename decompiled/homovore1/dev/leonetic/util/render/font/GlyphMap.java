package dev.leonetic.util.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_1011.class_1012;
import org.lwjgl.system.MemoryUtil;

public class GlyphMap {
   private static final AtomicInteger ID_COUNTER = new AtomicInteger();
   private final Font[] fonts;
   private final char include;
   private final char exclude;
   private final int padding;
   private final Char2ObjectArrayMap<GlyphMap.Glyph> glyphs = new Char2ObjectArrayMap();
   private boolean generated;
   private class_1043 texture;
   private class_2960 textureId;
   private int width;
   private int height;

   public GlyphMap(Font[] fonts, char include, char exclude, int padding) {
      this.fonts = fonts;
      this.include = include;
      this.exclude = exclude;
      this.padding = padding;
   }

   public void generate() {
      synchronized (this) {
         this.generateInternal();
      }
   }

   public GlyphMap.Glyph getGlyph(char character) {
      synchronized (this) {
         if (!this.generated) {
            this.generateInternal();
         }

         return (GlyphMap.Glyph)this.glyphs.get(character);
      }
   }

   public boolean contains(char c) {
      return c >= this.include && c < this.exclude;
   }

   public void destroy() {
      synchronized (this) {
         this.generated = false;
         if (this.texture != null) {
            class_310 mc = class_310.method_1551();
            if (mc != null && this.textureId != null) {
               mc.method_1531().method_4615(this.textureId);
            }

            this.texture.close();
            this.texture = null;
            this.textureId = null;
         }

         this.glyphs.clear();
         this.width = -1;
         this.height = -1;
      }
   }

   public class_2960 getTextureId() {
      return this.textureId;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   private void generateInternal() {
      if (!this.generated) {
         int count = this.exclude - this.include;
         if (count <= 0) {
            this.width = 1;
            this.height = 1;
            this.generated = true;
         } else {
            FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);
            int[] gw = new int[count];
            int[] gh = new int[count];
            float[] adv = new float[count];
            char[] chars = new char[count];

            for (int i = 0; i < count; i++) {
               char c = (char)(this.include + i);
               chars[i] = c;
               Font font = this.getFontForGlyph(c);
               Rectangle2D b = font.getStringBounds(String.valueOf(c), frc);
               adv[i] = (float)b.getWidth();
               gw[i] = (int)Math.ceil(adv[i]);
               gh[i] = (int)Math.ceil(b.getHeight());
            }

            int colsTarget = (int)Math.ceil(Math.sqrt(count) * 1.5);
            int[] ux = new int[count];
            int[] uy = new int[count];
            int curX = 0;
            int curY = 0;
            int rowH = 0;
            int maxX = 0;
            int col = 0;

            for (int i = 0; i < count; i++) {
               int w = Math.max(gw[i], 1);
               int h = Math.max(gh[i], 1);
               if (col >= colsTarget) {
                  curX = 0;
                  curY += rowH + this.padding;
                  rowH = 0;
                  col = 0;
               }

               ux[i] = curX;
               uy[i] = curY;
               curX += w + this.padding;
               if (curX > maxX) {
                  maxX = curX;
               }

               if (h > rowH) {
                  rowH = h;
               }

               col++;
            }

            int atlasW = Math.max(maxX + this.padding, 1);
            int atlasH = Math.max(curY + rowH + this.padding, 1);
            BufferedImage img = new BufferedImage(atlasW, atlasH, 2);
            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, atlasW, atlasH);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            for (int i = 0; i < count; i++) {
               if (gw[i] > 0 && gh[i] > 0) {
                  Font font = this.getFontForGlyph(chars[i]);
                  g.setFont(font);
                  FontMetrics fm = g.getFontMetrics();
                  g.drawString(String.valueOf(chars[i]), ux[i], uy[i] + fm.getAscent());
                  this.glyphs.put(chars[i], new GlyphMap.Glyph(this, ux[i], uy[i], gw[i], gh[i], adv[i], chars[i]));
               } else {
                  this.glyphs.put(chars[i], new GlyphMap.Glyph(this, 0, 0, 0, 0, adv[i], chars[i]));
               }
            }

            g.dispose();
            this.width = atlasW;
            this.height = atlasH;
            int[] rgb = new int[atlasW * atlasH];
            img.getRGB(0, 0, atlasW, atlasH, rgb, 0, atlasW);
            class_1011 native_ = new class_1011(class_1012.field_4997, atlasW, atlasH, true);
            IntBuffer buf = MemoryUtil.memIntBuffer(native_.method_67769(), atlasW * atlasH);

            for (int i = 0; i < rgb.length; i++) {
               int p = rgb[i];
               int a = p >>> 24 & 0xFF;
               int r = p >>> 16 & 0xFF;
               int g2 = p >>> 8 & 0xFF;
               int b = p & 0xFF;
               buf.put(a << 24 | b << 16 | g2 << 8 | r);
            }

            int id = ID_COUNTER.getAndIncrement();
            this.texture = new GlyphMap.LinearDynamicTexture(() -> "HomovoreFontPage" + id, native_);
            this.texture.method_4524();
            this.textureId = class_2960.method_60655("homovore", "font/page_" + id);
            class_310.method_1551().method_1531().method_4616(this.textureId, this.texture);
            this.generated = true;
         }
      }
   }

   private Font getFontForGlyph(char c) {
      for (Font font : this.fonts) {
         if (font.canDisplay(c)) {
            return font;
         }
      }

      return this.fonts[0];
   }

   public record Glyph(GlyphMap parent, int u, int v, int width, int height, float advance, char value) {
   }

   private static final class LinearDynamicTexture extends class_1043 {
      LinearDynamicTexture(Supplier<String> label, class_1011 image) {
         super(label, image);
         this.field_63613 = RenderSystem.getSamplerCache().method_75294(FilterMode.LINEAR);
      }
   }
}
