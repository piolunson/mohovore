package dev.piolunson.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

public class OnlinePlayerArgumentType implements ArgumentType<String> {
   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
      if (conn == null) {
         return builder.buildFuture();
      }

      String input = builder.getRemainingLowerCase();

      for (PlayerListEntry info : conn.getPlayerList()) {
         String name = info.getProfile().name();
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
