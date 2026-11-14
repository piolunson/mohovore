package dev.leonetic.feature.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.command.Command;
import dev.leonetic.feature.command.CommandManager;
import dev.leonetic.feature.command.argument.PlayerNameArgumentType;
import dev.leonetic.manager.FriendManager;
import java.util.List;
import java.util.StringJoiner;
import net.minecraft.entity.player.PlayerEntity;

public class FriendCommand extends Command {
   private static final long DAY_MILLIS = 86400000L;

   public FriendCommand() {
      super("friend", "friends", "f");
      this.setDescription("Manages your friends list");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(
                        literal("list").executes(ctx -> this.list())
                     ))
                     .then(literal("clear").executes(ctx -> {
                        Homovore2Client.friends().clearFriends();
                        return this.success("Cleared friends list");
                     })))
                  .then(
                     literal("add")
                        .then(
                           argument("username", PlayerNameArgumentType.onlinePlayer())
                              .executes(ctx -> this.addPlayer(PlayerNameArgumentType.getPlayerName(ctx, "username"), -1L))
                        )
                  ))
               .then(
                  literal("remove")
                     .then(
                        argument("username", PlayerNameArgumentType.playerName(() -> Homovore2Client.friends().getFriends()))
                           .executes(ctx -> this.remove(PlayerNameArgumentType.getPlayerName(ctx, "username")))
                     )
               ))
            .then(
               literal("radius")
                  .then(
                     literal("add")
                        .then(
                           argument("blocks", IntegerArgumentType.integer(1))
                              .executes(ctx -> this.addRadius(IntegerArgumentType.getInteger(ctx, "blocks"), -1L))
                        )
                  )
            ))
         .then(
            literal("temp")
               .then(
                  ((RequiredArgumentBuilder)argument("days", IntegerArgumentType.integer(1))
                        .then(
                           literal("add")
                              .then(
                                 argument("username", PlayerNameArgumentType.onlinePlayer())
                                    .executes(
                                       ctx -> this.addPlayer(
                                          PlayerNameArgumentType.getPlayerName(ctx, "username"), expiryFromDays(IntegerArgumentType.getInteger(ctx, "days"))
                                       )
                                    )
                              )
                        ))
                     .then(
                        literal("radius")
                           .then(
                              literal("add")
                                 .then(
                                    argument("blocks", IntegerArgumentType.integer(1))
                                       .executes(
                                          ctx -> this.addRadius(
                                             IntegerArgumentType.getInteger(ctx, "blocks"), expiryFromDays(IntegerArgumentType.getInteger(ctx, "days"))
                                          )
                                       )
                                 )
                           )
                     )
               )
         );
   }

   private int list() {
      FriendManager friends = Homovore2Client.friends();
      List<String> names = friends.getFriends();
      if (names.isEmpty()) {
         return this.success("You have no friends :(");
      }

      StringJoiner joiner = new StringJoiner(", ");

      for (String name : names) {
         long expiry = friends.getExpiry(name);
         joiner.add(expiry == -1L ? name : name + " (" + formatRemaining(expiry) + ")");
      }

      return this.success("Friends (%d): {green}%s", names.size(), joiner);
   }

   private int remove(String username) {
      return !Homovore2Client.friends().removeFriend(username)
         ? this.fail("%s is not on your friends list.", username)
         : this.success("Removed {green}%s{reset} from your friends list", username);
   }

   private int addPlayer(String username, long expiry) {
      FriendManager friends = Homovore2Client.friends();
      if (friends.isFriend(username) && friends.getExpiry(username) == expiry) {
         return this.success("{green}%s{reset} is already on your friends list.", username);
      }

      friends.addFriend(username, expiry);
      return expiry == -1L
         ? this.success("Added {green}%s{reset} to your friends list", username)
         : this.success("Added {green}%s{reset} to your friends list for %s", username, formatRemaining(expiry));
   }

   private int addRadius(int blocks, long expiry) {
      if (!inGame()) {
         return this.fail("You need to be in a world to do that.");
      }

      FriendManager friends = Homovore2Client.friends();
      double radiusSq = (double)blocks * blocks;
      int added = 0;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player && !(mc.player.squaredDistanceTo(player) > radiusSq)) {
            String name = player.getGameProfile().name();
            if (name != null && !friends.isFriend(name)) {
               friends.addFriend(name, expiry);
               added++;
            }
         }
      }

      if (added == 0) {
         return this.success("No new players within %d blocks.", blocks);
      } else {
         return expiry == -1L
            ? this.success("Added {green}%d{reset} player(s) within %d blocks to your friends list", added, blocks)
            : this.success("Added {green}%d{reset} player(s) within %d blocks to your friends list for %s", added, blocks, formatRemaining(expiry));
      }
   }

   private static long expiryFromDays(int days) {
      return System.currentTimeMillis() + days * 86400000L;
   }

   private static String formatRemaining(long expiry) {
      long ms = Math.max(0L, expiry - System.currentTimeMillis());
      long totalHours = ms / 3600000L;
      long days = totalHours / 24L;
      long hours = totalHours % 24L;
      if (days > 0L) {
         return days + "d " + hours + "h";
      }

      long minutes = ms / 60000L % 60L;
      return hours > 0L ? hours + "h " + minutes + "m" : minutes + "m";
   }
}
