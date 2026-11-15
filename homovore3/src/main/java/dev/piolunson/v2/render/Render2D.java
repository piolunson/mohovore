package dev.piolunson.v2.render;

import dev.piolunson.v2.render.font.FontEngine;
import dev.piolunson.v2.render.font.GlyphFont;
import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class Render2D {
   private static final MinecraftClient MC = MinecraftClient.getInstance();
   public static float alpha = 1.0F;
   private static Render2D.Surface surface = Render2D.Surface.CLICK_GUI;
   public static final float TEXT_HEIGHT = 8.0F;

   public static Render2D.Surface surface(Render2D.Surface next) {
      Render2D.Surface previous = surface;
      surface = next;
      return previous;
   }

   private static GlyphFont font() {
      return surface == Render2D.Surface.HUD ? FontEngine.hud() : FontEngine.clickGui();
   }

   private Render2D() {
   }

   public static int fade(int color) {
      int a = (int)((color >>> 24 & 0xFF) * alpha);
      return color & 16777215 | a << 24;
   }

   public static int withAlpha(int color, int a) {
      return color & 16777215 | (a & 0xFF) << 24;
   }

   public static int scaleAlpha(int color, float factor) {
      return withAlpha(color, (int)((color >>> 24 & 0xFF) * factor));
   }

   public static int mix(int from, int to, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int result = 0;

      for (int shift = 0; shift < 32; shift += 8) {
         int a = from >>> shift & 0xFF;
         int b = to >>> shift & 0xFF;
         result |= Math.round(a + (b - a) * t) << shift;
      }

      return result;
   }

   public static void scissor(DrawContext graphics, float x1, float y1, float x2, float y2) {
      graphics.enableScissor((int)Math.floor(x1), (int)Math.floor(y1), (int)Math.ceil(x2), (int)Math.ceil(y2));
   }

   public static void unscissor(DrawContext graphics) {
      graphics.disableScissor();
   }

   public static void fill(DrawContext graphics, float x1, float y1, float x2, float y2, int color) {
      graphics.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), fade(color));
   }

   public static void texture(DrawContext graphics, Identifier texture, float x, float y, int width, int height, int color) {
      texture(graphics, texture, x, y, width, height, width, height, color);
   }

   public static void texture(DrawContext graphics, Identifier texture, float x, float y, int width, int height, int textureWidth, int textureHeight, int color) {
      graphics.drawTexture(
         RenderPipelines.GUI_TEXTURED,
         texture,
         Math.round(x),
         Math.round(y),
         0.0F,
         0.0F,
         width,
         height,
         textureWidth,
         textureHeight,
         textureWidth,
         textureHeight,
         fade(color)
      );
   }

   public static void outline(DrawContext graphics, float x1, float y1, float x2, float y2, int color, float width) {
      int w = Math.max(1, Math.round(width));
      fill(graphics, x1, y1, x2, y1 + w, color);
      fill(graphics, x2 - w, y1, x2, y2, color);
      fill(graphics, x1, y2 - w, x2, y2, color);
      fill(graphics, x1, y1, x1 + w, y2, color);
   }

   public static void verticalGradient(DrawContext graphics, float x1, float y1, float x2, float y2, Color top, Color bottom) {
      graphics.fillGradient(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), fade(top.getRGB()), fade(bottom.getRGB()));
   }

   public static void horizontalGradient(DrawContext graphics, float x1, float y1, float x2, float y2, Color left, Color right) {
      int start = Math.round(x1);
      int end = Math.round(x2);
      int span = Math.max(1, end - start);

      for (int x = start; x < end; x++) {
         float t = (float)(x - start) / span;
         int r = Math.round(left.getRed() + (right.getRed() - left.getRed()) * t);
         int g = Math.round(left.getGreen() + (right.getGreen() - left.getGreen()) * t);
         int b = Math.round(left.getBlue() + (right.getBlue() - left.getBlue()) * t);
         int a = Math.round(left.getAlpha() + (right.getAlpha() - left.getAlpha()) * t);
         fill(graphics, x, y1, x + 1, y2, new Color(r, g, b, a).getRGB());
      }
   }

   public static void rounded(DrawContext graphics, float x1, float y1, float x2, float y2, float radius, int color) {
      rounded(graphics, x1, y1, x2, y2, radius, radius, radius, radius, color);
   }

   public static void roundedTop(DrawContext graphics, float x1, float y1, float x2, float y2, float radius, int color) {
      rounded(graphics, x1, y1, x2, y2, radius, radius, 0.0F, 0.0F, color);
   }

   public static void roundedBottom(DrawContext graphics, float x1, float y1, float x2, float y2, float radius, int color) {
      rounded(graphics, x1, y1, x2, y2, 0.0F, 0.0F, radius, radius, color);
   }

   public static void rounded(
      DrawContext graphics, float x1, float y1, float x2, float y2, float topLeft, float topRight, float bottomLeft, float bottomRight, int color
   ) {
      float half = Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F);
      topLeft = clampRadius(topLeft, half);
      topRight = clampRadius(topRight, half);
      bottomLeft = clampRadius(bottomLeft, half);
      bottomRight = clampRadius(bottomRight, half);
      float top = Math.max(topLeft, topRight);
      float bottom = Math.max(bottomLeft, bottomRight);
      if (y2 - bottom > y1 + top) {
         fill(graphics, x1, y1 + top, x2, y2 - bottom, color);
      }

      for (int row = 0; row < Math.ceil(top); row++) {
         fill(graphics, x1 + inset(topLeft, row), y1 + row, x2 - inset(topRight, row), y1 + row + 1.0F, color);
      }

      for (int row = 0; row < Math.ceil(bottom); row++) {
         fill(graphics, x1 + inset(bottomLeft, row), y2 - row - 1.0F, x2 - inset(bottomRight, row), y2 - row, color);
      }
   }

   private static float clampRadius(float radius, float half) {
      return Math.max(0.0F, Math.min(radius, half));
   }

   private static float inset(float radius, int row) {
      if (row >= radius) {
         return 0.0F;
      }

      float dy = radius - row - 0.5F;
      return radius - (float)Math.sqrt(Math.max(0.0F, radius * radius - dy * dy));
   }

   public static void chevron(DrawContext graphics, float cx, float cy, float arm, float progress, int color) {
      float direction = progress * 2.0F - 1.0F;
      int steps = Math.max(1, Math.round(arm));

      for (int i = 0; i <= steps; i++) {
         float dy = (steps / 2.0F - i) * direction;
         fill(graphics, cx - steps + i, cy + dy, cx - steps + i + 1.0F, cy + dy + 1.0F, color);
         fill(graphics, cx + steps - i, cy + dy, cx + steps - i + 1.0F, cy + dy + 1.0F, color);
      }
   }

   public static float centeredTextY(float top, float height) {
      float textHeight = surface == Render2D.Surface.HUD ? textHeight() : 8.0F;
      return top + (height - textHeight) / 2.0F;
   }

   public static void text(DrawContext graphics, String text, float x, float y, int color) {
      GlyphFont font = font();
      if (font != null) {
         drawText(font, graphics, text, x, y, fade(color));
      } else {
         graphics.drawTextWithShadow(MC.textRenderer, text, Math.round(x), Math.round(y), fade(color));
      }
   }

   public static void text(DrawContext graphics, String text, float x, float y, int color, boolean shadow) {
      GlyphFont font = font();
      if (font != null) {
         if (shadow) {
            drawText(font, graphics, text, x + 1.0F, y + 1.0F, fade(shadow(color)));
         }

         drawText(font, graphics, text, x, y, fade(color));
      } else {
         graphics.drawText(MC.textRenderer, text, Math.round(x), Math.round(y), fade(color), shadow);
      }
   }

   private static void drawText(GlyphFont font, DrawContext graphics, String text, float x, float y, int color) {
      if (surface == Render2D.Surface.HUD) {
         font.drawTopAligned(graphics, text, x, y, color);
      } else {
         font.draw(graphics, text, x, y, color);
      }
   }

   private static int shadow(int color) {
      return color & 0xFF000000 | (color >>> 16 & 0xFF) / 4 << 16 | (color >>> 8 & 0xFF) / 4 << 8 | (color & 0xFF) / 4;
   }

   public static void textRight(DrawContext graphics, String text, float right, float y, int color) {
      text(graphics, text, right - width(text), y, color);
   }

   public static int width(String text) {
      GlyphFont font = font();
      return font != null ? (int)Math.ceil(font.width(text)) : MC.textRenderer.getWidth(text);
   }

   public static int lineHeight() {
      GlyphFont font = font();
      return font != null ? font.lineHeight() : 9;
   }

   public static boolean canDisplay(String text) {
      GlyphFont font = font();
      return font == null || font.canDisplay(text);
   }

   public static boolean usesCustomFont() {
      return font() != null;
   }

   public static int textHeight() {
      GlyphFont font = font();
      return font != null ? font.textHeight() : Math.round(8.0F);
   }

   public static int capHeight() {
      GlyphFont font = font();
      return font != null ? font.capHeight() : Math.round(8.0F);
   }

   public static float capTopOffset() {
      GlyphFont font = font();
      return font != null ? font.capTopOffset() : 0.0F;
   }

   public static String trim(String text, int maxWidth) {
      if (width(text) <= maxWidth) {
         return text;
      }

      GlyphFont font = font();
      return font != null ? font.trim(text, maxWidth) : MC.textRenderer.trimToWidth(text, Math.max(0, maxWidth - width("..."))) + "...";
   }

   public enum Surface {
      CLICK_GUI,
      HUD;
   }
}
