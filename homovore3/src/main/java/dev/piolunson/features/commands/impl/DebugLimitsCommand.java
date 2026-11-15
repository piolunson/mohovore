package dev.piolunson.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.debug.MoveLimitDebug;
import dev.piolunson.manager.CommandManager;

public class DebugLimitsCommand extends Command {
   public DebugLimitsCommand() {
      super("debuglimits");
      this.setDescription("Toggles the move-rate debug overlay");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> {
         MoveLimitDebug debug = MoveLimitDebug.get();
         debug.toggle();
         return this.success("Move-rate debug overlay is now %s", debug.isEnabled() ? "{green} enabled" : "{red} disabled");
      });
   }
}
