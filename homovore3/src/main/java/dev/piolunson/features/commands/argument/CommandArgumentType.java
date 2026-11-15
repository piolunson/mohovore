package dev.piolunson.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.piolunson.Homovore;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.commands.CommandExceptions;
import java.util.concurrent.CompletableFuture;

public class CommandArgumentType implements ArgumentType<Command>, CommandExceptionType {
   public Command parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.readString().toLowerCase();

      for (String alias : Homovore.commandManager.getCommandAliases()) {
         if (value.equalsIgnoreCase(alias)) {
            return Homovore.commandManager.getCommand(alias);
         }
      }

      throw CommandExceptions.invalidArgument("Invalid command").createWithContext(reader);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String input = builder.getRemainingLowerCase();

      for (String alias : Homovore.commandManager.getCommandAliases()) {
         String name = alias.toLowerCase();
         if (name.contains(input)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   public static Command getCommand(CommandContext<?> ctx, String name) {
      return (Command)ctx.getArgument(name, Command.class);
   }

   public static CommandArgumentType command() {
      return new CommandArgumentType();
   }
}
