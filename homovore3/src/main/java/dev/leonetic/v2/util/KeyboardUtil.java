package dev.leonetic.v2.util;

import dev.leonetic.v2.feature.setting.Bind;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

public final class KeyboardUtil {
   public static final String NONE = "NONE";
   private static final int MOUSE_BUTTONS = 8;
   private static final Map<String, Integer> KEYS = new LinkedHashMap<>();
   private static final List<String> NAMES = new ArrayList<>();

   private KeyboardUtil() {
   }

   public static Bind parse(String input) {
      String name = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
      if (name.isEmpty() || name.equals("NONE")) {
         return Bind.none();
      }

      if (!name.startsWith("MOUSE")) {
         Integer key = KEYS.get(name);
         return key == null ? null : new Bind(key);
      }

      try {
         int button = Integer.parseInt(name.substring(5));
         return button >= 1 && button <= 8 ? Bind.mouse(button - 1) : null;
      } catch (NumberFormatException e) {
         return null;
      }
   }

   public static List<String> names() {
      return NAMES;
   }

   static {
      NAMES.add("NONE");

      for (Field field : GLFW.class.getDeclaredFields()) {
         if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class && field.getName().startsWith("GLFW_KEY_")) {
            String name = field.getName().substring("GLFW_KEY_".length());
            if (!name.equals("UNKNOWN") && !name.equals("LAST")) {
               try {
                  int code = field.getInt(null);
                  if (KEYS.putIfAbsent(name, code) == null) {
                     NAMES.add(name);
                  }
               } catch (IllegalAccessException var6) {
               }
            }
         }
      }

      for (int button = 1; button <= 8; button++) {
         NAMES.add("MOUSE" + button);
      }
   }
}
