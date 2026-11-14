package dev.leonetic.v2.feature.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.leonetic.v2.feature.command.Command;
import dev.leonetic.v2.feature.command.CommandManager;
import dev.leonetic.v2.feature.command.argument.EnumArgumentType;
import dev.leonetic.v2.feature.command.argument.KeybindArgumentType;
import dev.leonetic.v2.feature.command.argument.ModuleArgumentType;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Bind;
import java.util.Locale;

public class BindCommand extends Command {
   public BindCommand() {
      super("bind", "setbind");
      this.setDescription("Binds a module to a key; bind it to NONE to clear it. Use \"bind <module> mode <toggle|hold>\" to pick how the bind acts");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(
         ((RequiredArgumentBuilder)((RequiredArgumentBuilder)argument("module", ModuleArgumentType.module())
                  .executes(
                     ctx -> {
                        Module module = ModuleArgumentType.getModule(ctx, "module");
                        return this.success(
                           "{gray}%s {reset}is bound to {green}%s {reset}in {green}%s {reset}mode",
                           module.getDisplayName(),
                           module.keybind.getValue(),
                           module.getBindMode().name().toLowerCase(Locale.ROOT)
                        );
                     }
                  ))
               .then(
                  argument("key", KeybindArgumentType.keybind())
                     .executes(
                        ctx -> {
                           Module module = ModuleArgumentType.getModule(ctx, "module");
                           Bind bind = KeybindArgumentType.getBind(ctx, "key");
                           module.keybind.setValue(bind);
                           return bind.isEmpty()
                              ? this.success("{gray}%s {reset}is no longer bound", module.getDisplayName())
                              : this.success("{gray}%s {reset}is now bound to {green}%s", module.getDisplayName(), bind);
                        }
                     )
               ))
            .then(((LiteralArgumentBuilder)literal("mode").executes(ctx -> {
               Module module = ModuleArgumentType.getModule(ctx, "module");
               return this.success("{gray}%s {reset}uses {green}%s {reset}mode", module.getDisplayName(), module.getBindMode().name().toLowerCase(Locale.ROOT));
            })).then(argument("value", EnumArgumentType.enumeration(Module.BindMode.class)).executes(ctx -> {
               Module module = ModuleArgumentType.getModule(ctx, "module");
               Module.BindMode mode = EnumArgumentType.getEnum(ctx, "value");
               module.bindMode.setValue(mode);
               return this.success("{gray}%s {reset}now uses {green}%s {reset}mode", module.getDisplayName(), mode.name().toLowerCase(Locale.ROOT));
            })))
      );
   }
}
