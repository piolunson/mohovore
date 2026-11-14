package dev.leonetic.mixin;

import dev.leonetic.Homovore2;
import dev.leonetic.util.ChatUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
   private static final int MARGIN = 4;
   private static final int GAP = 3;
   private static final int COLOR = -1;

   @Inject(method = "render", at = @At("TAIL"))
   private void homovore$signature(DrawContext graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Text logo = ChatUtil.clientName();
      graphics.drawTextWithShadow(mc.textRenderer, logo, 4, 4, -1);
      graphics.drawTextWithShadow(mc.textRenderer, Text.literal(Homovore2.version()), 4 + mc.textRenderer.getWidth(logo) + 3, 4, -1);
   }
}
