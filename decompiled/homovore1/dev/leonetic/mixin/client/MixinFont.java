package dev.leonetic.mixin.client;

import dev.leonetic.util.render.font.Fonts;
import net.minecraft.class_2477;
import net.minecraft.class_327;
import net.minecraft.class_5348;
import net.minecraft.class_5481;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_327.class)
public class MixinFont {
   @Inject(method = "method_1727(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
   private void homovore$widthString(String text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(text));
         } catch (Exception var4) {
         }
      }
   }

   @Inject(method = "method_30880(Lnet/minecraft/class_5481;)I", at = @At("HEAD"), cancellable = true)
   private void homovore$widthFormatted(class_5481 text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(text));
         } catch (Exception var4) {
         }
      }
   }

   @Inject(method = "method_27525(Lnet/minecraft/class_5348;)I", at = @At("HEAD"), cancellable = true)
   private void homovore$widthFormattedText(class_5348 text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(class_2477.method_10517().method_30934(text)));
         } catch (Exception var4) {
         }
      }
   }
}
