package dev.leonetic.mixin.client;

import dev.leonetic.util.render.font.Fonts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextRenderer.class)
public class MixinFont {
   @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
   private void homovore$widthString(String text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(text));
         } catch (Exception var4) {
         }
      }
   }

   @Inject(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), cancellable = true)
   private void homovore$widthFormatted(OrderedText text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(text));
         } catch (Exception var4) {
         }
      }
   }

   @Inject(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), cancellable = true)
   private void homovore$widthFormattedText(StringVisitable text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(Language.getInstance().reorder(text)));
         } catch (Exception var4) {
         }
      }
   }
}
