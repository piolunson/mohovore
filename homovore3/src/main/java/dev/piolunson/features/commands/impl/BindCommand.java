package dev.piolunson.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.piolunson.event.impl.input.KeyInputEvent;
import dev.piolunson.event.impl.input.MouseInputEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.commands.Command;
import dev.piolunson.features.commands.argument.ModuleArgumentType;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Bind;
import dev.piolunson.manager.CommandManager;
import dev.piolunson.util.KeyboardUtil;

public class BindCommand extends Command {
   private Module module;

   public BindCommand() {
      super("bind", "setbind");
      this.setDescription("Sets a key bind for a module");
      EVENT_BUS.register(this);
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(argument("module", ModuleArgumentType.module(true)).executes(ctx -> {
         this.module = ModuleArgumentType.getModule(ctx, "module");
         return this.success("Press any key...");
      }));
   }

   @Subscribe
   public void onKey(KeyInputEvent event) {
      if (!nullCheck() && this.module != null && event.getKey() != -1 && event.getAction() == 1) {
         if (event.getKey() == 256) {
            this.module = null;
            sendMessage("Operation canceled.");
         } else {
            sendMessage("Bind for {green} %s {} set to {green} %s", this.module.getName(), KeyboardUtil.getKeyName(event.getKey()));
            this.module.bind.setValue(new Bind(event.getKey()));
            this.module = null;
         }
      }
   }

   @Subscribe
   public void onMouse(MouseInputEvent event) {
      if (!nullCheck() && this.module != null && event.getAction() == 1) {
         Bind bind = Bind.fromMouseButton(event.getButton());
         sendMessage("Bind for {green} %s {} set to {green} %s", this.module.getName(), bind.toString());
         this.module.bind.setValue(bind);
         this.module = null;
      }
   }
}
