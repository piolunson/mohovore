package dev.leonetic.v2.feature.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.v2.feature.command.Command;
import dev.leonetic.v2.feature.command.CommandManager;
import dev.leonetic.v2.feature.command.argument.ModuleArgumentType;
import dev.leonetic.v2.feature.module.Module;

public class ToggleCommand extends Command {
   public ToggleCommand() {
      super("toggle", "t");
      this.setDescription("Toggles a module on or off");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(
         argument("module", ModuleArgumentType.module())
            .executes(
               ctx -> {
                  Module module = ModuleArgumentType.getModule(ctx, "module");
                  module.toggle();
                  return this.success(
                     "{gray}%s {reset}is now %s%s",
                     module.getDisplayName(),
                     module.isEnabled() ? "{green}" : "{red}",
                     module.isEnabled() ? "enabled" : "disabled"
                  );
               }
            )
      );
   }
}
