package dev.leonetic.mixin.render.gui;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.leonetic.Homovore;
import dev.leonetic.manager.CommandManager;
import java.util.concurrent.CompletableFuture;
import net.minecraft.class_342;
import net.minecraft.class_4717;
import net.minecraft.class_637;
import net.minecraft.class_4717.class_464;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_4717.class)
public abstract class MixinCommandSuggestions {
   @Shadow
   private class_464 field_21612;
   @Shadow
   private @Nullable CompletableFuture<Suggestions> field_21611;
   @Shadow
   private @Nullable ParseResults<class_637> field_21610;
   @Shadow
   boolean field_21614;
   @Shadow
   @Final
   class_342 field_21599;

   @Shadow
   protected abstract void method_23937();

   @Inject(method = "method_23934", at = @At("RETURN"))
   public void updateCommandInfo(CallbackInfo ci, @Local StringReader stringReader) {
      if (stringReader.canRead() && stringReader.getString().startsWith(Homovore.commandManager.getCommandPrefix())) {
         stringReader.skip();
         CommandDispatcher<CommandManager> dispatcher = Homovore.commandManager.getDispatcher();
         ParseResults<CommandManager> parse = dispatcher.parse(stringReader, Homovore.commandManager);
         this.field_21610 = parse;
         if (this.field_21612 == null || !this.field_21614) {
            this.field_21611 = dispatcher.getCompletionSuggestions(parse, this.field_21599.method_1881());
            this.field_21611.thenRun(() -> {
               if (this.field_21611.isDone()) {
                  this.method_23937();
               }
            });
         }
      }
   }
}
