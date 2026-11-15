package dev.piolunson.features.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.piolunson.Homovore;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.modules.hud.ActiveModulesHudModule;
import dev.piolunson.manager.CommandManager;
import java.util.List;
import java.util.StringJoiner;

public class ActiveModulesCommand extends Command {
   public ActiveModulesCommand() {
      super("activemodules", "am");
      this.setDescription("Manages the ActiveModules HUD list");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(
                  literal("add")
                     .then(
                        argument("module", StringArgumentType.word())
                           .suggests(addSuggestions())
                           .executes(
                              ctx -> {
                                 String name = StringArgumentType.getString(ctx, "module");
                                 Module module = Homovore.moduleManager.getModuleByName(name);
                                 if (module == null) {
                                    return this.fail("No module named {green} %s", name);
                                 } else {
                                    ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
                                    if (h == null) {
                                       return this.fail("ActiveModules HUD element not found");
                                    } else {
                                       return !h.add(name)
                                          ? this.success("{green} %s {reset} is already in the list", module.getDisplayName())
                                          : this.success("Added {green} %s {reset} to ActiveModules", module.getDisplayName());
                                    }
                                 }
                              }
                           )
                     )
               ))
               .then(
                  literal("remove")
                     .then(
                        argument("module", StringArgumentType.word())
                           .suggests(removeSuggestions())
                           .executes(
                              ctx -> {
                                 String name = StringArgumentType.getString(ctx, "module");
                                 ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
                                 if (h == null) {
                                    return this.fail("ActiveModules HUD element not found");
                                 } else {
                                    return !h.remove(name)
                                       ? this.success("{green} %s {reset} is not in the list", name)
                                       : this.success("Removed {green} %s {reset} from ActiveModules", name);
                                 }
                              }
                           )
                     )
               ))
            .then(literal("clear").executes(ctx -> {
               ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
               if (h == null) {
                  return this.fail("ActiveModules HUD element not found");
               }

               h.clear();
               return this.success("Cleared ActiveModules list");
            })))
         .then(literal("list").executes(ctx -> {
            ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
            if (h == null) {
               return this.fail("ActiveModules HUD element not found");
            }

            List<String> entries = h.getEntries();
            if (entries.isEmpty()) {
               return this.success("ActiveModules list is empty");
            }

            StringJoiner joiner = new StringJoiner(", ");
            entries.forEach(joiner::add);
            return this.success("ActiveModules (%s): %s", entries.size(), joiner);
         }));
   }

   private static SuggestionProvider<CommandManager> addSuggestions() {
      return (ctx, builder) -> {
         String input = builder.getRemainingLowerCase();
         ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
         List<String> entries = h != null ? h.getEntries() : List.of();

         for (Module module : Homovore.moduleManager.getModules()) {
            String name = module.getName();
            if (!entries.stream().anyMatch(e -> e.equalsIgnoreCase(name))
               && (name.toLowerCase().contains(input) || module.getDisplayName().toLowerCase().contains(input))) {
               builder.suggest(name);
            }
         }

         return builder.buildFuture();
      };
   }

   private static SuggestionProvider<CommandManager> removeSuggestions() {
      return (ctx, builder) -> {
         String input = builder.getRemainingLowerCase();
         ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
         if (h == null) {
            return builder.buildFuture();
         }

         for (String entry : h.getEntries()) {
            if (entry.toLowerCase().contains(input)) {
               builder.suggest(entry);
            }
         }

         return builder.buildFuture();
      };
   }
}
