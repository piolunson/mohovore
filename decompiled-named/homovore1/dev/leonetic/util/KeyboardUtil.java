package dev.leonetic.util;

import dev.leonetic.features.settings.Bind;

public class KeyboardUtil {
   public static String getKeyName(int key) {
      String str = new Bind(key).toString().toUpperCase();
      return str.replace("KEY.KEYBOARD", "").replace(".", " ");
   }
}
