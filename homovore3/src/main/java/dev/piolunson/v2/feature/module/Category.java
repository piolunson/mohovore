package dev.piolunson.v2.feature.module;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Category {
   private static final List<Category> VALUES = new ArrayList<>();
   public static final Category COMBAT = builtin("COMBAT", "Combat", new Color(148, 0, 0));
   public static final Category SAFETY = builtin("SAFETY", "Safety", new Color(0, 148, 62));
   public static final Category MOVEMENT = builtin("MOVEMENT", "Movement", new Color(148, 79, 0));
   public static final Category RENDER = builtin("RENDER", "Render", new Color(0, 86, 148));
   public static final Category EXTRA = builtin("EXTRA", "Extra", new Color(111, 0, 148));
   public static final Category CLIENT = builtin("CLIENT", "Client", new Color(0, 148, 135));
   public static final Category HUD = builtin("HUD", "HUD", new Color(148, 0, 99));
   private final String id;
   private final String displayName;
   private final Color color;
   private final boolean plugin;

   private Category(String id, String displayName, Color color, boolean plugin) {
      this.id = id;
      this.displayName = displayName;
      this.color = color;
      this.plugin = plugin;
   }

   private static Category builtin(String id, String displayName, Color color) {
      Category category = new Category(id, displayName, color, false);
      VALUES.add(category);
      return category;
   }

   public static synchronized Category registerPlugin(String pluginId, String displayName, Color color) {
      Objects.requireNonNull(pluginId, "pluginId");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(color, "color");
      String id = "PLUGIN_" + pluginId.toUpperCase(Locale.ROOT);
      if (VALUES.stream().anyMatch(categoryx -> categoryx.id.equalsIgnoreCase(id))) {
         throw new IllegalArgumentException("A category already exists for plugin '" + pluginId + "'");
      }

      Category category = new Category(id, displayName, color, true);
      VALUES.add(category);
      return category;
   }

   public static synchronized void unregisterPlugin(Category category) {
      if (category != null && category.plugin) {
         VALUES.remove(category);
      }
   }

   public static synchronized Category[] values() {
      return VALUES.toArray(Category[]::new);
   }

   public String name() {
      return this.id;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public Color getColor() {
      return this.color;
   }

   public boolean isPlugin() {
      return this.plugin;
   }

   @Override
   public String toString() {
      return this.id;
   }
}
