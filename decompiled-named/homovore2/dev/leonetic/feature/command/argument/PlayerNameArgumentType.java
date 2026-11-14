package dev.leonetic.feature.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

public final class PlayerNameArgumentType implements ArgumentType<String> {
   private final Supplier<List<String>> candidates;

   private PlayerNameArgumentType(Supplier<List<String>> candidates) {
      this.candidates = candidates;
   }

   public static PlayerNameArgumentType onlinePlayer() {
      return new PlayerNameArgumentType(PlayerNameArgumentType::onlineNames);
   }

   public static PlayerNameArgumentType playerName(Supplier<List<String>> candidates) {
      return new PlayerNameArgumentType(candidates);
   }

   public static String getPlayerName(CommandContext<?> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemainingLowerCase();

      for (String name : this.candidates.get()) {
         if (name.toLowerCase(Locale.ROOT).contains(remaining)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   private static List<String> onlineNames() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      if (connection == null) {
         return List.of();
      }

      String self = mc.player == null ? null : mc.player.getGameProfile().name();
      List<String> names = new ArrayList<>();

      for (PlayerListEntry info : connection.getPlayerList()) {
         String name = info.getProfile().name();
         if (name != null && !name.equals(self)) {
            names.add(name);
         }
      }

      return names;
   }
}
