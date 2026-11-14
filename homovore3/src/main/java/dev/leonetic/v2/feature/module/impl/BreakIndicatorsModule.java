package dev.leonetic.v2.feature.module.impl;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.feature.module.Category;
import dev.leonetic.v2.feature.module.Module;
import dev.leonetic.v2.feature.setting.Setting;
import dev.leonetic.v2.manager.BreakManager;
import java.awt.Color;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

public final class BreakIndicatorsModule extends Module {
   public final Setting<Boolean> self = this.bool("Self", true).describe("Also draws the blocks you are breaking yourself.");
   public final Setting<Boolean> second = this.bool("Second", true).describe("Draws the other block of a player mining two at once.");
   public final Setting<Color> lineColor = this.color("LineColor", 255, 0, 0, 255);
   public final Setting<Color> sideColor = this.color("SideColor", 198, 176, 12, 0);
   public final Setting<Double> lineWidth = this.number("LineWidth", (double)3.0, (double)0.5, (double)5.0);
   public final Setting<Boolean> throughWalls = this.bool("ThroughWalls", true);

   public BreakIndicatorsModule() {
      super("BreakIndicators", Category.RENDER, "Highlights the blocks players are breaking.");
   }

   @Override
   public void onRender3D(Camera camera, float partialTick) {
      if (mc.world != null) {
         BreakManager breaks = Homovore2Client.breaks();
         breaks.forEachStarted(this::renderBox);
         if (this.second.getValue()) {
            breaks.forEachSecond(this::renderBox);
         }
      }
   }

   private void renderBox(BlockPos pos, int minerId) {
      if (this.shown(minerId)) {
         VisibilityConfigurable gizmo = GizmoDrawing.box(
            new Box(pos),
            DrawStyle.filledAndStroked(this.lineColor.getValue().getRGB(), this.lineWidth.getValue().floatValue(), this.sideColor.getValue().getRGB()),
            false
         );
         if (this.throughWalls.getValue()) {
            gizmo.ignoreOcclusion();
         }
      }
   }

   private boolean shown(int id) {
      return mc.world.getEntityById(id) instanceof PlayerEntity player ? this.self.getValue() || player != mc.player : false;
   }
}
