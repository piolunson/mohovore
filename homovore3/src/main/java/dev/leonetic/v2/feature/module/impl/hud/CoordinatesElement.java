package dev.leonetic.v2.feature.module.impl.hud;

import dev.leonetic.v2.feature.module.impl.HudModule;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.render.Render2D;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

public final class CoordinatesElement extends StackedElement {
   public final Setting<Boolean> otherDimension = this.register(new Setting<>("Dimension", true))
      .describe("Shows where these coordinates land in the other dimension.");
   public final Setting<Color> color = this.register(new Setting<>("Color", new Color(255, 255, 255, 255))).describe("The coordinates themselves.");

   public CoordinatesElement() {
      super("Coordinates", true, HudPosition.BOTTOM_RIGHT, 3);
   }

   @Override
   public int width() {
      return mc.player != null && mc.world != null ? Render2D.width(this.main()) + Render2D.width(this.other()) : 0;
   }

   @Override
   public int height() {
      return mc.player != null && mc.world != null ? Render2D.lineHeight() : 0;
   }

   @Override
   public void render(DrawContext graphics, HudModule hud, int left, int top, int width) {
      String main = this.main();
      String other = this.other();
      int x = this.rowLeft(left, width, Render2D.width(main) + Render2D.width(other));
      Render2D.text(graphics, main, x, top, this.color.getValue().getRGB());
      if (!other.isEmpty()) {
         Render2D.text(graphics, other, x + Render2D.width(main), top, -5592406);
      }
   }

   private String main() {
      int x = (int)Math.floor(mc.player.getX());
      int y = (int)Math.floor(mc.player.getY());
      int z = (int)Math.floor(mc.player.getZ());
      return x + ", " + y + ", " + z + (this.other().isEmpty() ? "" : " ");
   }

   private String other() {
      if (this.otherDimension.getValue() && !mc.world.getRegistryKey().equals(World.END)) {
         int x = (int)Math.floor(mc.player.getX());
         int z = (int)Math.floor(mc.player.getZ());
         return mc.world.getRegistryKey().equals(World.NETHER)
            ? "[" + x * 8 + ", " + z * 8 + "]"
            : "[" + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8) + "]";
      } else {
         return "";
      }
   }
}
