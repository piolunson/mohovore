package dev.leonetic.features.modules.hud;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.client.HudClientModule;
import dev.leonetic.features.modules.client.HudModule;
import dev.leonetic.features.modules.client.HudPosition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

public class CoordinatesHudModule extends HudModule {
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;

   public CoordinatesHudModule() {
      super("Coordinates");
   }

   @Override
   public void render(Render2DEvent event) {
      DrawContext ctx = event.getContext();
      int x = (int)Math.floor(mc.player.getX());
      int y = (int)Math.floor(mc.player.getY());
      int z = (int)Math.floor(mc.player.getZ());
      boolean nether = mc.world.getRegistryKey().equals(World.NETHER);
      boolean end = mc.world.getRegistryKey().equals(World.END);
      String main = x + ", " + y + ", " + z + (end ? "" : " ");
      String other;
      if (end) {
         other = "";
      } else if (nether) {
         other = "[" + x * 8 + ", " + z * 8 + "]";
      } else {
         other = "[" + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8) + "]";
      }

      int totalWidth = mc.textRenderer.getWidth(main) + mc.textRenderer.getWidth(other);
      HudClientModule hudClient = Homovore.moduleManager.getModuleByClass(HudClientModule.class);
      HudPosition pos = hudClient != null ? hudClient.positionOf(this) : HudPosition.BOTTOM_RIGHT;
      int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
      int rx = this.lineX(pos, totalWidth);
      int ry = this.blockTop(pos, 1, linesBelow, 0);
      ctx.drawTextWithShadow(mc.textRenderer, main, rx, ry, -1);
      if (!other.isEmpty()) {
         ctx.drawTextWithShadow(mc.textRenderer, other, rx + mc.textRenderer.getWidth(main), ry, -5592406);
      }
   }
}
