package dev.leonetic.feature.command.impl;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import dev.leonetic.feature.command.Command;
import dev.leonetic.feature.command.CommandManager;
import dev.leonetic.feature.command.argument.CommandArgumentType;
import dev.leonetic.feature.command.argument.NumberArgumentType;
import dev.leonetic.util.ChatUtil;
import dev.leonetic.util.TextUtil;
import java.util.List;
import java.util.Map;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_5250;
import net.minecraft.class_2558.class_10610;

public class HelpCommand extends Command {
   private static final int PER_PAGE = 5;

   public HelpCommand() {
      super("help", "commands", "cmds", "h");
      this.setDescription("Lists every command, or the usages of one");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.executes(ctx -> this.listCommands((CommandManager)ctx.getSource(), 1)))
            .then(
               argument("page", NumberArgumentType.number(Integer.class))
                  .executes(ctx -> this.listCommands((CommandManager)ctx.getSource(), NumberArgumentType.get(Integer.class, ctx, "page")))
            ))
         .then(argument("command", CommandArgumentType.command()).executes(this::describe));
   }

   private int describe(CommandContext<CommandManager> ctx) {
      CommandManager manager = (CommandManager)ctx.getSource();
      Command command = CommandArgumentType.getCommand(ctx, "command");
      class_5250 text = ChatUtil.withPrefix("Usages for {green}%s{reset}:", command.getName());
      text.method_10852(class_2561.method_43470("\n  " + command.getDescription()).method_27692(class_124.field_1063));
      ParseResults<CommandManager> parse = manager.getDispatcher().parse(command.getName(), manager);
      List<? extends ParsedCommandNode<CommandManager>> nodes = parse.getContext().getNodes();
      if (nodes.isEmpty()) {
         text.method_10852(class_2561.method_43470("\n  No usages available").method_27692(class_124.field_1063));
      } else {
         CommandNode<CommandManager> node = ((ParsedCommandNode)Iterables.getLast(nodes)).getNode();
         Map<CommandNode<CommandManager>, String> usages = manager.getDispatcher().getSmartUsage(node, manager);
         if (usages.isEmpty()) {
            text.method_10852(usage(manager, command.getName(), ""));
         } else {
            for (String usage : usages.values()) {
               text.method_10852(usage(manager, command.getName(), usage));
            }
         }
      }

      ChatUtil.raw(text);
      return this.success();
   }

   private int listCommands(CommandManager manager, int page) {
      List<Command> commands = manager.getCommands().stream().filter(Command::isShown).toList();
      int pages = Math.max(1, (int)Math.ceil(commands.size() / 5.0));
      int index = Math.clamp(page, 1, pages);
      List<Command> shown = commands.subList((index - 1) * 5, Math.min(commands.size(), index * 5));
      class_5250 text = ChatUtil.withPrefix("Commands ({green}%d{reset}):", commands.size());

      for (Command command : shown) {
         text.method_10852(class_2561.method_43470("\n  " + String.join(", ", command.getAliases())).method_27692(class_124.field_1080));
         text.method_10852(class_2561.method_43470("\n    " + command.getDescription()).method_27692(class_124.field_1063));
      }

      text.method_10852(class_2561.method_43470("\n"));
      text.method_10852(footer(manager, index, pages));
      ChatUtil.raw(text);
      return this.success();
   }

   private static class_2561 usage(CommandManager manager, String name, String usage) {
      String line = (manager.getPrefix() + name + " " + usage).stripTrailing();
      return class_2561.method_43470("\n  " + line).method_27692(class_124.field_1080).method_27694(style -> style.method_10958(new class_10610(line)));
   }

   private static class_2561 footer(CommandManager manager, int index, int pages) {
      String base = manager.getPrefix() + "help ";
      class_5250 text = ChatUtil.prefix().method_27693(" ");
      if (index > 1) {
         text.method_10852(arrow(" < ", base + (index - 1)));
      }

      text.method_10852(TextUtil.text("{gray}Page {reset}%d{gray}/{reset}%d", index, pages));
      if (index < pages) {
         text.method_10852(arrow(" > ", base + (index + 1)));
      }

      return text;
   }

   private static class_2561 arrow(String label, String command) {
      return class_2561.method_43470(label).method_27692(class_124.field_1080).method_27694(style -> style.method_10958(new class_10610(command)));
   }
}
