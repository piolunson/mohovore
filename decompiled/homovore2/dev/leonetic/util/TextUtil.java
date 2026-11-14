package dev.leonetic.util;

import java.util.Locale;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;

public final class TextUtil {
   private TextUtil() {
   }

   public static class_5250 text(String format, Object... args) {
      return parse(args.length == 0 ? format : String.format(format, args));
   }

   private static class_5250 parse(String input) {
      class_5250 out = class_2561.method_43473();
      class_2583 style = class_2583.field_24360;
      StringBuilder run = new StringBuilder();

      for (int i = 0; i < input.length(); i++) {
         char c = input.charAt(i);
         if (c != '{') {
            run.append(c);
         } else {
            int close = input.indexOf(125, i);
            if (close < 0) {
               run.append(c);
            } else {
               String token = input.substring(i + 1, close);
               class_2583 next = apply(style, token);
               if (next == null) {
                  run.append(c);
               } else {
                  if (!run.isEmpty()) {
                     out.method_10852(class_2561.method_43470(run.toString()).method_27696(style));
                     run.setLength(0);
                  }

                  style = next;
                  i = close;
               }
            }
         }
      }

      if (!run.isEmpty()) {
         out.method_10852(class_2561.method_43470(run.toString()).method_27696(style));
      }

      return out;
   }

   private static class_2583 apply(class_2583 style, String token) {
      String name = token.trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty() || name.equals("reset") || name.equals("r")) {
         return class_2583.field_24360;
      }

      if (name.startsWith("#")) {
         try {
            return style.method_36139(Integer.parseInt(name.substring(1), 16));
         } catch (NumberFormatException e) {
            return null;
         }
      } else {
         class_124 formatting = byName(name);
         if (formatting == null) {
            return null;
         } else {
            return isColor(formatting) ? style.method_10977(formatting) : style.method_27706(formatting);
         }
      }
   }

   public static class_124 byName(String name) {
      try {
         return class_124.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public static boolean isColor(class_124 formatting) {
      return formatting.ordinal() <= class_124.field_1068.ordinal();
   }
}
