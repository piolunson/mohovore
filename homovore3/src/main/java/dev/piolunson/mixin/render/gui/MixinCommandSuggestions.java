package dev.piolunson.mixin.render.gui;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.piolunson.Homovore;
import dev.piolunson.manager.CommandManager;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatInputSuggestor.SuggestionWindow;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientCommandSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatInputSuggestor.class)
public abstract class MixinCommandSuggestions {
   @Shadow
   private SuggestionWindow window;
   @Shadow
   private @Nullable CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   private @Nullable ParseResults<ClientCommandSource> parse;
   @Shadow
   boolean completingSuggestions;
   @Shadow
   @Final
   TextFieldWidget textField;

   @Shadow
   protected abstract void showCommandSuggestions();

   @Inject(method = "refresh", at = @At("RETURN"))
   public void updateCommandInfo(CallbackInfo ci, @Local StringReader stringReader) {
      if (stringReader.canRead() && stringReader.getString().startsWith(Homovore.commandManager.getCommandPrefix())) {
         stringReader.skip();
         CommandDispatcher<CommandManager> dispatcher = Homovore.commandManager.getDispatcher();
         ParseResults<CommandManager> parse = dispatcher.parse(stringReader, Homovore.commandManager);
         this.parse = (ParseResults) parse;
         if (this.window == null || !this.completingSuggestions) {
            this.pendingSuggestions = dispatcher.getCompletionSuggestions(parse, this.textField.getCursor());
            this.pendingSuggestions.thenRun(() -> {
               if (this.pendingSuggestions.isDone()) {
                  this.showCommandSuggestions();
               }
            });
         }
      }
   }
}
