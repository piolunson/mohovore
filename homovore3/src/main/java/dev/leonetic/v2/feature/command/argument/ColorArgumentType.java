package dev.leonetic.v2.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.leonetic.v2.feature.command.ArgumentSuggestions;
import dev.leonetic.v2.feature.command.CommandExceptions;
import dev.leonetic.v2.util.TextUtil;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public final class ColorArgumentType implements ArgumentType<Color> {
   private static final List<String> EXAMPLES = List.of("#ff00ff", "hsb:55,100,100", "255,0,255", "0,255,255,80");
   private static final String[] RGBA_NAMES = new String[]{"Red", "Green", "Blue", "Alpha"};
   private static final ColorArgumentType INSTANCE = new ColorArgumentType();

   private ColorArgumentType() {
   }

   public static ColorArgumentType color() {
      return INSTANCE;
   }

   public Color parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.getRemaining().trim();
      reader.setCursor(reader.getTotalLength());
      if (value.isEmpty()) {
         throw CommandExceptions.invalidArgument("Expected a colour").createWithContext(reader);
      }

      if (value.startsWith("#")) {
         String hex = value.substring(1);
         if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
         }

         try {
            return new Color(Integer.parseInt(hex, 16));
         } catch (NumberFormatException e) {
            throw CommandExceptions.invalidArgument("\"%s\" is not a hex colour", value).createWithContext(reader);
         }
      } else {
         if (value.toLowerCase(Locale.ROOT).startsWith("hsb:")) {
            int[] parts = parseElements(reader, value.substring(4));
            int hue = range(reader, parts[0], 0, 360, "Hue");
            int saturation = range(reader, parts[1], 0, 100, "Saturation");
            int brightness = range(reader, parts[2], 0, 100, "Brightness");
            return Color.getHSBColor(hue / 360.0F, saturation / 100.0F, brightness / 100.0F);
         }

         Formatting named = TextUtil.byName(value);
         if (named != null && TextUtil.isColor(named)) {
            TextColor color = TextColor.fromFormatting(named);
            if (color != null) {
               return new Color(color.getRgb());
            }
         }

         int[] parts = parseElements(reader, value);

         for (int i = 0; i < parts.length; i++) {
            range(reader, parts[i], 0, 255, RGBA_NAMES[Math.min(i, RGBA_NAMES.length - 1)]);
         }

         return new Color(parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : 255);
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String input = builder.getRemainingLowerCase();
      if (input.isEmpty()) {
         builder.suggest("#");
         builder.suggest("hsb:");
      }

      return input.isEmpty() || !input.startsWith("#") && !Character.isDigit(input.charAt(0))
         ? ArgumentSuggestions.suggest(
            Arrays.stream(Formatting.values()).filter(TextUtil::isColor).map(formatting -> formatting.name().toLowerCase(Locale.ROOT)), builder
         )
         : builder.buildFuture();
   }

   public Collection<String> getExamples() {
      return EXAMPLES;
   }

   private static int[] parseElements(StringReader reader, String value) throws CommandSyntaxException {
      String[] parts = value.split(",");
      if (parts.length >= 3 && parts.length <= 4) {
         int[] elements = new int[parts.length];

         for (int i = 0; i < parts.length; i++) {
            try {
               elements[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
               throw CommandExceptions.invalidArgument("\"%s\" is not a number", parts[i].trim()).createWithContext(reader);
            }
         }

         return elements;
      } else {
         throw CommandExceptions.invalidArgument("A colour needs three components (with an optional alpha), as RGB(A) or hsb:HSB").createWithContext(reader);
      }
   }

   private static int range(StringReader reader, int value, int min, int max, String name) throws CommandSyntaxException {
      if (value >= min && value <= max) {
         return value;
      } else {
         throw CommandExceptions.invalidArgument("%s must be between %d and %d", name, min, max).createWithContext(reader);
      }
   }

   public static Color getColor(CommandContext<?> ctx, String name) {
      return (Color)ctx.getArgument(name, Color.class);
   }
}
