package dev.leonetic.util;

import dev.cattyn.catformat.CatFormat;
import dev.cattyn.catformat.fabric.FabricCatFormat;
import dev.leonetic.Homovore;
import net.minecraft.class_5250;

public final class TextUtil {
   private static CatFormat<class_5250> formatter;

   private TextUtil() {
      throw new AssertionError("Can't create an instance of utility class");
   }

   public static void init() {
      if (formatter != null) {
         throw new IllegalStateException("Formatter is already initialized");
      }

      formatter = new FabricCatFormat(true);
      initColors();
   }

   public static class_5250 text(String content, Object... obj) {
      return obj != null && obj.length != 0 ? (class_5250)formatter.format(content, obj) : (class_5250)formatter.format(content);
   }

   public static CatFormat<class_5250> getFormatter() {
      return formatter;
   }

   private static void initColors() {
      formatter.add("global", () -> Homovore.colorManager.getAsInt("ui"));
   }
}
