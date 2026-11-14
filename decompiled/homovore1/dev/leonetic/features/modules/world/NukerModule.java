package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.AttackBlockEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;

public class NukerModule extends Module {
   private final Setting<Double> range = this.num("Range", 5.14, 0.0, 7.0);
   private final Setting<NukerModule.Shape> shape = this.mode("Shape", NukerModule.Shape.All);
   private final Setting<NukerModule.BlockMode> blockMode = this.mode("Blocks", NukerModule.BlockMode.All);
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 255, 40, 40, 255).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 40, 40, 0).setPage("Render");
   private final List<class_2338> targetedBlocks = new ArrayList<>();
   private class_2248 selectedBlock = null;

   public NukerModule() {
      super("Nuker", "Breaks all blocks around you through SpeedMine. Requires SpeedMine.", Module.Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.targetedBlocks.clear();
      this.selectedBlock = null;
   }

   @Subscribe
   private void onAttackBlock(AttackBlockEvent event) {
      if (!nullCheck()) {
         if (this.blockMode.getValue() == NukerModule.BlockMode.Select) {
            class_2680 state = mc.field_1687.method_8320(event.getPos());
            if (!state.method_26215()) {
               this.selectedBlock = state.method_26204();
               Command.sendMessage("Nuker selected: " + this.selectedBlock.method_9518().getString());
            }
         }
      }
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (!mc.field_1724.method_68878() && !mc.field_1724.method_7325()) {
            this.targetedBlocks.clear();
            SpeedMineModule mine = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
            if (mine != null && mine.isEnabled()) {
               class_243 eye = mc.field_1724.method_33571();
               double r = this.range.getValue();
               double rSq = r * r;
               class_2338 origin = mc.field_1724.method_24515();
               int reach = (int)Math.ceil(r);
               List<class_2338> candidates = new ArrayList<>();

               for (int x = -reach; x <= reach; x++) {
                  for (int y = -reach; y <= reach; y++) {
                     for (int z = -reach; z <= reach; z++) {
                        if (this.shape.getValue() != NukerModule.Shape.Flat || y >= 0) {
                           class_2338 pos = origin.method_10069(x, y, z);
                           if (!(new class_238(pos).method_49271(eye) > rSq)) {
                              class_2680 state = mc.field_1687.method_8320(pos);
                              if (!state.method_26215() && this.canBreak(pos, state) && this.passesFilter(state) && mine.inMineRange(pos)) {
                                 candidates.add(pos.method_10062());
                              }
                           }
                        }
                     }
                  }
               }

               candidates.sort(Comparator.comparingDouble(p -> new class_238(p).method_49271(eye)));

               for (class_2338 pos : candidates) {
                  if (mine.isMining(pos)) {
                     this.targetedBlocks.add(pos);
                  }
               }

               int free = (mine.hasFreePrimary() ? 1 : 0) + (mine.hasFreeSecondary() ? 1 : 0);

               for (class_2338 pos : candidates) {
                  if (free <= 0) {
                     break;
                  }

                  if (!mine.isMining(pos) && mine.requestBreak(pos)) {
                     this.targetedBlocks.add(pos);
                     free--;
                  }
               }
            }
         }
      }
   }

   @Subscribe
   private void onRenderTargets(Render3DEvent event) {
      if (!nullCheck() && this.render.getValue()) {
         Color line = this.lineColor.getValue();
         Color fill = this.fillColor.getValue();
         float lw = this.lineWidth.getValue();

         for (class_2338 pos : this.targetedBlocks) {
            if (!mc.field_1687.method_8320(pos).method_26215()) {
               if (fill.getAlpha() > 0) {
                  RenderUtil.drawBoxFilled(event.getMatrix(), pos, fill);
               }

               if (line.getAlpha() > 0) {
                  RenderUtil.drawBox(event.getMatrix(), pos, line, lw);
               }
            }
         }
      }
   }

   private boolean passesFilter(class_2680 state) {
      return this.blockMode.getValue() == NukerModule.BlockMode.All ? true : this.selectedBlock != null && state.method_26204() == this.selectedBlock;
   }

   private boolean canBreak(class_2338 pos, class_2680 state) {
      return !state.method_26215() && state.method_26214(mc.field_1687, pos) >= 0.0F;
   }

   enum BlockMode {
      All,
      Select;
   }

   enum Shape {
      All,
      Flat;
   }
}
