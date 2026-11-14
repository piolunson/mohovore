package dev.leonetic.v2.feature.module.impl.hud;

import dev.leonetic.v2.feature.module.impl.HudModule;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;

public final class SpeedElement extends StackedElement {
   private static final String UNIT = " bps";
   public final Setting<Color> color = this.register(new Setting<>("Color", new Color(255, 255, 255, 255))).describe("The unit after the number.");

   public SpeedElement() {
      super("Speed", false, HudPosition.BOTTOM_LEFT, 0);
   }

   @Override
   public int width() {
      return mc.player == null ? 0 : Render2D.width(this.value()) + Render2D.width(" bps");
   }

   @Override
   public int height() {
      return mc.player == null ? 0 : Render2D.lineHeight();
   }

   @Override
   public void render(DrawContext graphics, HudModule hud, int left, int top, int width) {
      String value = this.value();
      int x = this.rowLeft(left, width, Render2D.width(value) + Render2D.width(" bps"));
      Render2D.text(graphics, value, x, top, -5592406);
      Render2D.text(graphics, " bps", x + Render2D.width(value), top, this.color.getValue().getRGB());
   }

   private String value() {
      double dx = mc.player.getX() - mc.player.lastX;
      double dz = mc.player.getZ() - mc.player.lastZ;
      return String.format("%.1f", Math.sqrt(dx * dx + dz * dz) * 20.0);
   }
}
