package dev.piolunson.v2.feature.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.piolunson.v2.util.ChatUtil;
import net.minecraft.client.MinecraftClient;

public abstract class Command {
   protected static final MinecraftClient mc = MinecraftClient.getInstance();
   public static final int SINGLE_SUCCESS = 1;
   public static final int SINGLE_FAILURE = 0;
   private final String[] aliases;
   private String description = "No description was provided for this command.";

   protected Command(String... aliases) {
      this.aliases = aliases;
   }

   public abstract void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> var1);

   protected int success(String message, Object... format) {
      ChatUtil.send(message, format);
      return 1;
   }

   protected int success() {
      return 1;
   }

   protected int fail(String message, Object... format) {
      ChatUtil.send("{red}" + message, format);
      return 0;
   }

   protected int fail() {
      return 0;
   }

   public String getName() {
      return this.aliases[0];
   }

   public String[] getAliases() {
      return this.aliases;
   }

   protected void setDescription(String description) {
      this.description = description;
   }

   public String getDescription() {
      return this.description;
   }

   public boolean isShown() {
      return true;
   }

   protected static boolean inGame() {
      return mc.player != null && mc.world != null;
   }

   public static LiteralArgumentBuilder<CommandManager> literal(String literal) {
      return LiteralArgumentBuilder.literal(literal);
   }

   public static <T> RequiredArgumentBuilder<CommandManager, T> argument(String name, ArgumentType<T> type) {
      return RequiredArgumentBuilder.argument(name, type);
   }
}
