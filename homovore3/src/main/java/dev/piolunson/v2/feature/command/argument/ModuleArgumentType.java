package dev.piolunson.v2.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.command.ArgumentSuggestions;
import dev.piolunson.v2.feature.command.CommandExceptions;
import dev.piolunson.v2.feature.module.Module;
import java.util.concurrent.CompletableFuture;

public final class ModuleArgumentType implements ArgumentType<Module> {
   private static final ModuleArgumentType INSTANCE = new ModuleArgumentType();

   private ModuleArgumentType() {
   }

   public static ModuleArgumentType module() {
      return INSTANCE;
   }

   public Module parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.readString();

      for (Module module : Homovore2Client.modules().getModules()) {
         if (value.equalsIgnoreCase(module.getName())) {
            return module;
         }
      }

      throw CommandExceptions.invalidArgument("Unknown module \"%s\"", value).createWithContext(reader);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return ArgumentSuggestions.suggest(Homovore2Client.modules().getModules(), Module::getName, builder);
   }

   public static Module getModule(CommandContext<?> ctx, String name) {
      return (Module)ctx.getArgument(name, Module.class);
   }
}
