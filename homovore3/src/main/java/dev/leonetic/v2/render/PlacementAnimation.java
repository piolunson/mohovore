package dev.leonetic.v2.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

public final class PlacementAnimation {
   private static final float LN2 = 0.6931472F;
   private static final float MAX_DELTA = 0.1F;
   private static final float SPINNER_EXPAND = 0.6F;
   private static final int SPINNER_CAPACITY = 32;
   private static final float ALPHA_HALF_LIFE = 0.06F;
   private static final float ALPHA_CUTOFF = 0.004F;
   private static final float SPIN_WRAP = 360.0F;
   private static final long NO_PLACEMENT = Long.MIN_VALUE;
   private final AtomicLong lastBase = new AtomicLong(Long.MIN_VALUE);
   private final AtomicLong placements = new AtomicLong();
   private long seenPlacements;
   private long lastFrame;
   private boolean hasPane;
   private double paneX;
   private double paneY;
   private double paneZ;
   private double targetX;
   private double targetY;
   private double targetZ;
   private float paneAlpha;
   private float sincePlacement;
   private final double[] spinnerX = new double[32];
   private final double[] spinnerY = new double[32];
   private final double[] spinnerZ = new double[32];
   private final float[] spinnerAngle = new float[32];
   private final float[] spinnerLife = new float[32];
   private int spinnerHead;
   private int spinnerCount;
   private final Map<Long, Float> boxes = new HashMap<>();
   private float spin;

   public void record(long packedBase) {
      this.lastBase.set(packedBase);
      this.placements.incrementAndGet();
   }

   public void clear() {
      this.lastBase.set(Long.MIN_VALUE);
      this.seenPlacements = this.placements.get();
      this.hasPane = false;
      this.paneAlpha = 0.0F;
      this.spinnerHead = 0;
      this.spinnerCount = 0;
      this.boxes.clear();
      this.spin = 0.0F;
   }

   public void render(PlacementAnimation.Style style) {
      float delta = this.frameDelta();
      long placed = this.placements.get();
      long base = this.lastBase.get();
      if (placed != this.seenPlacements && base != Long.MIN_VALUE) {
         this.onPlacement(style, base, placed - this.seenPlacements);
         this.seenPlacements = placed;
      } else {
         this.sincePlacement += delta;
      }

      this.renderPane(style, delta);
      this.renderSpinner(style, delta);
      this.renderBoxes(style, delta);
   }

   private void onPlacement(PlacementAnimation.Style style, long base, long steps) {
      this.sincePlacement = 0.0F;
      double x = BlockPos.unpackLongX(base) + 0.5;
      double y = BlockPos.unpackLongY(base) + 1.0;
      double z = BlockPos.unpackLongZ(base) + 0.5;
      this.targetX = x;
      this.targetY = y;
      this.targetZ = z;
      if (!this.hasPane) {
         this.paneX = x;
         this.paneY = y;
         this.paneZ = z;
         this.hasPane = true;
      }

      long shown = Math.min(steps, 32L);
      this.spin = wrapSpin(this.spin + style.spinnerStep() * (float)(steps - shown));

      for (long i = 0L; i < shown; i++) {
         this.spin = wrapSpin(this.spin + style.spinnerStep());
         this.spawnSpinner(x, y, z, this.spin);
      }

      this.spawnBox(base);
   }

   private static float wrapSpin(float angle) {
      return angle % 360.0F;
   }

   private void spawnSpinner(double x, double y, double z, float angle) {
      this.spinnerX[this.spinnerHead] = x;
      this.spinnerY[this.spinnerHead] = y;
      this.spinnerZ[this.spinnerHead] = z;
      this.spinnerAngle[this.spinnerHead] = angle;
      this.spinnerLife[this.spinnerHead] = 1.0F;
      this.spinnerHead = (this.spinnerHead + 1) % 32;
      if (this.spinnerCount < 32) {
         this.spinnerCount++;
      }
   }

   private void spawnBox(long base) {
      this.boxes.put(base, 1.0F);
   }

   private void renderPane(PlacementAnimation.Style style, float delta) {
      if (style.pane() && this.hasPane) {
         float halfLife = style.paneHalfLife();
         this.paneX = towards(this.paneX, this.targetX, halfLife, delta);
         this.paneY = towards(this.paneY, this.targetY, halfLife, delta);
         this.paneZ = towards(this.paneZ, this.targetZ, halfLife, delta);
         boolean lingering = this.sincePlacement <= style.paneLinger();
         if (lingering) {
            this.paneAlpha = towards(this.paneAlpha, 1.0F, 0.06F, delta, 0.004F);
         } else {
            float fade = style.paneFade();
            this.paneAlpha = fade <= 0.0F ? 0.0F : Math.max(0.0F, this.paneAlpha - delta / fade);
         }

         if (this.paneAlpha <= 0.0F) {
            if (!lingering) {
               this.hasPane = false;
            }
         } else {
            double half = style.paneSize() * 0.5;
            double y = this.paneY + style.height();
            Vec3d a = new Vec3d(this.paneX - half, y, this.paneZ - half);
            Vec3d b = new Vec3d(this.paneX + half, y, this.paneZ - half);
            Vec3d c = new Vec3d(this.paneX + half, y, this.paneZ + half);
            Vec3d d = new Vec3d(this.paneX - half, y, this.paneZ + half);
            DrawStyle gizmo = DrawStyle.filledAndStroked(
               fade(style.paneLineColor(), this.paneAlpha), style.paneLineWidth(), fade(style.paneSideColor(), this.paneAlpha)
            );
            depth(GizmoDrawing.quad(a, b, c, d, gizmo), style.throughWalls());
         }
      }
   }

   private void renderSpinner(PlacementAnimation.Style style, float delta) {
      if (style.spinner() && this.spinnerCount != 0) {
         float fade = style.spinnerFade();
         int alive = 0;

         for (int age = 0; age < this.spinnerCount; age++) {
            int slot = Math.floorMod(this.spinnerHead - 1 - age, 32);
            float life = fade <= 0.0F ? 0.0F : Math.max(0.0F, this.spinnerLife[slot] - delta / fade);
            this.spinnerLife[slot] = life;
            if (life <= 0.0F) {
               break;
            }

            alive++;
            this.drawSpinner(style, slot, life);
         }

         this.spinnerCount = alive;
      }
   }

   private void drawSpinner(PlacementAnimation.Style style, int slot, float life) {
      float alpha = life * life;
      double radius = style.spinnerSize() * 0.5 * (1.0 + 0.6F * (1.0F - life));
      double x = this.spinnerX[slot];
      double y = this.spinnerY[slot] + style.height();
      double z = this.spinnerZ[slot];
      Vec3d[] corners = new Vec3d[3];

      for (int i = 0; i < 3; i++) {
         double angle = Math.toRadians(this.spinnerAngle[slot] + i * 120.0);
         corners[i] = new Vec3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
      }

      depth(GizmoDrawing.quad(corners[0], corners[1], corners[2], corners[2], DrawStyle.filled(fade(style.spinnerSideColor(), alpha))), style.throughWalls());
      int color = fade(style.spinnerLineColor(), alpha);
      float width = style.spinnerLineWidth();

      for (int i = 0; i < 3; i++) {
         depth(GizmoDrawing.line(corners[i], corners[(i + 1) % 3], color, width), style.throughWalls());
      }
   }

   private void renderBoxes(PlacementAnimation.Style style, float delta) {
      if (!this.boxes.isEmpty()) {
         float fade = style.boxFade();
         Iterator<Entry<Long, Float>> entries = this.boxes.entrySet().iterator();

         while (entries.hasNext()) {
            Entry<Long, Float> entry = entries.next();
            float life = fade <= 0.0F ? 0.0F : Math.max(0.0F, entry.getValue() - delta / fade);
            if (life <= 0.0F) {
               entries.remove();
            } else {
               entry.setValue(life);
               if (style.box()) {
                  this.drawBox(style, entry.getKey(), life);
               }
            }
         }
      }
   }

   private void drawBox(PlacementAnimation.Style style, long base, float life) {
      DrawStyle gizmo = DrawStyle.filledAndStroked(fade(style.boxLineColor(), life), style.boxLineWidth(), fade(style.boxSideColor(), life));
      depth(GizmoDrawing.box(new Box(BlockPos.fromLong(base)), gizmo, false), style.throughWalls());
   }

   private static void depth(VisibilityConfigurable gizmo, boolean throughWalls) {
      if (throughWalls) {
         gizmo.ignoreOcclusion();
      }
   }

   private float frameDelta() {
      long now = System.nanoTime();
      if (this.lastFrame == 0L) {
         this.lastFrame = now;
         return 0.0F;
      } else {
         float delta = Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
         this.lastFrame = now;
         return delta;
      }
   }

   private static float towards(float current, float target, float halfLife, float delta) {
      return towards(current, target, halfLife, delta, 0.001F);
   }

   private static float towards(float current, float target, float halfLife, float delta, float epsilon) {
      if (halfLife <= 0.0F) {
         return target;
      }

      float next = current + (target - current) * (1.0F - (float)Math.exp(-delta * (0.6931472F / halfLife)));
      return Math.abs(target - next) < epsilon ? target : next;
   }

   private static double towards(double current, double target, float halfLife, float delta) {
      if (halfLife <= 0.0F) {
         return target;
      }

      double next = current + (target - current) * (1.0 - Math.exp(-delta * (0.6931472F / halfLife)));
      return Math.abs(target - next) < 1.0E-4 ? target : next;
   }

   private static int fade(int argb, float multiplier) {
      int alpha = Math.round((argb >>> 24 & 0xFF) * Math.clamp(multiplier, 0.0F, 1.0F));
      return alpha << 24 | argb & 16777215;
   }

   public record Style(
      boolean pane,
      int paneLineColor,
      int paneSideColor,
      float paneLineWidth,
      double paneSize,
      float paneHalfLife,
      float paneLinger,
      float paneFade,
      boolean spinner,
      int spinnerLineColor,
      int spinnerSideColor,
      float spinnerLineWidth,
      double spinnerSize,
      float spinnerStep,
      float spinnerFade,
      boolean box,
      int boxLineColor,
      int boxSideColor,
      float boxLineWidth,
      float boxFade,
      double height,
      boolean throughWalls
   ) {
   }
}
