package dev.piolunson.features.modules.render;

import dev.piolunson.event.impl.render.Render3DEvent;
import dev.piolunson.event.impl.render.RenderBlockOutlineEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.util.render.RenderUtil;
import java.awt.Color;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

public class BlockHighlightModule extends Module {
   public Setting<Color> color = this.color("Color", 255, 0, 0, 255);
   public Setting<Float> lineWidth = this.num("LineWidth", 1.0F, 0.1F, 5.0F);
   private static final float ANIM_DURATION = 60.0F;
   private BlockHighlightModule.State state = BlockHighlightModule.State.IDLE;
   private Box fromBox;
   private Box toBox;
   private float progress = 0.0F;
   private BlockPos lastPos;
   private long lastNanos;

   public BlockHighlightModule() {
      super("BlockHighlight", "Draws box at the block that you are looking at", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.state = BlockHighlightModule.State.IDLE;
      this.fromBox = null;
      this.toBox = null;
      this.progress = 0.0F;
      this.lastPos = null;
      this.lastNanos = 0L;
   }

   @Subscribe
   @Override
   public void onRender3D(Render3DEvent event) {
      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1000000.0F;
      this.lastNanos = now;
      float duration = 60.0F;
      BlockPos targetPos = null;
      Box targetBox = null;
      if (mc.crosshairTarget instanceof BlockHitResult result) {
         BlockPos bp = result.getBlockPos();
         VoxelShape shape = mc.world.getBlockState(bp).getOutlineShape(mc.world, bp);
         if (!shape.isEmpty()) {
            Box box = shape.getBoundingBox().offset(bp);
            if (mc.player == null || !mc.player.getBoundingBox().intersects(box)) {
               targetPos = bp;
               targetBox = box;
            }
         }
      }

      boolean targetChanged = targetPos != null && !targetPos.equals(this.lastPos);
      boolean targetLost = targetPos == null && this.lastPos != null;
      this.lastPos = targetPos;
      switch (this.state) {
         case IDLE:
            if (targetBox != null) {
               this.fromBox = targetBox;
               this.toBox = targetBox;
               this.progress = 0.0F;
               this.state = BlockHighlightModule.State.FADING_IN;
            }
            break;
         case FADING_IN:
            this.progress = Math.min(1.0F, this.progress + dt / duration);
            if (targetChanged) {
               this.fromBox = this.animatedBox();
               this.toBox = targetBox;
               this.progress = 0.0F;
               this.state = BlockHighlightModule.State.SHIFTING;
            } else if (targetLost) {
               this.progress = 1.0F - this.progress;
               this.state = BlockHighlightModule.State.FADING_OUT;
            } else if (this.progress >= 1.0F) {
               this.state = BlockHighlightModule.State.VISIBLE;
            }
            break;
         case VISIBLE:
            this.progress = 1.0F;
            if (targetChanged) {
               this.fromBox = this.toBox;
               this.toBox = targetBox;
               this.progress = 0.0F;
               this.state = BlockHighlightModule.State.SHIFTING;
            } else if (targetLost) {
               this.progress = 0.0F;
               this.state = BlockHighlightModule.State.FADING_OUT;
            }
            break;
         case SHIFTING:
            this.progress = Math.min(1.0F, this.progress + dt / duration);
            if (targetChanged) {
               this.fromBox = this.animatedBox();
               this.toBox = targetBox;
               this.progress = 0.0F;
            } else if (targetLost) {
               this.fromBox = this.animatedBox();
               this.toBox = this.fromBox;
               this.progress = 0.0F;
               this.state = BlockHighlightModule.State.FADING_OUT;
            } else if (this.progress >= 1.0F) {
               this.state = BlockHighlightModule.State.VISIBLE;
            }
            break;
         case FADING_OUT:
            this.progress = Math.min(1.0F, this.progress + dt / duration);
            if (targetBox != null) {
               this.fromBox = targetBox;
               this.toBox = targetBox;
               this.progress = 0.0F;
               this.state = BlockHighlightModule.State.FADING_IN;
            } else if (this.progress >= 1.0F) {
               this.state = BlockHighlightModule.State.IDLE;
               this.fromBox = null;
               this.toBox = null;
            }
      }

      if (this.state != BlockHighlightModule.State.IDLE) {
         float alpha = switch (this.state) {
            case FADING_IN -> this.smoothstep(this.progress);
            case FADING_OUT -> 1.0F - this.smoothstep(this.progress);
            default -> 1.0F;
         };
         float scale = this.state == BlockHighlightModule.State.SHIFTING ? 1.0F - 0.2F * (float)Math.sin(this.progress * Math.PI) : 1.0F;
         Color c = this.color.getValue();
         Color drawColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * alpha));
         RenderUtil.drawBox(event.getMatrix(), this.scaledBox(this.animatedBox(), scale), drawColor, this.lineWidth.getValue());
      }
   }

   private Box animatedBox() {
      float t = this.smoothstep(this.progress);
      return new Box(
         this.lerp(this.fromBox.minX, this.toBox.minX, t),
         this.lerp(this.fromBox.minY, this.toBox.minY, t),
         this.lerp(this.fromBox.minZ, this.toBox.minZ, t),
         this.lerp(this.fromBox.maxX, this.toBox.maxX, t),
         this.lerp(this.fromBox.maxY, this.toBox.maxY, t),
         this.lerp(this.fromBox.maxZ, this.toBox.maxZ, t)
      );
   }

   private Box scaledBox(Box box, float scale) {
      if (scale == 1.0F) {
         return box;
      }

      double cx = (box.minX + box.maxX) / 2.0;
      double cy = (box.minY + box.maxY) / 2.0;
      double cz = (box.minZ + box.maxZ) / 2.0;
      double hw = (box.maxX - box.minX) / 2.0 * scale;
      double hh = (box.maxY - box.minY) / 2.0 * scale;
      double hd = (box.maxZ - box.minZ) / 2.0 * scale;
      return new Box(cx - hw, cy - hh, cz - hd, cx + hw, cy + hh, cz + hd);
   }

   private float smoothstep(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   private double lerp(double a, double b, float t) {
      return a + (b - a) * t;
   }

   @Subscribe
   public void onRenderBlockOutline(RenderBlockOutlineEvent event) {
      event.cancel();
   }

   private enum State {
      IDLE,
      FADING_IN,
      VISIBLE,
      SHIFTING,
      FADING_OUT;
   }
}
