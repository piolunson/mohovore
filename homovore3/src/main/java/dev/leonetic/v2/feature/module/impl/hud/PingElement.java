package dev.leonetic.v2.feature.module.impl.hud;

import dev.leonetic.v2.feature.module.impl.HudModule;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

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
   public void render(DrawContext graphics, HudModule hud, int left, int top, int width) {
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
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      if (connection != null && mc.player != null) {
         PlayerListEntry info = connection.getPlayerListEntry(mc.player.getUuid());
         return info == null ? 0 : Math.max(0, info.getLatency());
      } else {
         return 0;
      }
   }
}
