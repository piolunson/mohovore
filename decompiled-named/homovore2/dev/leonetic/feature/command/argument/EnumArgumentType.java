package dev.leonetic.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.leonetic.feature.command.ArgumentSuggestions;
import dev.leonetic.feature.command.CommandExceptions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

public final class EnumArgumentType<T extends Enum<?>> implements ArgumentType<T> {
   private final Map<String, T> constants = new LinkedHashMap<>();

   private EnumArgumentType(Class<T> type) {
      for (T constant : (Enum[])type.getEnumConstants()) {
         this.constants.put(constant.name(), constant);
      }
   }

   public static <T extends Enum<?>> EnumArgumentType<T> enumeration(Class<T> type) {
      return new EnumArgumentType<>(type);
   }

   public T parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.readString();

      for (Entry<String, T> entry : this.constants.entrySet()) {
         if (entry.getKey().equalsIgnoreCase(value)) {
            return entry.getValue();
         }
      }

      throw CommandExceptions.invalidArgument("Expected one of %s", this.constants.keySet()).createWithContext(reader);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return ArgumentSuggestions.suggest(this.constants.keySet(), builder);
   }

   public static <T extends Enum<?>> T getEnum(CommandContext<?> ctx, String name) {
      return (T)ctx.getArgument(name, Object.class);
   }
}
