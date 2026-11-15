package dev.piolunson.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.Homovore;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.commands.argument.EnemyArgumentType;
import dev.piolunson.features.commands.argument.OnlinePlayerArgumentType;
import dev.piolunson.manager.CommandManager;
import java.util.List;
import java.util.StringJoiner;

public class EnemyCommand extends Command {
   public EnemyCommand() {
      super("enemy", "enemies", "e");
      this.setDescription("Manages your enemies list");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(literal("list").executes(ctx -> {
         List<String> enemies = Homovore.enemyManager.getEnemies();
         if (enemies.isEmpty()) {
            return this.success("You have no enemies :)");
         }

         StringJoiner joiner = new StringJoiner(",");
         enemies.forEach(joiner::add);
         return this.success("Enemies (%s): %s", enemies.size(), joiner);
      }))).then(literal("clear").executes(ctx -> {
         Homovore.enemyManager.clearEnemies();
         return this.success("Cleared enemies list");
      }))).then(literal("add").then(argument("username", OnlinePlayerArgumentType.onlinePlayer()).executes(ctx -> {
         String username = OnlinePlayerArgumentType.getOnlinePlayer(ctx, "username");
         if (Homovore.enemyManager.isEnemy(username)) {
            return this.success("{red} %s {reset} is already on your enemies list.", username);
         }

         Homovore.enemyManager.addEnemy(username);
         return this.success("Added {red} %s {reset} to your enemies list", username);
      })))).then(literal("remove").then(argument("username", EnemyArgumentType.enemy()).executes(ctx -> {
         String username = EnemyArgumentType.getEnemy(ctx, "username");
         if (!Homovore.enemyManager.isEnemy(username)) {
            return this.success("{red} %s {reset} is not on your enemies list.", username);
         }

         Homovore.enemyManager.removeEnemy(username);
         return this.success("Removed {red} %s {reset} from your enemies list", username);
      })));
   }
}
