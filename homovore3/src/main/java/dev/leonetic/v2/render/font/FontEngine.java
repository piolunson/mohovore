package dev.leonetic.v2.render.font;

import dev.leonetic.v2.Homovore2;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.impl.ClickGuiModule;
import java.awt.Font;
import net.minecraft.client.MinecraftClient;

public final class FontEngine {
   private static FontEngine.Baked clickGui;
   private static FontEngine.Baked hud;

   private FontEngine() {
   }

   public static GlyphFont clickGui() {
      ClickGuiModule module = settings();
      if (module != null && module.clickGuiFont.getValue()) {
         clickGui = resolve(clickGui, module.fontName.getValue(), module.fontSize.getValue());
         return clickGui == null ? null : clickGui.font();
      } else {
         return dispose(true);
      }
   }

   public static GlyphFont hud() {
      ClickGuiModule module = settings();
      if (module != null && module.hudFont.getValue()) {
         hud = resolve(hud, module.fontName.getValue(), module.fontSize.getValue());
         return hud == null ? null : hud.font();
      } else {
         return dispose(false);
      }
   }

   public static void flush() {
      if (clickGui != null) {
         clickGui.font().flush();
      }

      if (hud != null) {
         hud.font().flush();
      }
   }

   private static FontEngine.Baked resolve(FontEngine.Baked current, String name, int size) {
      int oversample = oversample();
      if (current != null && current.matches(name, size, oversample)) {
         return current;
      }

      if (current != null) {
         current.font().close();
      }

      Font base = FontLibrary.lookup(name);
      if (base == null) {
         return null;
      }

      try {
         return new FontEngine.Baked(name, size, oversample, new GlyphFont(base, size, oversample));
      } catch (Exception e) {
         Homovore2.LOGGER.error("Could not bake font {}", name, e);
         return null;
      }
   }

   private static GlyphFont dispose(boolean isClickGui) {
      FontEngine.Baked baked = isClickGui ? clickGui : hud;
      if (baked != null) {
         baked.font().close();
      }

      if (isClickGui) {
         clickGui = null;
      } else {
         hud = null;
      }

      return null;
   }

   private static int oversample() {
      return Math.max(1, Math.min(3, MinecraftClient.getInstance().getWindow().getScaleFactor()));
   }

   private static ClickGuiModule settings() {
      return Homovore2Client.modules() == null ? null : Homovore2Client.modules().get(ClickGuiModule.class);
   }

   private record Baked(String name, int size, int oversample, GlyphFont font) {
      boolean matches(String name, int size, int oversample) {
         return this.size == size && this.oversample == oversample && this.name.equals(name);
      }
   }
}
