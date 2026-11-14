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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class NukerModule extends Module {
   private final Setting<Double> range = this.num("Range", 5.14, 0.0, 7.0);
   private final Setting<NukerModule.Shape> shape = this.mode("Shape", NukerModule.Shape.All);
   private final Setting<NukerModule.BlockMode> blockMode = this.mode("Blocks", NukerModule.BlockMode.All);
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 255, 40, 40, 255).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 40, 40, 0).setPage("Render");
   private final List<BlockPos> targetedBlocks = new ArrayList<>();
   private Block selectedBlock = null;

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
            BlockState state = mc.world.getBlockState(event.getPos());
            if (!state.isAir()) {
               this.selectedBlock = state.getBlock();
               Command.sendMessage("Nuker selected: " + this.selectedBlock.getName().getString());
            }
         }
      }
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (!mc.player.isCreative() && !mc.player.isSpectator()) {
            this.targetedBlocks.clear();
            SpeedMineModule mine = Homovore.moduleManager.getModuleByClass(SpeedMineModule.class);
            if (mine != null && mine.isEnabled()) {
               Vec3d eye = mc.player.getEyePos();
               double r = this.range.getValue();
               double rSq = r * r;
               BlockPos origin = mc.player.getBlockPos();
               int reach = (int)Math.ceil(r);
               List<BlockPos> candidates = new ArrayList<>();

               for (int x = -reach; x <= reach; x++) {
                  for (int y = -reach; y <= reach; y++) {
                     for (int z = -reach; z <= reach; z++) {
                        if (this.shape.getValue() != NukerModule.Shape.Flat || y >= 0) {
                           BlockPos pos = origin.add(x, y, z);
                           if (!(new Box(pos).squaredMagnitude(eye) > rSq)) {
                              BlockState state = mc.world.getBlockState(pos);
                              if (!state.isAir() && this.canBreak(pos, state) && this.passesFilter(state) && mine.inMineRange(pos)) {
                                 candidates.add(pos.toImmutable());
                              }
                           }
                        }
                     }
                  }
               }

               candidates.sort(Comparator.comparingDouble(p -> new Box(p).squaredMagnitude(eye)));

               for (BlockPos pos : candidates) {
                  if (mine.isMining(pos)) {
                     this.targetedBlocks.add(pos);
                  }
               }

               int free = (mine.hasFreePrimary() ? 1 : 0) + (mine.hasFreeSecondary() ? 1 : 0);

               for (BlockPos pos : candidates) {
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

         for (BlockPos pos : this.targetedBlocks) {
            if (!mc.world.getBlockState(pos).isAir()) {
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

   private boolean passesFilter(BlockState state) {
      return this.blockMode.getValue() == NukerModule.BlockMode.All ? true : this.selectedBlock != null && state.getBlock() == this.selectedBlock;
   }

   private boolean canBreak(BlockPos pos, BlockState state) {
      return !state.isAir() && state.getHardness(mc.world, pos) >= 0.0F;
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
