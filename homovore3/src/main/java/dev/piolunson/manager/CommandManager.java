package dev.piolunson.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.piolunson.Homovore;
import dev.piolunson.features.Feature;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.commands.impl.ActiveModulesCommand;
import dev.piolunson.features.commands.impl.BindCommand;
import dev.piolunson.features.commands.impl.DebugLimitsCommand;
import dev.piolunson.features.commands.impl.EnemyCommand;
import dev.piolunson.features.commands.impl.FakePlayerCommand;
import dev.piolunson.features.commands.impl.FriendCommand;
import dev.piolunson.features.commands.impl.FunnyCommand;
import dev.piolunson.features.commands.impl.HelpCommand;
import dev.piolunson.features.commands.impl.LoadKitCommand;
import dev.piolunson.features.commands.impl.PrefixCommand;
import dev.piolunson.features.commands.impl.SaveKitCommand;
import dev.piolunson.features.commands.impl.ToggleCommand;
import dev.piolunson.features.debug.MoveLimitDebug;
import dev.piolunson.util.traits.Jsonable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandManager extends Feature implements Jsonable {
   private static final Logger LOGGER = LogManager.getLogger("Commands");
   private final CommandDispatcher<CommandManager> dispatcher = new CommandDispatcher();
   private final Map<String, Command> commandAliasMap = new LinkedHashMap<>();
   private final List<Command> commandList = new LinkedList<>();
   private String commandPrefix = ".";
   private boolean funnyVisible = false;

   public CommandManager() {
      super("Commands");
   }

   public void init() {
      this.register(new BindCommand());
      this.register(new FriendCommand());
      this.register(new EnemyCommand());
      this.register(new HelpCommand());
      this.register(new PrefixCommand());
      this.register(new ToggleCommand());
      this.register(new ActiveModulesCommand());
      this.register(new FunnyCommand());
      this.register(new SaveKitCommand());
      this.register(new LoadKitCommand());
      this.register(new FakePlayerCommand());
      this.register(new DebugLimitsCommand());
      MoveLimitDebug.get();
      LOGGER.info("Registered {} commands", this.commandList.size());
      Homovore.configManager.addConfig(this);
   }

   public void onChatSent(String message) {
      try {
         int result = this.dispatcher.execute(message.substring(this.commandPrefix.length()).trim(), this);
         if (result == 1) {
            Command.sendMessage("{green} Command executed successfully");
         } else if (result == 0) {
            Command.sendMessage("{red} Failed to execute command");
         }
      } catch (CommandSyntaxException e) {
         LOGGER.error("Failed to execute command", e);
         Command.sendMessage("{red} %s", e.getMessage());
      }
   }

   public void register(Command command) {
      this.commandList.add(command);

      for (String alias : command.getAliases()) {
         this.commandAliasMap.put(alias, command);
         LiteralArgumentBuilder<CommandManager> builder = Command.literal(alias);
         command.createArgumentBuilder(builder);
         this.dispatcher.register(builder);
      }
   }

   public void setCommandPrefix(String commandPrefix) {
      this.commandPrefix = commandPrefix;
   }

   public String getCommandPrefix() {
      return this.commandPrefix;
   }

   public boolean isFunnyVisible() {
      return this.funnyVisible;
   }

   public void setFunnyVisible(boolean funnyVisible) {
      this.funnyVisible = funnyVisible;
   }

   public Command getCommand(String alias) {
      return this.commandAliasMap.get(alias);
   }

   public Set<String> getCommandAliases() {
      return this.commandAliasMap.keySet();
   }

   public List<Command> getCommands() {
      return this.commandList;
   }

   public CommandDispatcher<CommandManager> getDispatcher() {
      return this.dispatcher;
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();
      object.addProperty("prefix", this.commandPrefix);
      object.addProperty("funnyVisible", this.funnyVisible);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         if (object.has("prefix")) {
            this.setCommandPrefix(object.get("prefix").getAsString());
         }

         if (object.has("funnyVisible")) {
            this.setFunnyVisible(object.get("funnyVisible").getAsBoolean());
         }
      }
   }

   @Override
   public String getFileName() {
      return "commands.json";
   }
}
