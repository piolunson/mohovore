package dev.leonetic.util.render;

import com.mojang.blaze3d.vertex.VertexFormat.class_5596;
import dev.leonetic.util.render.state.RectRenderState;
import dev.leonetic.util.traits.Util;
import java.awt.Color;
import net.minecraft.class_10799;
import net.minecraft.class_11231;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_332;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_7833;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

public class RenderUtil implements Util {
   public static void rect(class_332 context, float x1, float y1, float x2, float y2, int color) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      context.method_25294(ix1, iy1, ix2, iy2, GuiFade.apply(color));
   }

   public static void rect(class_332 context, float x1, float y1, float x2, float y2, int color, float width) {
      int w = Math.max(1, Math.round(width));
      int c = GuiFade.apply(color);
      context.method_25294(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y1) + w, c);
      context.method_25294(Math.round(x2) - w, Math.round(y1), Math.round(x2), Math.round(y2), c);
      context.method_25294(Math.round(x1), Math.round(y2) - w, Math.round(x2), Math.round(y2), c);
      context.method_25294(Math.round(x1), Math.round(y1), Math.round(x1) + w, Math.round(y2), c);
   }

   public static void horizontalGradient(class_332 context, float x1, float y1, float x2, float y2, Color left, Color right) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      gradient(context, ix1, iy1, ix2, iy2, left.hashCode(), left.hashCode(), right.hashCode(), right.hashCode());
   }

   public static void verticalGradient(class_332 context, float x1, float y1, float x2, float y2, Color top, Color bottom) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      gradient(context, ix1, iy1, ix2, iy2, top.hashCode(), bottom.hashCode(), bottom.hashCode(), top.hashCode());
   }

   public static void shadow(class_332 context, float x1, float y1, float x2, float y2, int radius, int alpha) {
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

   public static void gradient(class_332 graphics, int x1, int y1, int x2, int y2, int topLeft, int bottomLeft, int bottomRight, int topRight) {
      graphics.field_59826
         .method_70919(
            new RectRenderState(
               class_10799.field_56879,
               class_11231.method_70899(),
               new Matrix3x2f(graphics.method_51448()),
               x1,
               y1,
               x2,
               y2,
               GuiFade.apply(topLeft),
               GuiFade.apply(bottomLeft),
               GuiFade.apply(bottomRight),
               GuiFade.apply(topRight),
               graphics.field_44659.method_70863()
            )
         );
   }

   public static void rect(class_4587 stack, float x1, float y1, float x2, float y2, int color) {
      rectFilled(stack, x1, y1, x2, y2, color);
   }

   public static void rect(class_4587 stack, float x1, float y1, float x2, float y2, int color, float width) {
      drawHorizontalLine(stack, x1, x2, y1, color, width);
      drawVerticalLine(stack, x2, y1, y2, color, width);
      drawHorizontalLine(stack, x1, x2, y2, color, width);
      drawVerticalLine(stack, x1, y1, y2, color, width);
   }

   protected static void drawHorizontalLine(class_4587 matrices, float x1, float x2, float y, int color) {
      if (x2 < x1) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      rectFilled(matrices, x1, y, x2 + 1.0F, y + 1.0F, color);
   }

   protected static void drawVerticalLine(class_4587 matrices, float x, float y1, float y2, int color) {
      if (y2 < y1) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      rectFilled(matrices, x, y1 + 1.0F, x + 1.0F, y2, color);
   }

   protected static void drawHorizontalLine(class_4587 matrices, float x1, float x2, float y, int color, float width) {
      if (x2 < x1) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      rectFilled(matrices, x1, y, x2 + width, y + width, color);
   }

   protected static void drawVerticalLine(class_4587 matrices, float x, float y1, float y2, int color, float width) {
      if (y2 < y1) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      rectFilled(matrices, x, y1 + width, x + width, y2, color);
   }

   public static void rectFilled(class_4587 matrix, float x1, float y1, float x2, float y2, int color) {
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
      class_287 bufferBuilder = class_289.method_1348().method_60827(class_5596.field_27382, class_290.field_1576);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x1, y2, 0.0F).method_22915(g, h, j, f);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x2, y2, 0.0F).method_22915(g, h, j, f);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x2, y1, 0.0F).method_22915(g, h, j, f);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x1, y1, 0.0F).method_22915(g, h, j, f);
      Layers.quads().method_60895(bufferBuilder.method_60800());
   }

   public static void horizontalGradient(class_4587 matrix, float x1, float y1, float x2, float y2, Color left, Color right) {
      class_287 bufferBuilder = class_289.method_1348().method_60827(class_5596.field_27382, class_290.field_1576);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x1, y1, 0.0F)
         .method_22915(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x1, y2, 0.0F)
         .method_22915(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x2, y2, 0.0F)
         .method_22915(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x2, y1, 0.0F)
         .method_22915(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);
      Layers.quads().method_60895(bufferBuilder.method_60800());
   }

   public static void verticalGradient(class_4587 matrix, float x1, float y1, float x2, float y2, Color top, Color bottom) {
      class_287 bufferBuilder = class_289.method_1348().method_60827(class_5596.field_27382, class_290.field_1576);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x1, y1, 0.0F)
         .method_22915(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x1, y2, 0.0F)
         .method_22915(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x2, y2, 0.0F)
         .method_22915(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
      bufferBuilder.method_22918(matrix.method_23760().method_23761(), x2, y1, 0.0F)
         .method_22915(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);
      Layers.quads().method_60895(bufferBuilder.method_60800());
   }

   public static void drawBoxFilled(class_4587 stack, class_238 box, Color c) {
      class_12180.method_75541(box, class_12179.method_75539(c.getRGB())).method_75533();
   }

   public static void drawBoxFilled(class_4587 stack, class_243 vec, Color c) {
      drawBoxFilled(stack, class_238.method_29968(vec), c);
   }

   public static void drawBoxFilled(class_4587 stack, class_2338 bp, Color c) {
      drawBoxFilled(stack, new class_238(bp), c);
   }

   public static void drawBox(class_4587 stack, class_238 box, Color c, float lineWidth) {
      class_12180.method_75541(box, class_12179.method_75536(c.getRGB(), lineWidth)).method_75533();
   }

   public static void drawBox(class_4587 stack, class_243 vec, Color c, float lineWidth) {
      drawBox(stack, class_238.method_29968(vec), c, lineWidth);
   }

   public static void drawBox(class_4587 stack, class_2338 bp, Color c, float lineWidth) {
      drawBox(stack, new class_238(bp), c, lineWidth);
   }

   public static void drawLine(class_243 from, class_243 to, Color c, float lineWidth) {
      class_12180.method_75546(from, to, c.getRGB(), lineWidth).method_75533();
   }

   private static void worldVertex(class_287 bb, Matrix4f m, class_243 cam, double x, double y, double z, Color c) {
      bb.method_22918(m, (float)(x - cam.field_1352), (float)(y - cam.field_1351), (float)(z - cam.field_1350))
         .method_22915(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, c.getAlpha() / 255.0F);
   }

   public static void quadHorizontal(class_4587 stack, double x1, double z1, double x2, double z2, double y, Color c) {
      class_243 cam = mc.field_1773.method_19418().method_71156();
      Matrix4f m = stack.method_23760().method_23761();
      class_287 bb = class_289.method_1348().method_60827(class_5596.field_27382, class_290.field_1576);
      worldVertex(bb, m, cam, x1, y, z1, c);
      worldVertex(bb, m, cam, x2, y, z1, c);
      worldVertex(bb, m, cam, x2, y, z2, c);
      worldVertex(bb, m, cam, x1, y, z2, c);
      Layers.quads().method_60895(bb.method_60800());
   }

   public static void gradientQuadVertical(class_4587 stack, double x1, double z1, double x2, double z2, double yTop, double yBottom, Color top, Color bottom) {
      class_243 cam = mc.field_1773.method_19418().method_71156();
      Matrix4f m = stack.method_23760().method_23761();
      class_287 bb = class_289.method_1348().method_60827(class_5596.field_27382, class_290.field_1576);
      worldVertex(bb, m, cam, x1, yTop, z1, top);
      worldVertex(bb, m, cam, x2, yTop, z2, top);
      worldVertex(bb, m, cam, x2, yBottom, z2, bottom);
      worldVertex(bb, m, cam, x1, yBottom, z1, bottom);
      Layers.quads().method_60895(bb.method_60800());
   }

   public static class_4587 matrixFrom(class_243 pos) {
      class_4587 matrices = new class_4587();
      class_4184 camera = mc.field_1773.method_19418();
      matrices.method_22907(class_7833.field_40714.rotationDegrees(camera.method_19329()));
      matrices.method_22907(class_7833.field_40716.rotationDegrees(camera.method_19330() + 180.0F));
      matrices.method_22904(
         pos.method_10216() - camera.method_71156().field_1352,
         pos.method_10214() - camera.method_71156().field_1351,
         pos.method_10215() - camera.method_71156().field_1350
      );
      return matrices;
   }
}
