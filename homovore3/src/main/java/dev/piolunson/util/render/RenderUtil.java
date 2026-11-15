package dev.piolunson.util.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import dev.piolunson.util.render.state.RectRenderState;
import dev.piolunson.util.traits.Util;
import java.awt.Color;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

public class RenderUtil implements Util {
   public static void rect(DrawContext context, float x1, float y1, float x2, float y2, int color) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      context.fill(ix1, iy1, ix2, iy2, GuiFade.apply(color));
   }

   public static void rect(DrawContext context, float x1, float y1, float x2, float y2, int color, float width) {
      int w = Math.max(1, Math.round(width));
      int c = GuiFade.apply(color);
      context.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y1) + w, c);
      context.fill(Math.round(x2) - w, Math.round(y1), Math.round(x2), Math.round(y2), c);
      context.fill(Math.round(x1), Math.round(y2) - w, Math.round(x2), Math.round(y2), c);
      context.fill(Math.round(x1), Math.round(y1), Math.round(x1) + w, Math.round(y2), c);
   }

   public static void horizontalGradient(DrawContext context, float x1, float y1, float x2, float y2, Color left, Color right) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      gradient(context, ix1, iy1, ix2, iy2, left.hashCode(), left.hashCode(), right.hashCode(), right.hashCode());
   }

   public static void verticalGradient(DrawContext context, float x1, float y1, float x2, float y2, Color top, Color bottom) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      gradient(context, ix1, iy1, ix2, iy2, top.hashCode(), bottom.hashCode(), bottom.hashCode(), top.hashCode());
   }

   public static void shadow(DrawContext context, float x1, float y1, float x2, float y2, int radius, int alpha) {
      if (radius > 0 && alpha > 0) {
         int ix1 = Math.round(x1);
         int iy1 = Math.round(y1);
         int ix2 = Math.round(x2);
         int iy2 = Math.round(y2);
         int solid = Math.min(255, alpha) << 24;
         int clear = 0;
         gradient(context, ix1, iy1 - radius, ix2, iy1, clear, solid, solid, clear);
         gradient(context, ix1, iy2, ix2, iy2 + radius, solid, clear, clear, solid);
         gradient(context, ix1 - radius, iy1, ix1, iy2, clear, clear, solid, solid);
         gradient(context, ix2, iy1, ix2 + radius, iy2, solid, solid, clear, clear);
         int cornerAlpha = (int)(alpha * 0.7F) << 24;
         gradient(context, ix1 - radius, iy1 - radius, ix1, iy1, clear, clear, cornerAlpha, clear);
         gradient(context, ix2, iy1 - radius, ix2 + radius, iy1, clear, clear, clear, cornerAlpha);
         gradient(context, ix1 - radius, iy2, ix1, iy2 + radius, clear, cornerAlpha, clear, clear);
         gradient(context, ix2, iy2, ix2 + radius, iy2 + radius, cornerAlpha, clear, clear, clear);
      }
   }

   public static void gradient(DrawContext graphics, int x1, int y1, int x2, int y2, int topLeft, int bottomLeft, int bottomRight, int topRight) {
      graphics.state
         .addSimpleElement(
            new RectRenderState(
               RenderPipelines.GUI,
               TextureSetup.empty(),
               new Matrix3x2f(graphics.getMatrices()),
               x1,
               y1,
               x2,
               y2,
               GuiFade.apply(topLeft),
               GuiFade.apply(bottomLeft),
               GuiFade.apply(bottomRight),
               GuiFade.apply(topRight),
               graphics.scissorStack.peekLast()
            )
         );
   }

   public static void rect(MatrixStack stack, float x1, float y1, float x2, float y2, int color) {
      rectFilled(stack, x1, y1, x2, y2, color);
   }

   public static void rect(MatrixStack stack, float x1, float y1, float x2, float y2, int color, float width) {
      drawHorizontalLine(stack, x1, x2, y1, color, width);
      drawVerticalLine(stack, x2, y1, y2, color, width);
      drawHorizontalLine(stack, x1, x2, y2, color, width);
      drawVerticalLine(stack, x1, y1, y2, color, width);
   }

   protected static void drawHorizontalLine(MatrixStack matrices, float x1, float x2, float y, int color) {
      if (x2 < x1) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      rectFilled(matrices, x1, y, x2 + 1.0F, y + 1.0F, color);
   }

   protected static void drawVerticalLine(MatrixStack matrices, float x, float y1, float y2, int color) {
      if (y2 < y1) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      rectFilled(matrices, x, y1 + 1.0F, x + 1.0F, y2, color);
   }

   protected static void drawHorizontalLine(MatrixStack matrices, float x1, float x2, float y, int color, float width) {
      if (x2 < x1) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      rectFilled(matrices, x1, y, x2 + width, y + width, color);
   }

   protected static void drawVerticalLine(MatrixStack matrices, float x, float y1, float y2, int color, float width) {
      if (y2 < y1) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      rectFilled(matrices, x, y1 + width, x + width, y2, color);
   }

   public static void rectFilled(MatrixStack matrix, float x1, float y1, float x2, float y2, int color) {
      if (x1 < x2) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      if (y1 < y2) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      float f = (color >> 24 & 0xFF) / 255.0F;
      float g = (color >> 16 & 0xFF) / 255.0F;
      float h = (color >> 8 & 0xFF) / 255.0F;
      float j = (color & 0xFF) / 255.0F;
      BufferBuilder bufferBuilder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y2, 0.0F).color(g, h, j, f);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y2, 0.0F).color(g, h, j, f);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y1, 0.0F).color(g, h, j, f);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y1, 0.0F).color(g, h, j, f);
      Layers.quads().draw(bufferBuilder.end());
   }

   public static void horizontalGradient(MatrixStack matrix, float x1, float y1, float x2, float y2, Color left, Color right) {
      BufferBuilder bufferBuilder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y1, 0.0F)
         .color(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y2, 0.0F)
         .color(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y2, 0.0F)
         .color(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y1, 0.0F)
         .color(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);
      Layers.quads().draw(bufferBuilder.end());
   }

   public static void verticalGradient(MatrixStack matrix, float x1, float y1, float x2, float y2, Color top, Color bottom) {
      BufferBuilder bufferBuilder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y1, 0.0F)
         .color(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y2, 0.0F)
         .color(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y2, 0.0F)
         .color(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
      bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y1, 0.0F)
         .color(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);
      Layers.quads().draw(bufferBuilder.end());
   }

   public static void drawBoxFilled(MatrixStack stack, Box box, Color c) {
      GizmoDrawing.box(box, DrawStyle.filled(c.getRGB())).ignoreOcclusion();
   }

   public static void drawBoxFilled(MatrixStack stack, Vec3d vec, Color c) {
      drawBoxFilled(stack, Box.from(vec), c);
   }

   public static void drawBoxFilled(MatrixStack stack, BlockPos bp, Color c) {
      drawBoxFilled(stack, new Box(bp), c);
   }

   public static void drawBox(MatrixStack stack, Box box, Color c, float lineWidth) {
      GizmoDrawing.box(box, DrawStyle.stroked(c.getRGB(), lineWidth)).ignoreOcclusion();
   }

   public static void drawBox(MatrixStack stack, Vec3d vec, Color c, float lineWidth) {
      drawBox(stack, Box.from(vec), c, lineWidth);
   }

   public static void drawBox(MatrixStack stack, BlockPos bp, Color c, float lineWidth) {
      drawBox(stack, new Box(bp), c, lineWidth);
   }

   public static void drawLine(Vec3d from, Vec3d to, Color c, float lineWidth) {
      GizmoDrawing.line(from, to, c.getRGB(), lineWidth).ignoreOcclusion();
   }

   private static void worldVertex(BufferBuilder bb, Matrix4f m, Vec3d cam, double x, double y, double z, Color c) {
      bb.vertex(m, (float)(x - cam.x), (float)(y - cam.y), (float)(z - cam.z))
         .color(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, c.getAlpha() / 255.0F);
   }

   public static void quadHorizontal(MatrixStack stack, double x1, double z1, double x2, double z2, double y, Color c) {
      Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
      Matrix4f m = stack.peek().getPositionMatrix();
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      worldVertex(bb, m, cam, x1, y, z1, c);
      worldVertex(bb, m, cam, x2, y, z1, c);
      worldVertex(bb, m, cam, x2, y, z2, c);
      worldVertex(bb, m, cam, x1, y, z2, c);
      Layers.quads().draw(bb.end());
   }

   public static void gradientQuadVertical(MatrixStack stack, double x1, double z1, double x2, double z2, double yTop, double yBottom, Color top, Color bottom) {
      Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
      Matrix4f m = stack.peek().getPositionMatrix();
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      worldVertex(bb, m, cam, x1, yTop, z1, top);
      worldVertex(bb, m, cam, x2, yTop, z2, top);
      worldVertex(bb, m, cam, x2, yBottom, z2, bottom);
      worldVertex(bb, m, cam, x1, yBottom, z1, bottom);
      Layers.quads().draw(bb.end());
   }

   public static MatrixStack matrixFrom(Vec3d pos) {
      MatrixStack matrices = new MatrixStack();
      Camera camera = mc.gameRenderer.getCamera();
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
      matrices.translate(pos.getX() - camera.getCameraPos().x, pos.getY() - camera.getCameraPos().y, pos.getZ() - camera.getCameraPos().z);
      return matrices;
   }
}
