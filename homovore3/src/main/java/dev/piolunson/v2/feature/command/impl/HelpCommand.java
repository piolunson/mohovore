package dev.piolunson.v2.feature.command.impl;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import dev.piolunson.v2.feature.command.Command;
import dev.piolunson.v2.feature.command.CommandManager;
import dev.piolunson.v2.feature.command.argument.CommandArgumentType;
import dev.piolunson.v2.feature.command.argument.NumberArgumentType;
import dev.piolunson.v2.util.ChatUtil;
import dev.piolunson.v2.util.TextUtil;
import java.util.List;
import java.util.Map;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent.SuggestCommand;
import net.minecraft.util.Formatting;

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
      MutableText text = ChatUtil.withPrefix("Usages for {green}%s{reset}:", command.getName());
      text.append(Text.literal("\n  " + command.getDescription()).formatted(Formatting.DARK_GRAY));
      ParseResults<CommandManager> parse = manager.getDispatcher().parse(command.getName(), manager);
      List<? extends ParsedCommandNode<CommandManager>> nodes = parse.getContext().getNodes();
      if (nodes.isEmpty()) {
         text.append(Text.literal("\n  No usages available").formatted(Formatting.DARK_GRAY));
      } else {
         CommandNode<CommandManager> node = ((ParsedCommandNode)Iterables.getLast(nodes)).getNode();
         Map<CommandNode<CommandManager>, String> usages = manager.getDispatcher().getSmartUsage(node, manager);
         if (usages.isEmpty()) {
            text.append(usage(manager, command.getName(), ""));
         } else {
            for (String usage : usages.values()) {
               text.append(usage(manager, command.getName(), usage));
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
      MutableText text = ChatUtil.withPrefix("Commands ({green}%d{reset}):", commands.size());

      for (Command command : shown) {
         text.append(Text.literal("\n  " + String.join(", ", command.getAliases())).formatted(Formatting.GRAY));
         text.append(Text.literal("\n    " + command.getDescription()).formatted(Formatting.DARK_GRAY));
      }

      text.append(Text.literal("\n"));
      text.append(footer(manager, index, pages));
      ChatUtil.raw(text);
      return this.success();
   }

   private static Text usage(CommandManager manager, String name, String usage) {
      String line = (manager.getPrefix() + name + " " + usage).stripTrailing();
      return Text.literal("\n  " + line).formatted(Formatting.GRAY).styled(style -> style.withClickEvent(new SuggestCommand(line)));
   }

   private static Text footer(CommandManager manager, int index, int pages) {
      String base = manager.getPrefix() + "help ";
      MutableText text = ChatUtil.prefix().append(" ");
      if (index > 1) {
         text.append(arrow(" < ", base + (index - 1)));
      }

      text.append(TextUtil.text("{gray}Page {reset}%d{gray}/{reset}%d", index, pages));
      if (index < pages) {
         text.append(arrow(" > ", base + (index + 1)));
      }

      return text;
   }

   private static Text arrow(String label, String command) {
      return Text.literal(label).formatted(Formatting.GRAY).styled(style -> style.withClickEvent(new SuggestCommand(command)));
   }
}
