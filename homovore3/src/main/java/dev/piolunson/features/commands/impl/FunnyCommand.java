package dev.piolunson.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.Homovore;
import dev.piolunson.features.Feature;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.gui.HomovoreGui;
import dev.piolunson.features.modules.Module;
import dev.piolunson.manager.CommandManager;

public class FunnyCommand extends Command {
   public FunnyCommand() {
      super("funny");
      this.setDescription("Toggles visibility of the Funny category in the ClickGui");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> {
         CommandManager cm = (CommandManager)ctx.getSource();
         boolean nowVisible = !cm.isFunnyVisible();
         cm.setFunnyVisible(nowVisible);
         if (!nowVisible) {
            Homovore.moduleManager.stream().filter(m -> m.getCategory() == Module.Category.FUNNY).filter(Feature::isEnabled).forEach(Module::disable);
         }

         HomovoreGui.getInstance().reload();
         return this.success("Funny category is now %s", nowVisible ? "{green} visible" : "{red} hidden");
      });
   }
}
