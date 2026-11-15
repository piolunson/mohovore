package dev.piolunson.mixin.render.gui;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(InGameHud.class)
public abstract class MixinHeldItemTooltip {
   private static final int HOMOVORE$Y_OFFSET = 9;

   @ModifyArg(
      method = "renderHeldItemTooltip",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithBackground(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)V"),
      index = 3
   )
   private int homovore$shiftHeldItemName(int y) {
      return y - 9;
   }
}
