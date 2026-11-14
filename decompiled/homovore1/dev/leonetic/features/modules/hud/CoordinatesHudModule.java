package dev.leonetic.features.modules.hud;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.client.HudModule;
import dev.leonetic.features.modules.client.HudPosition;
import net.minecraft.class_1937;
import net.minecraft.class_332;

public class CoordinatesHudModule extends HudModule {
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;

   public CoordinatesHudModule() {
      super("Coordinates");
   }

   @Override
   public void render(Render2DEvent event) {
      class_332 ctx = event.getContext();
      int x = (int)Math.floor(mc.field_1724.method_23317());
      int y = (int)Math.floor(mc.field_1724.method_23318());
      int z = (int)Math.floor(mc.field_1724.method_23321());
      boolean nether = mc.field_1687.method_27983().equals(class_1937.field_25180);
      boolean end = mc.field_1687.method_27983().equals(class_1937.field_25181);
      String main = x + ", " + y + ", " + z + (end ? "" : " ");
      String other;
      if (end) {
         other = "";
      } else if (nether) {
         other = "[" + x * 8 + ", " + z * 8 + "]";
      } else {
         other = "[" + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8) + "]";
      }

      int totalWidth = mc.field_1772.method_1727(main) + mc.field_1772.method_1727(other);
      HudClientModule hudClient = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
      HudPosition pos = hudClient != null ? hudClient.positionOf(this) : HudPosition.BOTTOM_RIGHT;
      int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
      int rx = this.lineX(pos, totalWidth);
      int ry = this.blockTop(pos, 1, linesBelow, 0);
      ctx.method_25303(mc.field_1772, main, rx, ry, -1);
      if (!other.isEmpty()) {
         ctx.method_25303(mc.field_1772, other, rx + mc.field_1772.method_1727(main), ry, -5592406);
      }
   }
}
