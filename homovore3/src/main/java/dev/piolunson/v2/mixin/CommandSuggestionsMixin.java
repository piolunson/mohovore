package dev.piolunson.v2.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.feature.command.CommandManager;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatInputSuggestor.SuggestionWindow;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientCommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatInputSuggestor.class)
public abstract class CommandSuggestionsMixin {
   @Shadow
   @Final
   private Screen owner;
   @Shadow
   @Final
   private TextFieldWidget textField;
   @Shadow
   private ParseResults<ClientCommandSource> parse;
   @Shadow
   private CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   private SuggestionWindow window;
   @Shadow
   private boolean completingSuggestions;

   @Invoker("showCommandSuggestions")
   protected abstract void homovore$invokeUpdateUsageInfo();

   @Inject(method = "refresh", at = @At("RETURN"))
   private void homovore$suggestCommands(CallbackInfo ci) {
      if (this.owner instanceof ChatScreen) {
         CommandManager commands = Homovore2Client.commands();
         if (commands != null) {
            String text = this.textField.getText();
            if (commands.isCommand(text)) {
               StringReader reader = new StringReader(text);
               reader.setCursor(commands.getPrefix().length());
               CommandDispatcher<CommandManager> dispatcher = commands.getDispatcher();
               ParseResults<CommandManager> parse = dispatcher.parse(reader, commands);
               this.parse = (ParseResults) parse;
               if (this.window == null || !this.completingSuggestions) {
                  CompletableFuture<Suggestions> pending = dispatcher.getCompletionSuggestions(parse, this.textField.getCursor());
                  this.pendingSuggestions = pending;
                  pending.thenAccept(result -> {
                     if (this.pendingSuggestions == pending) {
                        this.homovore$invokeUpdateUsageInfo();
                     }
                  });
               }
            }
         }
      }
   }
}
