package dev.leonetic.feature.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.command.argument.ColorArgumentType;
import dev.leonetic.feature.command.argument.EnumArgumentType;
import dev.leonetic.feature.command.argument.KeybindArgumentType;
import dev.leonetic.feature.command.argument.ListOptionArgumentType;
import dev.leonetic.feature.command.argument.NumberArgumentType;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Bind;
import dev.leonetic.feature.setting.ListSetting;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.util.ChatUtil;
import java.awt.Color;
import java.util.Locale;
import net.minecraft.class_124;
import net.minecraft.class_2558;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;
import net.minecraft.class_2558.class_10610;

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
               this.registerNumber(builder, setting, Integer.class);
               break;
            case "Long":
               this.registerNumber(builder, setting, Long.class);
               break;
            case "Float":
               this.registerNumber(builder, setting, Float.class);
               break;
            case "Double":
               this.registerNumber(builder, setting, Double.class);
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
      class_5250 message = class_2561.method_43473();
      message.method_10852(
         ChatUtil.withPrefix(
            class_2561.method_43470(this.module.getDisplayName())
               .method_27694(
                  style -> style.method_10977(this.module.isEnabled() ? class_124.field_1060 : class_124.field_1061)
                     .method_10958(suggest("toggle " + this.module.getName()))
               )
         )
      );
      if (!this.module.getDescription().isEmpty()) {
         message.method_10852(class_2561.method_43470("\n  " + this.module.getDescription()).method_27692(class_124.field_1063));
      }

      for (Setting<?> setting : this.module.getSettings()) {
         if (setting != this.module.enabled) {
            message.method_10852(class_2561.method_43470("\n  " + setting.getName() + ": ").method_27692(class_124.field_1080));
            message.method_10852(
               class_2561.method_43470(display(setting))
                  .method_27694(style -> valueStyle(setting, style))
                  .method_27694(style -> style.method_10958(suggest(this.module.getName().toLowerCase(Locale.ROOT) + " " + name(setting) + " ")))
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

   private static class_2583 valueStyle(Setting<?> setting, class_2583 style) {
      return switch (setting.getValue()) {
         case Boolean value -> style.method_10977(value ? class_124.field_1060 : class_124.field_1061);
         case Color value -> style.method_36139(value.getRGB() & 16777215);
         case Enum<?> ignored -> style.method_10977(class_124.field_1054);
         default -> style.method_10977(class_124.field_1068);
      };
   }

   private static class_2558 suggest(String command) {
      return new class_10610(Homovore2Client.commands().getPrefix() + command);
   }

   private static String name(Setting<?> setting) {
      return setting.getName().toLowerCase(Locale.ROOT).replace(" ", "");
   }

   @Override
   public boolean isShown() {
      return false;
   }
}
