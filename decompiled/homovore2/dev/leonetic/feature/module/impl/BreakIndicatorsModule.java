package dev.leonetic.feature.module.impl;

import dev.leonetic.Homovore2Client;
import dev.leonetic.feature.module.Category;
import dev.leonetic.feature.module.Module;
import dev.leonetic.feature.setting.Setting;
import dev.leonetic.manager.BreakManager;
import java.awt.Color;
import net.minecraft.class_12178;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_4184;

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
   public void onRender3D(class_4184 camera, float partialTick) {
      if (mc.field_1687 != null) {
         BreakManager breaks = Homovore2Client.breaks();
         breaks.forEachStarted(this::renderBox);
         if (this.second.getValue()) {
            breaks.forEachSecond(this::renderBox);
         }
      }
   }

   private void renderBox(class_2338 pos, int minerId) {
      if (this.shown(minerId)) {
         class_12178 gizmo = class_12180.method_75542(
            new class_238(pos),
            class_12179.method_75537(this.lineColor.getValue().getRGB(), this.lineWidth.getValue().floatValue(), this.sideColor.getValue().getRGB()),
            false
         );
         if (this.throughWalls.getValue()) {
            gizmo.method_75533();
         }
      }
   }

   private boolean shown(int id) {
      return mc.field_1687.method_8469(id) instanceof class_1657 player ? this.self.getValue() || player != mc.field_1724 : false;
   }
}
