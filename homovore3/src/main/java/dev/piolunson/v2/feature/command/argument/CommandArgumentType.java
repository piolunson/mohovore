package dev.piolunson.v2.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.command.ArgumentSuggestions;
import dev.piolunson.v2.feature.command.Command;
import dev.piolunson.v2.feature.command.CommandExceptions;
import java.util.concurrent.CompletableFuture;

public final class CommandArgumentType implements ArgumentType<Command> {
   private static final CommandArgumentType INSTANCE = new CommandArgumentType();

   private CommandArgumentType() {
   }

   public static CommandArgumentType command() {
      return INSTANCE;
   }

   public Command parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.readString();
      Command command = Homovore2Client.commands().getCommand(value);
      if (command == null) {
         throw CommandExceptions.invalidArgument("Unknown command \"%s\"", value).createWithContext(reader);
      } else {
         return command;
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return ArgumentSuggestions.suggest(Homovore2Client.commands().getCommands().stream().filter(Command::isShown).map(Command::getName), builder);
   }

   public static Command getCommand(CommandContext<?> ctx, String name) {
      return (Command)ctx.getArgument(name, Command.class);
   }
}
