package dev.leonetic.feature.module.impl.hud;

import dev.leonetic.feature.module.impl.HudModule;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.render.Render2D;
import java.awt.Color;
import net.minecraft.class_332;
import net.minecraft.class_634;
import net.minecraft.class_640;

public final class PingElement extends StackedElement {
   private static final String LABEL = "Ping ";
   public final Setting<Boolean> label = this.register(new Setting<>("Label", true)).describe("Writes \"Ping\" before the number.");
   public final Setting<Color> color = this.register(new Setting<>("Color", new Color(255, 255, 255, 255))).describe("The label before the number.");

   public PingElement() {
      super("Ping", true, HudPosition.BOTTOM_RIGHT, 2);
   }

   @Override
   public int width() {
      return Render2D.width(this.prefix()) + Render2D.width(String.valueOf(this.ping()));
   }

   @Override
   public int height() {
      return Render2D.lineHeight();
   }

   @Override
   public void render(class_332 graphics, HudModule hud, int left, int top, int width) {
      String prefix = this.prefix();
      String value = String.valueOf(this.ping());
      int x = this.rowLeft(left, width, Render2D.width(prefix) + Render2D.width(value));
      if (!prefix.isEmpty()) {
         Render2D.text(graphics, prefix, x, top, this.color.getValue().getRGB());
      }

      Render2D.text(graphics, value, x + Render2D.width(prefix), top, -5592406);
   }

   private String prefix() {
      return this.label.getValue() ? "Ping " : "";
   }

   private int ping() {
      class_634 connection = mc.method_1562();
      if (connection != null && mc.field_1724 != null) {
         class_640 info = connection.method_2871(mc.field_1724.method_5667());
         return info == null ? 0 : Math.max(0, info.method_2959());
      } else {
         return 0;
      }
   }
}
