package dev.piolunson.v2.util;

import java.util.Locale;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TextUtil {
   private TextUtil() {
   }

   public static MutableText text(String format, Object... args) {
      return parse(args.length == 0 ? format : String.format(format, args));
   }

   private static MutableText parse(String input) {
      MutableText out = Text.empty();
      Style style = Style.EMPTY;
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
               Style next = apply(style, token);
               if (next == null) {
                  run.append(c);
               } else {
                  if (!run.isEmpty()) {
                     out.append(Text.literal(run.toString()).fillStyle(style));
                     run.setLength(0);
                  }

                  style = next;
                  i = close;
               }
            }
         }
      }

      if (!run.isEmpty()) {
         out.append(Text.literal(run.toString()).fillStyle(style));
      }

      return out;
   }

   private static Style apply(Style style, String token) {
      String name = token.trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty() || name.equals("reset") || name.equals("r")) {
         return Style.EMPTY;
      }

      if (name.startsWith("#")) {
         try {
            return style.withColor(Integer.parseInt(name.substring(1), 16));
         } catch (NumberFormatException e) {
            return null;
         }
      } else {
         Formatting formatting = byName(name);
         if (formatting == null) {
            return null;
         } else {
            return isColor(formatting) ? style.withColor(formatting) : style.withFormatting(formatting);
         }
      }
   }

   public static Formatting byName(String name) {
      try {
         return Formatting.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public static boolean isColor(Formatting formatting) {
      return formatting.ordinal() <= Formatting.WHITE.ordinal();
   }
}
