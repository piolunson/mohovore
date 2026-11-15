package dev.piolunson.mixin.client;

import dev.piolunson.util.render.font.Fonts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class MixinGuiGraphics {
   @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), cancellable = true)
   private void homovore$onDrawString(TextRenderer font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
      if (text != null && !text.isEmpty() && Fonts.drawOverrideActive()) {
         try {
            Fonts.renderOverrideString((DrawContext)(Object)this, text, x, y, color, shadow);
            ci.cancel();
         } catch (Exception var9) {
         }
      }
   }

   @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", at = @At("HEAD"), cancellable = true)
   private void homovore$onDrawFormatted(TextRenderer font, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            Fonts.renderOverrideText((DrawContext)(Object)this, text, x, y, color, shadow);
            ci.cancel();
         } catch (Exception var9) {
         }
      }
   }
}
