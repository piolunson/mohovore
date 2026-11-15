package dev.piolunson.v2.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.piolunson.v2.feature.command.CommandExceptions;
import java.util.Collection;
import java.util.List;

public record NumberArgumentType<T extends Number>(Class<T> type, Number min, Number max) implements ArgumentType<T> {
   public static <T extends Number> NumberArgumentType<T> number(Class<T> type) {
      return new NumberArgumentType<>(type, null, null);
   }

   public static <T extends Number> NumberArgumentType<T> number(Class<T> type, Number min, Number max) {
      return new NumberArgumentType<>(type, min, max);
   }

   public T parse(StringReader reader) throws CommandSyntaxException {
      int start = reader.getCursor();
      String input = reader.readString();

      Number value;
      try {
         if (this.type == Integer.class) {
            value = Integer.valueOf(input);
         } else if (this.type == Long.class) {
            value = Long.valueOf(input);
         } else if (this.type == Float.class) {
            value = Float.valueOf(input);
         } else {
            if (this.type != Double.class) {
               throw CommandExceptions.invalidArgument("Unsupported number type %s", this.type.getSimpleName()).createWithContext(reader);
            }

            value = Double.valueOf(input);
         }
      } catch (NumberFormatException e) {
         reader.setCursor(start);
         throw CommandExceptions.invalidArgument("\"%s\" is not a number", input).createWithContext(reader);
      }

      if (this.min != null && value.doubleValue() < this.min.doubleValue()) {
         throw CommandExceptions.invalidArgument("Value must be at least %s", this.min).createWithContext(reader);
      } else if (this.max != null && value.doubleValue() > this.max.doubleValue()) {
         throw CommandExceptions.invalidArgument("Value must be at most %s", this.max).createWithContext(reader);
      } else {
         return (T)value;
      }
   }

   public Collection<String> getExamples() {
      return this.min != null && this.max != null ? List.of(String.valueOf(this.min), String.valueOf(this.max)) : List.of("1", "1.5");
   }

   public static <T extends Number> T get(Class<T> type, CommandContext<?> ctx, String name) {
      return (T)ctx.getArgument(name, type);
   }
}
