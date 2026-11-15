package dev.piolunson.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.commands.argument.ModuleArgumentType;
import dev.piolunson.features.modules.Module;
import dev.piolunson.manager.CommandManager;

public class ToggleCommand extends Command {
   public ToggleCommand() {
      super("toggle", "t");
      this.setDescription("Toggles a module");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(argument("module", ModuleArgumentType.module(true)).executes(ctx -> {
         Module module = ModuleArgumentType.getModule(ctx, "module");
         module.toggle();
         boolean toggled = module.isEnabled();
         return this.success("{gray} %s {reset} is now %s %s", module.getDisplayName(), toggled ? "{green}" : "{red}", toggled ? "enabled" : "disabled");
      }));
   }
}
