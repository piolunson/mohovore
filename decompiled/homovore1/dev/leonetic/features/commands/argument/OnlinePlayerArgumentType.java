package dev.leonetic.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.class_310;
import net.minecraft.class_634;
import net.minecraft.class_640;

public class OnlinePlayerArgumentType implements ArgumentType<String> {
   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      class_634 conn = class_310.method_1551().method_1562();
      if (conn == null) {
         return builder.buildFuture();
      }

      String input = builder.getRemainingLowerCase();

      for (class_640 info : conn.method_2880()) {
         String name = info.method_2966().name();
         if (name != null && (input.isEmpty() || name.toLowerCase().contains(input))) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   public static OnlinePlayerArgumentType onlinePlayer() {
      return new OnlinePlayerArgumentType();
   }

   public static String getOnlinePlayer(CommandContext<?> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }
}
