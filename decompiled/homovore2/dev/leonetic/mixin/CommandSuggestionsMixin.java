package dev.leonetic.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.command.CommandManager;
import java.util.concurrent.CompletableFuture;
import net.minecraft.class_342;
import net.minecraft.class_408;
import net.minecraft.class_437;
import net.minecraft.class_4717;
import net.minecraft.class_637;
import net.minecraft.class_4717.class_464;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_4717.class)
public abstract class CommandSuggestionsMixin {
   @Shadow
   @Final
   private class_437 field_21598;
   @Shadow
   @Final
   private class_342 field_21599;
   @Shadow
   private ParseResults<class_637> field_21610;
   @Shadow
   private CompletableFuture<Suggestions> field_21611;
   @Shadow
   private class_464 field_21612;
   @Shadow
   private boolean field_21614;

   @Invoker("method_23937")
   protected abstract void homovore$invokeUpdateUsageInfo();

   @Inject(method = "method_23934", at = @At("RETURN"))
   private void homovore$suggestCommands(CallbackInfo ci) {
      if (this.field_21598 instanceof class_408) {
         CommandManager commands = Homovore2Client.commands();
         if (commands != null) {
            String text = this.field_21599.method_1882();
            if (commands.isCommand(text)) {
               StringReader reader = new StringReader(text);
               reader.setCursor(commands.getPrefix().length());
               CommandDispatcher<CommandManager> dispatcher = commands.getDispatcher();
               ParseResults<CommandManager> parse = dispatcher.parse(reader, commands);
               this.field_21610 = parse;
               if (this.field_21612 == null || !this.field_21614) {
                  CompletableFuture<Suggestions> pending = dispatcher.getCompletionSuggestions(parse, this.field_21599.method_1881());
                  this.field_21611 = pending;
                  pending.thenAccept(result -> {
                     if (this.field_21611 == pending) {
                        this.homovore$invokeUpdateUsageInfo();
                     }
                  });
               }
            }
         }
      }
   }
}
