package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.PlaceUtil;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ScaffoldModule extends Module {
   private final Setting<Double> radius = this.num("Radius", 1.0, 1.0, 5.0);
   private final Setting<Integer> depth = this.num("Depth", 1, 1, 5);
   private final Setting<Double> lookahead = this.num("Lookahead", 1.0, 0.0, 5.0);
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.5F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 85, 255, 85, 180).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 85, 255, 85, 35).setPage("Render");
   private final Map<BlockPos, Long> renderMap = new LinkedHashMap<>();

   public ScaffoldModule() {
      super("Scaffold", "Places blocks beneath you as you walk, through the PlacementManager. No rotations.", Module.Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.renderMap.clear();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (!mc.player.isSpectator()) {
            Item block = this.findHotbarBlock();
            if (block != null) {
               Vec3d eye = mc.player.getEyePos();
               Vec3d vel = mc.player.getVelocity();
               double centerX = mc.player.getX() + vel.x * this.lookahead.getValue();
               double centerZ = mc.player.getZ() + vel.z * this.lookahead.getValue();
               int baseX = MathHelper.floor(centerX);
               int baseZ = MathHelper.floor(centerZ);
               int feetY = mc.player.getBlockPos().getY();
               int drop = mc.options.sneakKey.isPressed() ? 1 : 0;
               double r = this.radius.getValue() - 1.0;
               double rSq = r * r;
               int scanR = (int)Math.ceil(r);
               int d = this.depth.getValue();
               List<BlockPos> candidates = new ArrayList<>();

               for (int x = -scanR; x <= scanR; x++) {
                  for (int z = -scanR; z <= scanR; z++) {
                     if (!(x * x + z * z > rSq)) {
                        for (int dy = 1; dy <= d; dy++) {
                           BlockPos pos = new BlockPos(baseX + x, feetY - dy - drop, baseZ + z);
                           BlockState state = mc.world.getBlockState(pos);
                           if (state.isReplaceable() && PlaceUtil.canPlace(pos)) {
                              candidates.add(pos.toImmutable());
                           }
                        }
                     }
                  }
               }

               candidates.sort(Comparator.comparingDouble(p -> eye.squaredDistanceTo(Vec3d.ofCenter(p))));
               long now = System.currentTimeMillis();

               for (BlockPos pos : Homovore.placementManager.placeBatch(candidates, block)) {
                  this.renderMap.put(pos, now);
               }

               long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
               this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
            }
         }
      }
   }

   private Item findHotbarBlock() {
      int selected = mc.player.getInventory().getSelectedSlot();
      if (mc.player.getInventory().getStack(selected).getItem() instanceof BlockItem held) {
         return held;
      } else {
         for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem block) {
               return block;
            }
         }

         return null;
      }
   }

   @Subscribe
   private void onRenderTargets(Render3DEvent event) {
      if (!nullCheck() && this.render.getValue()) {
         Color line = this.lineColor.getValue();
         Color fill = this.fillColor.getValue();
         float lw = this.lineWidth.getValue();
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               float t = (float)(1.0 - age / fadeMs);
               BlockPos pos = entry.getKey();
               if (fill.getAlpha() > 0) {
                  RenderUtil.drawBoxFilled(event.getMatrix(), pos, withAlpha(fill, (int)(fill.getAlpha() * t)));
               }

               if (line.getAlpha() > 0) {
                  RenderUtil.drawBox(event.getMatrix(), pos, withAlpha(line, (int)(line.getAlpha() * t)), lw);
               }
            }
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(a, 0, 255));
   }
}
