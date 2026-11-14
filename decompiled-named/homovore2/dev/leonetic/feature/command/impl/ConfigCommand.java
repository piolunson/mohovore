package dev.leonetic.feature.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.command.Command;
import dev.leonetic.feature.command.CommandManager;
import dev.leonetic.util.ChatUtil;
import java.util.List;

public class ConfigCommand extends Command {
   public ConfigCommand() {
      super("config", "cfg");
      this.setDescription("Saves, reloads, exports or imports the configuration");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(literal("save").executes(ctx -> {
               Homovore2Client.config().save();
               return this.success("Configuration saved");
            }))).then(literal("load").executes(ctx -> {
               Homovore2Client.config().load();
               return this.success("Configuration reloaded");
            })))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal("export").executes(ctx -> exportToClipboard(false) ? this.success() : this.fail()))
                     .then(literal("all").executes(ctx -> exportToClipboard(true) ? this.success() : this.fail())))
                  .then(literal("functional").executes(ctx -> exportFunctionalToClipboard() ? this.success() : this.fail()))
            ))
         .then(
            ((LiteralArgumentBuilder)literal("import").executes(ctx -> importCode(mc.keyboard.getClipboard()) ? this.success() : this.fail()))
               .then(
                  argument("code", StringArgumentType.greedyString())
                     .executes(ctx -> importCode(StringArgumentType.getString(ctx, "code")) ? this.success() : this.fail())
               )
         );
   }

   public static boolean exportToClipboard(boolean includePersonal) {
      try {
         String code = Homovore2Client.config().exportCode(includePersonal);
         mc.keyboard.setClipboard(code);
         ChatUtil.send("Copied config code to clipboard {gray}(%d characters)", code.length());
         return true;
      } catch (RuntimeException e) {
         ChatUtil.send("{red}Failed to export config: %s", String.valueOf(e.getMessage()));
         return false;
      }
   }

   public static boolean exportFunctionalToClipboard() {
      try {
         String code = Homovore2Client.config().exportFunctionalCode();
         mc.keyboard.setClipboard(code);
         ChatUtil.send("Copied functional config code to clipboard {gray}(%d characters, no renders or keybinds)", code.length());
         return true;
      } catch (RuntimeException e) {
         ChatUtil.send("{red}Failed to export config: %s", String.valueOf(e.getMessage()));
         return false;
      }
   }

   public static boolean importCode(String code) {
      List<String> applied;
      try {
         applied = Homovore2Client.config().importCode(code);
      } catch (IllegalArgumentException e) {
         ChatUtil.send("{red}%s", e.getMessage());
         return false;
      }

      if (applied.isEmpty()) {
         ChatUtil.send("{red}Config code has nothing this client can load");
         return false;
      } else {
         ChatUtil.send("Imported config {gray}(%s)", String.join(", ", applied));
         return true;
      }
   }
}
