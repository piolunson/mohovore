package dev.piolunson.v2.feature.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.command.impl.BindCommand;
import dev.piolunson.v2.feature.command.impl.ConfigCommand;
import dev.piolunson.v2.feature.command.impl.EnemyCommand;
import dev.piolunson.v2.feature.command.impl.FakePlayerCommand;
import dev.piolunson.v2.feature.command.impl.FriendCommand;
import dev.piolunson.v2.feature.command.impl.HelpCommand;
import dev.piolunson.v2.feature.command.impl.LoadKitCommand;
import dev.piolunson.v2.feature.command.impl.PrefixCommand;
import dev.piolunson.v2.feature.command.impl.SaveKitCommand;
import dev.piolunson.v2.feature.command.impl.ToggleCommand;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.module.impl.ClickGuiModule;
import dev.piolunson.v2.util.ChatUtil;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommandManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("Commands");
   public static final String DEFAULT_PREFIX = ".";
   private final CommandDispatcher<CommandManager> dispatcher = new CommandDispatcher();
   private final Map<String, Command> byAlias = new LinkedHashMap<>();
   private final List<Command> commands = new LinkedList<>();

   public CommandManager() {
      this.register(new BindCommand());
      this.register(new ConfigCommand());
      this.register(new EnemyCommand());
      this.register(new FakePlayerCommand());
      this.register(new FriendCommand());
      this.register(new HelpCommand());
      this.register(new LoadKitCommand());
      this.register(new PrefixCommand());
      this.register(new SaveKitCommand());
      this.register(new ToggleCommand());

      for (Module module : Homovore2Client.modules().getModules()) {
         this.register(new ModuleCommand(module));
      }

      LOGGER.info("Registered {} commands", this.commands.size());
   }

   public void register(Command command) {
      this.commands.add(command);

      for (String alias : command.getAliases()) {
         String key = alias.toLowerCase();
         Command taken = this.byAlias.putIfAbsent(key, command);
         if (taken != null) {
            LOGGER.warn("Alias '{}' is already taken by {}; skipping it for {}", new Object[]{key, taken.getName(), command.getName()});
         } else {
            LiteralArgumentBuilder<CommandManager> builder = Command.literal(key);
            command.createArgumentBuilder(builder);
            this.dispatcher.register(builder);
         }
      }
   }

   public boolean isCommand(String message) {
      String prefix = this.getPrefix();
      return message.length() > prefix.length() && message.startsWith(prefix);
   }

   public void dispatch(String message) {
      String input = message.substring(this.getPrefix().length()).trim();
      if (!input.isEmpty()) {
         try {
            if (this.dispatcher.execute(input, this) == 0) {
               LOGGER.debug("Command '{}' reported failure", input);
            }
         } catch (CommandSyntaxException e) {
            ChatUtil.send("{red}%s", e.getMessage());
         } catch (Throwable t) {
            LOGGER.error("Failed to execute command '{}'", input, t);
            ChatUtil.send("{red}Failed to execute command: %s", String.valueOf(t.getMessage()));
         }
      }
   }

   public String getPrefix() {
      ClickGuiModule gui = Homovore2Client.modules() == null ? null : Homovore2Client.modules().get(ClickGuiModule.class);
      if (gui == null) {
         return ".";
      }

      String prefix = gui.prefix.getValue();
      return prefix != null && !prefix.isEmpty() ? prefix : ".";
   }

   public void setPrefix(String prefix) {
      ClickGuiModule gui = Homovore2Client.modules().get(ClickGuiModule.class);
      if (gui != null) {
         gui.prefix.setValue(prefix);
      }
   }

   public Command getCommand(String alias) {
      return this.byAlias.get(alias.toLowerCase());
   }

   public Set<String> getAliases() {
      return this.byAlias.keySet();
   }

   public List<Command> getCommands() {
      return this.commands;
   }

   public CommandDispatcher<CommandManager> getDispatcher() {
      return this.dispatcher;
   }
}
