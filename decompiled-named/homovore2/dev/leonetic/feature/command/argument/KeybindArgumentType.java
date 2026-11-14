package dev.leonetic.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.leonetic.feature.command.ArgumentSuggestions;
import dev.leonetic.feature.command.CommandExceptions;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.util.KeyboardUtil;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KeybindArgumentType implements ArgumentType<Bind> {
   private static final List<String> EXAMPLES = List.of("A", "RIGHT_SHIFT", "MOUSE1", "NONE");
   private static final KeybindArgumentType INSTANCE = new KeybindArgumentType();

   private KeybindArgumentType() {
   }

   public static KeybindArgumentType keybind() {
      return INSTANCE;
   }

   public Bind parse(StringReader reader) throws CommandSyntaxException {
      String input = reader.readString();
      Bind bind = KeyboardUtil.parse(input);
      if (bind == null) {
         throw CommandExceptions.invalidArgument("Unknown key \"%s\"", input).createWithContext(reader);
      } else {
         return bind;
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return ArgumentSuggestions.suggest(KeyboardUtil.names(), builder);
   }

   public Collection<String> getExamples() {
      return EXAMPLES;
   }

   public static Bind getBind(CommandContext<?> ctx, String name) {
      return (Bind)ctx.getArgument(name, Bind.class);
   }
}
