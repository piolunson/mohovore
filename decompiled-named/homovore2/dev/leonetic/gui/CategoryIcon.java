package dev.leonetic.gui;

import dev.leonetic.Homovore2;
import dev.leonetic.feature.module.Category;
import dev.leonetic.render.Render2D;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class CategoryIcon {
   public static final int SIZE = 10;
   public static final int GAP = 3;
   private static final Map<Category, Identifier> BUILTIN_TEXTURES = new IdentityHashMap<>();
   private static final Map<Category, CategoryIcon.Icon> PLUGIN_TEXTURES = new IdentityHashMap<>();

   private CategoryIcon() {
   }

   public static Identifier of(Category category) {
      CategoryIcon.Icon plugin = PLUGIN_TEXTURES.get(category);
      if (plugin == null) {
         return BUILTIN_TEXTURES.get(category);
      } else {
         return plugin.ready() ? plugin.id : null;
      }
   }

   public static void registerPlugin(Category category, String pluginId, byte[] png) throws IOException {
      NativeImage image = NativeImage.read(png);
      if (image.getWidth() <= 512 && image.getHeight() <= 512) {
         String safeId = pluginId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
         Identifier id = Homovore2.id("plugin/icon/" + safeId);
         CategoryIcon.Icon previous = PLUGIN_TEXTURES.put(category, new CategoryIcon.Icon(id, pluginId, image));
         if (previous != null) {
            previous.close();
         }
      } else {
         image.close();
         throw new IOException("Plugin icon dimensions may not exceed 512x512");
      }
   }

   public static void draw(DrawContext graphics, Category category, float x, float y, int color) {
      CategoryIcon.Icon plugin = PLUGIN_TEXTURES.get(category);
      if (plugin != null) {
         if (plugin.ready()) {
            Render2D.texture(graphics, plugin.id, x, y, 10, 10, plugin.width, plugin.height, color);
         } else {
            drawPuzzle(graphics, x, y, color);
         }
      } else {
         Identifier builtin = BUILTIN_TEXTURES.get(category);
         if (builtin != null) {
            Render2D.texture(graphics, builtin, x, y, 10, 10, color);
         } else {
            drawPuzzle(graphics, x, y, color);
         }
      }
   }

   private static void drawPuzzle(DrawContext graphics, float x, float y, int color) {
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
      private final Identifier id;
      private final String pluginId;
      private final int width;
      private final int height;
      private NativeImage pending;
      private boolean uploaded;

      Icon(Identifier id, String pluginId, NativeImage image) {
         this.id = id;
         this.pluginId = pluginId;
         this.width = image.getWidth();
         this.height = image.getHeight();
         this.pending = image;
      }

      boolean ready() {
         if (this.uploaded) {
            return true;
         }

         if (this.pending == null) {
            return false;
         }

         NativeImage image = this.pending;
         this.pending = null;

         try {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Homovore plugin icon " + this.pluginId, image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.id, texture);
            texture.upload();
            this.uploaded = true;
         } catch (Throwable throwable) {
            image.close();
            Homovore2.LOGGER.warn("Could not upload icon for plugin {}; using puzzle piece", this.pluginId, throwable);
         }

         return this.uploaded;
      }

      void close() {
         if (this.uploaded) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(this.id);
         }

         if (this.pending != null) {
            this.pending.close();
         }

         this.pending = null;
         this.uploaded = false;
      }
   }
}
