package dev.leonetic.features.modules.hud;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudModule;
import net.minecraft.class_332;

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
      class_332 ctx = event.getContext();
      double dx = mc.field_1724.method_23317() - mc.field_1724.field_6014;
      double dz = mc.field_1724.method_23321() - mc.field_1724.field_5969;
      double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
      String value = String.format("%.1f", bps);
      String unit = " bps";
      int ry = this.bottomAnchor() - 2 - 9;
      ctx.method_25303(mc.field_1772, value, 2, ry, -5592406);
      ctx.method_25303(mc.field_1772, unit, 2 + mc.field_1772.method_1727(value), ry, -1);
   }
}
