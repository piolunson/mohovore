package dev.piolunson.features.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.Homovore;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.modules.player.InstantRekitModule;
import dev.piolunson.manager.CommandManager;

public class LoadKitCommand extends Command {
   public LoadKitCommand() {
      super("loadkit");
      this.setDescription("Selects which saved kit InstantRekit will apply");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> this.load("default"));
      builder.then(argument("name", StringArgumentType.word()).executes(ctx -> this.load(StringArgumentType.getString(ctx, "name"))));
   }

   private int load(String name) {
      InstantRekitModule module = Homovore.moduleManager.getModuleByClass(InstantRekitModule.class);
      if (module == null) {
         return this.fail("InstantRekit module is not registered.");
      } else if (!module.loadKit(name)) {
         return module.listKits().isEmpty()
            ? this.fail("No kit named %s. No kits saved yet — use .savekit <name>.", name)
            : this.fail("No kit named %s. Saved kits: %s", name, String.join(", ", module.listKits()));
      } else {
         return this.success("Loaded kit {green} %s {reset}. Open a container to apply it.", name);
      }
   }
}
