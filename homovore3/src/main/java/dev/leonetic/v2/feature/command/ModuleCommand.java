package dev.leonetic.v2.feature.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.command.argument.ColorArgumentType;
import dev.leonetic.v2.feature.command.argument.EnumArgumentType;
import dev.leonetic.v2.feature.command.argument.KeybindArgumentType;
import dev.leonetic.v2.feature.command.argument.ListOptionArgumentType;
import dev.leonetic.v2.feature.command.argument.NumberArgumentType;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Bind;
import dev.leonetic.v2.feature.setting.ListSetting;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.util.ChatUtil;
import java.awt.Color;
import java.util.Locale;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent.SuggestCommand;
import net.minecraft.util.Formatting;

public class ModuleCommand extends Command {
   private final Module module;

   public ModuleCommand(Module module) {
      super(module.getName().toLowerCase(Locale.ROOT));
      this.module = module;
      this.setDescription("Configures " + module.getDisplayName() + " from chat");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> this.printState());

      for (Setting<?> setting : this.module.getSettings()) {
         this.registerSetting(builder, setting);
      }
   }

   private void registerSetting(LiteralArgumentBuilder<CommandManager> builder, Setting<?> setting) {
      if (setting instanceof ListSetting list) {
         builder.then(
            literal(name(setting))
               .then(argument("value", ListOptionArgumentType.option(list)).executes(ctx -> this.apply(list, ListOptionArgumentType.getOption(ctx, "value"))))
         );
      } else {
         switch (setting.getType()) {
            case "Boolean": {
               Setting<Boolean> typed = (Setting<Boolean>)setting;
               builder.then(
                  literal(name(setting))
                     .then(argument("value", BoolArgumentType.bool()).executes(ctx -> this.apply(typed, BoolArgumentType.getBool(ctx, "value"))))
               );
               break;
            }
            case "Integer":
               this.registerNumber(builder, (Setting) setting, Integer.class);
               break;
            case "Long":
               this.registerNumber(builder, (Setting) setting, Long.class);
               break;
            case "Float":
               this.registerNumber(builder, (Setting) setting, Float.class);
               break;
            case "Double":
               this.registerNumber(builder, (Setting) setting, Double.class);
               break;
            case "String": {
               Setting<String> typed = (Setting<String>)setting;
               builder.then(
                  literal(name(setting))
                     .then(argument("value", StringArgumentType.greedyString()).executes(ctx -> this.apply(typed, StringArgumentType.getString(ctx, "value"))))
               );
               break;
            }
            case "Bind": {
               Setting<Bind> typed = (Setting<Bind>)setting;
               builder.then(
                  literal(name(setting))
                     .then(argument("value", KeybindArgumentType.keybind()).executes(ctx -> this.apply(typed, KeybindArgumentType.getBind(ctx, "value"))))
               );
               break;
            }
            case "Color": {
               Setting<Color> typed = (Setting<Color>)setting;
               builder.then(
                  literal(name(setting))
                     .then(argument("value", ColorArgumentType.color()).executes(ctx -> this.apply(typed, ColorArgumentType.getColor(ctx, "value"))))
               );
               break;
            }
            case "Enum":
               this.registerEnum(builder, (Setting<Enum<?>>)setting);
         }
      }
   }

   private void registerEnum(LiteralArgumentBuilder<CommandManager> builder, Setting<Enum<?>> setting) {
      Class<Enum<?>> type = (Class<Enum<?>>)setting.getValue().getDeclaringClass();
      builder.then(
         literal(name(setting))
            .then(argument("value", EnumArgumentType.enumeration(type)).executes(ctx -> this.apply(setting, EnumArgumentType.getEnum(ctx, "value"))))
      );
   }

   private <T extends Number> void registerNumber(LiteralArgumentBuilder<CommandManager> builder, Setting<T> setting, Class<T> type) {
      NumberArgumentType<T> argument = setting.isRanged()
         ? NumberArgumentType.number(type, setting.getMin(), setting.getMax())
         : NumberArgumentType.number(type);
      builder.then(literal(name(setting)).then(argument("value", argument).executes(ctx -> this.apply(setting, NumberArgumentType.get(type, ctx, "value")))));
   }

   private <T> int apply(Setting<T> setting, T value) {
      if (setting == this.module.enabled) {
         this.module.setEnabled((Boolean)value);
      } else {
         setting.setValue(value);
      }

      return this.success("{gray}%s.%s {reset}is now {green}%s", this.module.getName(), setting.getName(), display(setting));
   }

   private int printState() {
      MutableText message = Text.empty();
      message.append(
         ChatUtil.withPrefix(
            Text.literal(this.module.getDisplayName())
               .styled(
                  style -> style.withColor(this.module.isEnabled() ? Formatting.GREEN : Formatting.RED)
                     .withClickEvent(suggest("toggle " + this.module.getName()))
               )
         )
      );
      if (!this.module.getDescription().isEmpty()) {
         message.append(Text.literal("\n  " + this.module.getDescription()).formatted(Formatting.DARK_GRAY));
      }

      for (Setting<?> setting : this.module.getSettings()) {
         if (setting != this.module.enabled) {
            message.append(Text.literal("\n  " + setting.getName() + ": ").formatted(Formatting.GRAY));
            message.append(
               Text.literal(display(setting))
                  .styled(style -> valueStyle(setting, style))
                  .styled(style -> style.withClickEvent(suggest(this.module.getName().toLowerCase(Locale.ROOT) + " " + name(setting) + " ")))
            );
         }
      }

      ChatUtil.raw(message);
      return this.success();
   }

   private static String display(Setting<?> setting) {
      return setting.getValue() instanceof Color color
         ? String.format("#%06X", color.getRGB() & 16777215) + (color.getAlpha() == 255 ? "" : " (alpha " + color.getAlpha() + ")")
         : setting.displayValue();
   }

   private static Style valueStyle(Setting<?> setting, Style style) {
      return switch (setting.getValue()) {
         case Boolean value -> style.withColor(value ? Formatting.GREEN : Formatting.RED);
         case Color value -> style.withColor(value.getRGB() & 16777215);
         case Enum<?> ignored -> style.withColor(Formatting.YELLOW);
         default -> style.withColor(Formatting.WHITE);
      };
   }

   private static ClickEvent suggest(String command) {
      return new SuggestCommand(Homovore2Client.commands().getPrefix() + command);
   }

   private static String name(Setting<?> setting) {
      return setting.getName().toLowerCase(Locale.ROOT).replace(" ", "");
   }

   @Override
   public boolean isShown() {
      return false;
   }
}
