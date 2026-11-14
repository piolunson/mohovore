package dev.leonetic.feature.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.command.ArgumentSuggestions;
import dev.leonetic.feature.command.Command;
import dev.leonetic.feature.command.CommandManager;
import dev.leonetic.feature.module.impl.InstantRekitModule;

public final class LoadKitCommand extends Command {
   public LoadKitCommand() {
      super("loadkit");
      this.setDescription("Selects the saved kit Instant Rekit will apply");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)builder.executes(ctx -> this.load("default")))
         .then(
            argument("name", StringArgumentType.greedyString())
               .suggests((ctx, suggestions) -> ArgumentSuggestions.suggest(module().listKits(), suggestions))
               .executes(ctx -> this.load(StringArgumentType.getString(ctx, "name")))
         );
   }

   private int load(String name) {
      InstantRekitModule module = module();
      if (module == null) {
         return this.fail("Instant Rekit is not registered.");
      }

      if (!module.selectKit(name)) {
         return module.listKits().isEmpty()
            ? this.fail("No kits are saved yet. Use .savekit <name>.")
            : this.fail("No kit named %s. Saved kits: %s", name, String.join(", ", module.listKits()));
      }

      Homovore2Client.config().save();
      return this.success("Selected kit {green}%s{reset}. Open a container to apply it.", module.selectedKitName());
   }

   private static InstantRekitModule module() {
      return Homovore2Client.modules().get(InstantRekitModule.class);
   }
}
