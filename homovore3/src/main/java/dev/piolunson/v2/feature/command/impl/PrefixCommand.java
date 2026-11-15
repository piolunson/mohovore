package dev.piolunson.v2.feature.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.v2.feature.command.Command;
import dev.piolunson.v2.feature.command.CommandManager;

public class PrefixCommand extends Command {
   public PrefixCommand() {
      super("prefix", "setprefix");
      this.setDescription("Changes the character every command starts with");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)builder.executes(ctx -> this.success("The prefix is {green}%s", ((CommandManager)ctx.getSource()).getPrefix())))
         .then(argument("prefix", StringArgumentType.word()).executes(ctx -> {
            String prefix = StringArgumentType.getString(ctx, "prefix");
            if (prefix.isBlank()) {
               return this.fail("The prefix cannot be blank");
            }

            if (prefix.startsWith("/")) {
               return this.fail("The prefix cannot start with /");
            }

            ((CommandManager)ctx.getSource()).setPrefix(prefix);
            return this.success("The prefix is now {green}%s", prefix);
         }));
   }
}
