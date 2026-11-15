package dev.piolunson.v2.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.piolunson.v2.feature.command.ArgumentSuggestions;
import dev.piolunson.v2.feature.command.CommandExceptions;
import dev.piolunson.v2.feature.setting.ListSetting;
import java.util.concurrent.CompletableFuture;

public record ListOptionArgumentType(ListSetting setting) implements ArgumentType<String> {
   public static ListOptionArgumentType option(ListSetting setting) {
      return new ListOptionArgumentType(setting);
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.readString();

      for (String option : this.setting.getOptions()) {
         if (option.equalsIgnoreCase(value)) {
            return option;
         }
      }

      throw CommandExceptions.invalidArgument("\"%s\" is not one of %s", value, this.setting.getOptions()).createWithContext(reader);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      this.setting.refresh();
      return ArgumentSuggestions.suggest(this.setting.getOptions(), builder);
   }

   public static String getOption(CommandContext<?> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }
}
