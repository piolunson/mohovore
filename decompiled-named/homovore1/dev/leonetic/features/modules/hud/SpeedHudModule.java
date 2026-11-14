package dev.leonetic.features.modules.hud;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudModule;
import net.minecraft.client.gui.DrawContext;

public class SpeedHudModule extends HudModule {
   private static final int LEFT_MARGIN = 2;
   private static final int BOTTOM_MARGIN = 2;
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;

   public SpeedHudModule() {
      super("Speed");
   }

   @Override
   public void render(Render2DEvent event) {
      DrawContext ctx = event.getContext();
      double dx = mc.player.getX() - mc.player.lastX;
      double dz = mc.player.getZ() - mc.player.lastZ;
      double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
      String value = String.format("%.1f", bps);
      String unit = " bps";
      int ry = this.bottomAnchor() - 2 - 9;
      ctx.drawTextWithShadow(mc.textRenderer, value, 2, ry, -5592406);
      ctx.drawTextWithShadow(mc.textRenderer, unit, 2 + mc.textRenderer.getWidth(value), ry, -1);
   }
}
