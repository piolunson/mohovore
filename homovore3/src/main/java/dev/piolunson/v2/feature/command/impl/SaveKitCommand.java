package dev.piolunson.v2.feature.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.command.Command;
import dev.piolunson.v2.feature.command.CommandManager;
import dev.piolunson.v2.feature.module.impl.InstantRekitModule;

public final class SaveKitCommand extends Command {
   public SaveKitCommand() {
      super("savekit");
      this.setDescription("Saves your current inventory as a named Instant Rekit kit");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)builder.executes(ctx -> this.save("default")))
         .then(argument("name", StringArgumentType.greedyString()).executes(ctx -> this.save(StringArgumentType.getString(ctx, "name"))));
   }

   private int save(String name) {
      InstantRekitModule module = Homovore2Client.modules().get(InstantRekitModule.class);
      if (module == null) {
         return this.fail("Instant Rekit is not registered.");
      }

      int slots = module.saveKit(name);
      if (slots == 0) {
         return this.fail("Enter a name and join a world with items in your inventory.");
      }

      Homovore2Client.config().save();
      return this.success("Saved kit {green}%s{reset} with {green}%d{reset} occupied slot(s).", module.selectedKitName(), slots);
   }
}
