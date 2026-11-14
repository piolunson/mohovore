package dev.leonetic.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.commands.argument.ModuleArgumentType;
import dev.leonetic.features.modules.Module;
import dev.leonetic.manager.CommandManager;

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
