package dev.leonetic.gui;

import dev.leonetic.Homovore2;
import dev.leonetic.feature.module.Category;
import dev.leonetic.render.Render2D;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;

public final class CategoryIcon {
   public static final int SIZE = 10;
   public static final int GAP = 3;
   private static final Map<Category, class_2960> BUILTIN_TEXTURES = new IdentityHashMap<>();
   private static final Map<Category, CategoryIcon.Icon> PLUGIN_TEXTURES = new IdentityHashMap<>();

   private CategoryIcon() {
   }

   public static class_2960 of(Category category) {
      CategoryIcon.Icon plugin = PLUGIN_TEXTURES.get(category);
      if (plugin == null) {
         return BUILTIN_TEXTURES.get(category);
      } else {
         return plugin.ready() ? plugin.id : null;
      }
   }

   public static void registerPlugin(Category category, String pluginId, byte[] png) throws IOException {
      class_1011 image = class_1011.method_49277(png);
      if (image.method_4307() <= 512 && image.method_4323() <= 512) {
         String safeId = pluginId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
         class_2960 id = Homovore2.id("plugin/icon/" + safeId);
         CategoryIcon.Icon previous = PLUGIN_TEXTURES.put(category, new CategoryIcon.Icon(id, pluginId, image));
         if (previous != null) {
            previous.close();
         }
      } else {
         image.close();
         throw new IOException("Plugin icon dimensions may not exceed 512x512");
      }
   }

   public static void draw(class_332 graphics, Category category, float x, float y, int color) {
      CategoryIcon.Icon plugin = PLUGIN_TEXTURES.get(category);
      if (plugin != null) {
         if (plugin.ready()) {
            Render2D.texture(graphics, plugin.id, x, y, 10, 10, plugin.width, plugin.height, color);
         } else {
            drawPuzzle(graphics, x, y, color);
         }
      } else {
         class_2960 builtin = BUILTIN_TEXTURES.get(category);
         if (builtin != null) {
            Render2D.texture(graphics, builtin, x, y, 10, 10, color);
         } else {
            drawPuzzle(graphics, x, y, color);
         }
      }
   }

   private static void drawPuzzle(class_332 graphics, float x, float y, int color) {
      Render2D.fill(graphics, x + 2.0F, y + 2.0F, x + 8.0F, y + 8.0F, color);
      Render2D.fill(graphics, x + 4.0F, y, x + 6.0F, y + 3.0F, color);
      Render2D.fill(graphics, x, y + 4.0F, x + 3.0F, y + 6.0F, color);
      Render2D.fill(graphics, x + 7.0F, y + 4.0F, x + 10.0F, y + 6.0F, color);
      Render2D.fill(graphics, x + 4.0F, y + 7.0F, x + 6.0F, y + 10.0F, color);
   }

   public static void closePluginTextures() {
      for (CategoryIcon.Icon icon : PLUGIN_TEXTURES.values()) {
         icon.close();
      }

      PLUGIN_TEXTURES.clear();
   }

   static {
      for (Category category : Category.values()) {
         if (!category.isPlugin()) {
            BUILTIN_TEXTURES.put(category, Homovore2.id("textures/gui/icon/" + category.name().toLowerCase(Locale.ROOT) + ".png"));
         }
      }
   }

   private static final class Icon {
      private final class_2960 id;
      private final String pluginId;
      private final int width;
      private final int height;
      private class_1011 pending;
      private boolean uploaded;

      Icon(class_2960 id, String pluginId, class_1011 image) {
         this.id = id;
         this.pluginId = pluginId;
         this.width = image.method_4307();
         this.height = image.method_4323();
         this.pending = image;
      }

      boolean ready() {
         if (this.uploaded) {
            return true;
         }

         if (this.pending == null) {
            return false;
         }

         class_1011 image = this.pending;
         this.pending = null;

         try {
            class_1043 texture = new class_1043(() -> "Homovore plugin icon " + this.pluginId, image);
            class_310.method_1551().method_1531().method_4616(this.id, texture);
            texture.method_4524();
            this.uploaded = true;
         } catch (Throwable throwable) {
            image.close();
            Homovore2.LOGGER.warn("Could not upload icon for plugin {}; using puzzle piece", this.pluginId, throwable);
         }

         return this.uploaded;
      }

      void close() {
         if (this.uploaded) {
            class_310.method_1551().method_1531().method_4615(this.id);
         }

         if (this.pending != null) {
            this.pending.close();
         }

         this.pending = null;
         this.uploaded = false;
      }
   }
}
