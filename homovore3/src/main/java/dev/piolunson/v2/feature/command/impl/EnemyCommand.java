package dev.piolunson.v2.feature.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.command.Command;
import dev.piolunson.v2.feature.command.CommandManager;
import dev.piolunson.v2.feature.command.argument.PlayerNameArgumentType;
import dev.piolunson.v2.manager.EnemyManager;
import java.util.List;

public class EnemyCommand extends Command {
   public EnemyCommand() {
      super("enemy", "enemies", "e");
      this.setDescription("Manages your enemies list");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(literal("list").executes(ctx -> {
               List<String> names = Homovore2Client.enemies().getEnemies();
               return names.isEmpty() ? this.success("You have no enemies :)") : this.success("Enemies (%d): {red}%s", names.size(), String.join(", ", names));
            }))).then(literal("clear").executes(ctx -> {
               Homovore2Client.enemies().clearEnemies();
               return this.success("Cleared enemies list");
            })))
            .then(
               literal("add")
                  .then(
                     argument("username", PlayerNameArgumentType.onlinePlayer())
                        .executes(ctx -> this.add(PlayerNameArgumentType.getPlayerName(ctx, "username")))
                  )
            ))
         .then(
            literal("remove")
               .then(
                  argument("username", PlayerNameArgumentType.playerName(() -> Homovore2Client.enemies().getEnemies()))
                     .executes(ctx -> this.remove(PlayerNameArgumentType.getPlayerName(ctx, "username")))
               )
         );
   }

   private int add(String username) {
      EnemyManager enemies = Homovore2Client.enemies();
      if (enemies.isEnemy(username)) {
         return this.success("{red}%s{reset} is already on your enemies list.", username);
      }

      enemies.addEnemy(username);
      return this.success("Added {red}%s{reset} to your enemies list", username);
   }

   private int remove(String username) {
      return !Homovore2Client.enemies().removeEnemy(username)
         ? this.fail("%s is not on your enemies list.", username)
         : this.success("Removed {red}%s{reset} from your enemies list", username);
   }
}
