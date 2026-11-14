package dev.leonetic.features.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.Homovore;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.modules.player.InstantRekitModule;
import dev.leonetic.manager.CommandManager;

public class SaveKitCommand extends Command {
   public SaveKitCommand() {
      super("savekit");
      this.setDescription("Snapshots your current inventory as a named InstantRekit kit");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> this.save("default"));
      builder.then(argument("name", StringArgumentType.word()).executes(ctx -> this.save(StringArgumentType.getString(ctx, "name"))));
   }

   private int save(String name) {
      InstantRekitModule module = Homovore.moduleManager.getModuleByClass(InstantRekitModule.class);
      if (module == null) {
         return this.fail("InstantRekit module is not registered.");
      }

      int saved = module.saveKit(name);
      if (saved == 0) {
         return this.fail("Inventory is empty — nothing to save.");
      }

      Homovore.configManager.save();
      return this.success("Saved kit {green} %s {reset} with {green} %s {reset} item slot(s).", name, saved);
   }
}
