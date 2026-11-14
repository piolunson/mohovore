package dev.leonetic.features.commands.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.leonetic.Homovore;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.commands.argument.FriendArgumentType;
import dev.leonetic.features.commands.argument.OnlinePlayerArgumentType;
import dev.leonetic.manager.CommandManager;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
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
                        literal("list").executes(ctx -> {
                           List<String> friends = Homovore.friendManager.getFriends();
                           if (friends.isEmpty()) {
                              return this.success("You have no friends :(");
                           }

                           StringJoiner joiner = new StringJoiner(", ");

                           for (String name : friends) {
                              long expiry = Homovore.friendManager.getExpiry(name);
                              if (expiry == -1L) {
                                 joiner.add(name);
                              } else {
                                 joiner.add(name + " (" + formatRemaining(expiry) + ")");
                              }
                           }

                           return this.success("Friends (%s): %s", friends.size(), joiner);
                        })
                     ))
                     .then(literal("clear").executes(ctx -> {
                        Homovore.friendManager.clearFriends();
                        return this.success("Cleared friends list");
                     })))
                  .then(
                     literal("add")
                        .then(
                           argument("username", OnlinePlayerArgumentType.onlinePlayer())
                              .executes(ctx -> this.addPlayer(OnlinePlayerArgumentType.getOnlinePlayer(ctx, "username"), -1L))
                        )
                  ))
               .then(literal("remove").then(argument("username", FriendArgumentType.friend()).executes(ctx -> {
                  String username = FriendArgumentType.getFriend(ctx, "username");
                  if (!Homovore.friendManager.isFriend(username)) {
                     return this.success("{green} %s {reset} is not on your friends list.", username);
                  }

                  Homovore.friendManager.removeFriend(username);
                  return this.success("Removed {green} %s {reset} from your friends list", username);
               }))))
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
                                 argument("username", OnlinePlayerArgumentType.onlinePlayer())
                                    .executes(
                                       ctx -> this.addPlayer(
                                          OnlinePlayerArgumentType.getOnlinePlayer(ctx, "username"),
                                          expiryFromDays(IntegerArgumentType.getInteger(ctx, "days"))
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

   private int addPlayer(String username, long expiry) {
      if (Homovore.friendManager.isFriend(username)) {
         return this.success("{green} %s {reset} is already on your friends list.", username);
      }

      Homovore.friendManager.addFriend(username, expiry);
      return expiry == -1L
         ? this.success("Added {green} %s {reset} to your friends list", username)
         : this.success("Added {green} %s {reset} to your friends list for %s", username, formatRemaining(expiry));
   }

   private int addRadius(int blocks, long expiry) {
      if (nullCheck()) {
         return this.fail("You need to be in a world to do that.");
      }

      double radiusSq = (double)blocks * blocks;
      UUID self = mc.player.getUuid();
      int added = 0;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (!player.getUuid().equals(self) && !(mc.player.squaredDistanceTo(player) > radiusSq)) {
            String name = player.getGameProfile().name();
            if (name != null && !Homovore.friendManager.isFriend(name)) {
               Homovore.friendManager.addFriend(name, expiry);
               added++;
            }
         }
      }

      if (added == 0) {
         return this.success("No new players within %s blocks.", blocks);
      } else {
         return expiry == -1L
            ? this.success("Added %s player(s) within %s blocks to your friends list", added, blocks)
            : this.success("Added %s player(s) within %s blocks to your friends list for %s", added, blocks, formatRemaining(expiry));
      }
   }

   private static long expiryFromDays(int days) {
      return System.currentTimeMillis() + days * 86400000L;
   }

   private static String formatRemaining(long expiry) {
      long ms = Math.max(0L, expiry - System.currentTimeMillis());
      long hours = ms / 3600000L;
      long days = hours / 24L;
      hours %= 24L;
      if (days > 0L) {
         return days + "d " + hours + "h";
      }

      long minutes = ms / 60000L % 60L;
      return hours > 0L ? hours + "h " + minutes + "m" : minutes + "m";
   }
}
