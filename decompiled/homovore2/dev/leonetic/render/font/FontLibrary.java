package dev.leonetic.render.font;

import dev.leonetic.Homovore2;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.class_310;

public final class FontLibrary {
   public static final String NONE = "None";
   private static final String FOLDER = "homovore/fonts";
   private static final Map<String, Font> folderFonts = new LinkedHashMap<>();
   private static final Map<String, Font> systemFonts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private static volatile boolean systemLoaded;
   private static boolean systemRequested;

   private FontLibrary() {
   }

   public static File folder() {
      File directory = new File(class_310.method_1551().field_1697, "homovore/fonts");
      if (!directory.isDirectory() && !directory.mkdirs()) {
         Homovore2.LOGGER.warn("Could not create font directory {}", directory);
      }

      return directory;
   }

   public static synchronized List<String> names() {
      requestSystemFonts();
      List<String> names = new ArrayList<>(folderFonts.size() + systemFonts.size() + 1);
      names.add("None");
      names.addAll(folderFonts.keySet());
      names.addAll(systemFonts.keySet());
      return names;
   }

   public static boolean isReady() {
      return systemLoaded;
   }

   public static synchronized Font lookup(String name) {
      if (name != null && !name.isBlank() && !"None".equalsIgnoreCase(name)) {
         requestSystemFonts();
         Font font = folderFonts.get(name);
         return font != null ? font : systemFonts.get(name);
      } else {
         return null;
      }
   }

   public static synchronized void rescanFolder() {
      File[] files = folder().listFiles();
      if (files != null) {
         folderFonts.clear();
         List<File> sorted = new ArrayList<>();

         for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (file.isFile() && (lower.endsWith(".ttf") || lower.endsWith(".otf"))) {
               sorted.add(file);
            }
         }

         sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

         for (File file : sorted) {
            try {
               Font font = Font.createFont(0, file);
               folderFonts.put(stripExtension(file.getName()), font);
            } catch (Exception e) {
               Homovore2.LOGGER.warn("Skipping unreadable font {}: {}", file.getName(), e.toString());
            }
         }
      }
   }

   private static void requestSystemFonts() {
      if (!systemRequested) {
         systemRequested = true;
         rescanFolder();
         Thread thread = new Thread(FontLibrary::loadSystemFonts, "Homovore font scan");
         thread.setDaemon(true);
         thread.start();
      }
   }

   private static void loadSystemFonts() {
      Map<String, Font> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

      try {
         for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            found.put(family, new Font(family, 0, 16));
         }
      } catch (Throwable t) {
         Homovore2.LOGGER.warn("Could not enumerate system fonts: {}", t.toString());
      }

      synchronized (FontLibrary.class) {
         systemFonts.putAll(found);
         systemLoaded = true;
      }
   }

   private static String stripExtension(String fileName) {
      int dot = fileName.lastIndexOf(46);
      return dot <= 0 ? fileName : fileName.substring(0, dot);
   }
}
