package dev.leonetic.mixin.client;

import dev.leonetic.util.player.ChatUtil;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class MixinChatComponent {
   @Unique
   private static final double homovore$SPEED = 12.0;
   @Unique
   private static final int homovore$MAX_LINES = 3;
   @Unique
   private boolean homovore$pushed;
   @Unique
   private float homovore$offset;
   @Unique
   private Visible homovore$lastHead;
   @Unique
   private long homovore$lastTimeMs;

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"))
   private void homovore$slideIn(DrawContext graphics, TextRenderer font, int tickCount, int mouseX, int mouseY, boolean focused, boolean bl, CallbackInfo ci) {
      this.homovore$pushed = false;
      float dy = this.homovore$update(((ChatComponentAccessor)this).homovore$getTrimmedMessages());
      if (dy != 0.0F) {
         graphics.getMatrices().pushMatrix();
         graphics.getMatrices().translate(0.0F, dy);
         this.homovore$pushed = true;
      }
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("RETURN"))
   private void homovore$slideInEnd(
      DrawContext graphics, TextRenderer font, int tickCount, int mouseX, int mouseY, boolean focused, boolean bl, CallbackInfo ci
   ) {
      if (this.homovore$pushed) {
         graphics.getMatrices().popMatrix();
         this.homovore$pushed = false;
      }
   }

   @Unique
   private float homovore$update(List<Visible> lines) {
      long now = System.currentTimeMillis();
      float dt = Math.min((float)(now - this.homovore$lastTimeMs) / 1000.0F, 0.1F);
      this.homovore$lastTimeMs = now;
      Visible head = lines.isEmpty() ? null : lines.get(0);
      if (head != this.homovore$lastHead) {
         if (this.homovore$lastHead != null && head != ChatUtil.noAnimateHead) {
            int newLines = 0;

            for (Visible line : lines) {
               if (line == this.homovore$lastHead) {
                  break;
               }

               newLines++;
            }

            float pitch = this.homovore$linePitch();
            this.homovore$offset = Math.min(this.homovore$offset + newLines * pitch, 3.0F * pitch);
         }

         this.homovore$lastHead = head;
      }

      this.homovore$offset = this.homovore$offset * (float)Math.exp(-12.0 * dt);
      if (this.homovore$offset < 0.5F) {
         this.homovore$offset = 0.0F;
      }

      return this.homovore$offset;
   }

   @Unique
   private float homovore$linePitch() {
      MinecraftClient mc = MinecraftClient.getInstance();
      double scale = (Double)mc.options.getChatScale().getValue();
      double spacing = (Double)mc.options.getChatLineSpacing().getValue();
      return (float)(9.0 * (spacing + 1.0) * scale);
   }
}
