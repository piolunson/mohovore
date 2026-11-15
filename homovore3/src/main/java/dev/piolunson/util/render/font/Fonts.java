package dev.piolunson.util.render.font;

import dev.piolunson.features.modules.client.ClickGuiModule;
import java.awt.Font;
import java.io.InputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

public final class Fonts {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final String BUNDLED_PATH = "/assets/homovore/font/default.ttf";
   private static final float SHADOW_OFFSET = 0.7F;
   private static volatile FontRenderer renderer;
   private static volatile boolean dirty = true;
   private static volatile boolean hudPass = false;
   private static Font bundledBase;
   private static boolean bundledLoaded;

   private Fonts() {
   }

   private static ClickGuiModule cfg() {
      return ClickGuiModule.getInstance();
   }

   private static boolean clickGuiOn() {
      ClickGuiModule c = cfg();
      return c != null && c.clickGuiFont.getValue();
   }

   private static boolean hudOn() {
      ClickGuiModule c = cfg();
      return c != null && c.hudFont.getValue();
   }

   private static boolean anyOn() {
      return clickGuiOn() || hudOn();
   }

   public static void markDirty() {
      dirty = true;
   }

   public static void beginHudPass() {
      hudPass = true;
   }

   public static void endHudPass() {
      hudPass = false;
   }

   public static void drawString(DrawContext graphics, String text, float x, float y, int color) {
      FontRenderer fr = clickGuiOn() ? renderer() : null;
      if (fr == null) {
         graphics.drawTextWithShadow(mc.textRenderer, text, (int)x, (int)y, color);
      } else {
         fr.drawString(graphics, text, x + 0.7F, y + 0.7F, color, true);
         fr.drawString(graphics, text, x, y, color, false);
      }
   }

   public static int width(String text) {
      FontRenderer fr = clickGuiOn() ? renderer() : null;
      return fr == null ? mc.textRenderer.getWidth(text) : Math.round(fr.getTextWidth(text));
   }

   public static int lineHeight() {
      FontRenderer fr = clickGuiOn() ? renderer() : null;
      return fr == null ? 9 : Math.max(1, Math.round(fr.getHeight()));
   }

   public static boolean drawOverrideActive() {
      return hudPass && hudOn() && renderer() != null;
   }

   public static void renderOverrideString(DrawContext graphics, String text, float x, float y, int color, boolean shadow) {
      FontRenderer fr = renderer();
      if (fr != null) {
         if (shadow) {
            fr.drawString(graphics, text, x + 0.7F, y + 0.7F, color, true);
         }

         fr.drawString(graphics, text, x, y, color, false);
      }
   }

   public static void renderOverrideText(DrawContext graphics, OrderedText text, float x, float y, int color, boolean shadow) {
      FontRenderer fr = renderer();
      if (fr != null) {
         if (shadow) {
            fr.drawText(graphics, text, x + 0.7F, y + 0.7F, color, true);
         }

         fr.drawText(graphics, text, x, y, color, false);
      }
   }

   public static int overrideWidth(String text) {
      FontRenderer fr = renderer();
      return fr == null ? mc.textRenderer.getWidth(text) : Math.round(fr.getTextWidth(text));
   }

   public static int overrideWidth(OrderedText text) {
      FontRenderer fr = renderer();
      return fr == null ? mc.textRenderer.getWidth(text) : Math.round(fr.getTextWidth(text));
   }

   private static FontRenderer renderer() {
      if (!anyOn()) {
         disposeIfPresent();
         return null;
      }

      if (mc.getWindow() == null) {
         return null;
      }

      if (dirty || renderer == null) {
         rebuild();
      }

      return renderer;
   }

   private static synchronized void rebuild() {
      if (dirty || renderer == null) {
         disposeIfPresent();
         ClickGuiModule c = cfg();
         Font base = resolveBaseFont(c == null ? "" : c.fontName.getValue());
         renderer = new FontRenderer(new Font[]{base});
         dirty = false;
      }
   }

   private static synchronized void disposeIfPresent() {
      FontRenderer old = renderer;
      renderer = null;
      if (old != null) {
         old.close();
      }
   }

   private static Font resolveBaseFont(String familyName) {
      if (familyName != null && !familyName.isBlank()) {
         return new Font(familyName.trim(), 0, 1);
      }

      Font bundled = bundled();
      return bundled != null ? bundled : new Font("SansSerif", 0, 1);
   }

   private static Font bundled() {
      if (bundledLoaded) {
         return bundledBase;
      }

      bundledLoaded = true;

      try (InputStream is = Fonts.class.getResourceAsStream("/assets/homovore/font/default.ttf")) {
         if (is != null) {
            bundledBase = Font.createFont(0, is).deriveFont(1.0F);
         }
      } catch (Exception e) {
         bundledBase = null;
      }

      return bundledBase;
   }
}
