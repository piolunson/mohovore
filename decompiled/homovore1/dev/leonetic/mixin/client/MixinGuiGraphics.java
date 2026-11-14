package dev.leonetic.mixin.client;

import dev.leonetic.util.render.font.Fonts;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_5481;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_332.class)
public abstract class MixinGuiGraphics {
   @Inject(method = "method_51433(Lnet/minecraft/class_327;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), cancellable = true)
   private void homovore$onDrawString(class_327 font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
      if (text != null && !text.isEmpty() && Fonts.drawOverrideActive()) {
         try {
            Fonts.renderOverrideString((class_332)this, text, x, y, color, shadow);
            ci.cancel();
         } catch (Exception var9) {
         }
      }
   }

   @Inject(method = "method_51430(Lnet/minecraft/class_327;Lnet/minecraft/class_5481;IIIZ)V", at = @At("HEAD"), cancellable = true)
   private void homovore$onDrawFormatted(class_327 font, class_5481 text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            Fonts.renderOverrideText((class_332)this, text, x, y, color, shadow);
            ci.cancel();
         } catch (Exception var9) {
         }
      }
   }
}
