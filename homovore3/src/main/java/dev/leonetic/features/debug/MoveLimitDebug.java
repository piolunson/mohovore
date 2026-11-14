package dev.leonetic.features.debug;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.hud.SpeedHudModule;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class MoveLimitDebug implements Util {
   private static final MoveLimitDebug INSTANCE = new MoveLimitDebug();
   private static final int LEFT_MARGIN = 2;
   private static final int BOTTOM_MARGIN = 2;
   private static final int CHAT_INPUT_HEIGHT = 14;
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;
   private static final long WINDOW_MS = 1000L;
   private static final int EXPECTED_MOVES_PER_SECOND = 20;
   private boolean enabled = false;
   private long windowStart = System.currentTimeMillis();
   private int windowCount = 0;
   private int lastCount = 0;

   private MoveLimitDebug() {
      EVENT_BUS.register(this);
   }

   public static MoveLimitDebug get() {
      return INSTANCE;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void toggle() {
      this.enabled = !this.enabled;
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof PlayerMoveC2SPacket) {
         this.rollWindow();
         this.windowCount++;
      }
   }

   @Subscribe
   private void onRender2D(Render2DEvent event) {
      if (this.enabled) {
         if (!mc.options.hudHidden) {
            this.rollWindow();
            DrawContext ctx = event.getContext();
            int ry = this.bottomAnchor() - 2 - 9;
            HudClientModule hud = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
            if (hud != null && hud.isElementEnabled(SpeedHudModule.class)) {
               ry -= 9;
            }

            String value = String.valueOf(this.lastCount);
            String label = "/20 move";
            ctx.drawTextWithShadow(mc.textRenderer, value, 2, ry, -5592406);
            ctx.drawTextWithShadow(mc.textRenderer, label, 2 + mc.textRenderer.getWidth(value), ry, -1);
         }
      }
   }

   private void rollWindow() {
      long now = System.currentTimeMillis();
      if (now - this.windowStart >= 1000L) {
         this.lastCount = this.windowCount;
         this.windowCount = 0;
         this.windowStart = now;
      }
   }

   private int bottomAnchor() {
      int height = mc.getWindow().getScaledHeight();
      return height - (mc.currentScreen instanceof ChatScreen ? 14 : 0);
   }
}
