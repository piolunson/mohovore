package dev.leonetic.features.modules.render;

import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.impl.render.RenderBlockOutlineEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.render.RenderUtil;
import java.awt.Color;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_265;
import net.minecraft.class_3965;

public class BlockHighlightModule extends Module {
   public Setting<Color> color = this.color("Color", 255, 0, 0, 255);
   public Setting<Float> lineWidth = this.num("LineWidth", 1.0F, 0.1F, 5.0F);
   private static final float ANIM_DURATION = 60.0F;
   private BlockHighlightModule.State state = BlockHighlightModule.State.IDLE;
   private class_238 fromBox;
   private class_238 toBox;
   private float progress = 0.0F;
   private class_2338 lastPos;
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
      class_2338 targetPos = null;
      class_238 targetBox = null;
      if (mc.field_1765 instanceof class_3965 result) {
         class_2338 bp = result.method_17777();
         class_265 shape = mc.field_1687.method_8320(bp).method_26218(mc.field_1687, bp);
         if (!shape.method_1110()) {
            class_238 box = shape.method_1107().method_996(bp);
            if (mc.field_1724 == null || !mc.field_1724.method_5829().method_994(box)) {
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

   private class_238 animatedBox() {
      float t = this.smoothstep(this.progress);
      return new class_238(
         this.lerp(this.fromBox.field_1323, this.toBox.field_1323, t),
         this.lerp(this.fromBox.field_1322, this.toBox.field_1322, t),
         this.lerp(this.fromBox.field_1321, this.toBox.field_1321, t),
         this.lerp(this.fromBox.field_1320, this.toBox.field_1320, t),
         this.lerp(this.fromBox.field_1325, this.toBox.field_1325, t),
         this.lerp(this.fromBox.field_1324, this.toBox.field_1324, t)
      );
   }

   private class_238 scaledBox(class_238 box, float scale) {
      if (scale == 1.0F) {
         return box;
      }

      double cx = (box.field_1323 + box.field_1320) / 2.0;
      double cy = (box.field_1322 + box.field_1325) / 2.0;
      double cz = (box.field_1321 + box.field_1324) / 2.0;
      double hw = (box.field_1320 - box.field_1323) / 2.0 * scale;
      double hh = (box.field_1325 - box.field_1322) / 2.0 * scale;
      double hd = (box.field_1324 - box.field_1321) / 2.0 * scale;
      return new class_238(cx - hw, cy - hh, cz - hd, cx + hw, cy + hh, cz + hd);
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
