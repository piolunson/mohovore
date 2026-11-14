package dev.leonetic.util.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.class_11231;
import net.minecraft.class_11244;
import net.minecraft.class_4588;
import net.minecraft.class_8030;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record RectRenderState(
   RenderPipeline pipeline,
   class_11231 textureSetup,
   Matrix3x2f pose,
   int x0,
   int y0,
   int x1,
   int y1,
   int topLeft,
   int bottomLeft,
   int bottomRight,
   int topRight,
   @Nullable class_8030 scissorArea,
   @Nullable class_8030 bounds
) implements class_11244 {
   public RectRenderState(
      RenderPipeline renderPipeline,
      class_11231 textureSetup,
      Matrix3x2f matrix3x2f,
      int x0,
      int y0,
      int x1,
      int y1,
      int topLeft,
      int bottomLeft,
      int bottomRight,
      int topRight,
      @Nullable class_8030 screenRectangle
   ) {
      this(
         renderPipeline,
         textureSetup,
         matrix3x2f,
         x0,
         y0,
         x1,
         y1,
         topLeft,
         bottomLeft,
         bottomRight,
         topRight,
         screenRectangle,
         getBounds(x0, y0, x1, y1, matrix3x2f, screenRectangle)
      );
   }

   public void method_70917(class_4588 vertexConsumer) {
      vertexConsumer.method_70815(this.pose(), this.x0(), this.y0()).method_39415(this.topLeft());
      vertexConsumer.method_70815(this.pose(), this.x0(), this.y1()).method_39415(this.bottomLeft());
      vertexConsumer.method_70815(this.pose(), this.x1(), this.y1()).method_39415(this.bottomRight());
      vertexConsumer.method_70815(this.pose(), this.x1(), this.y0()).method_39415(this.topRight());
   }

   @Nullable
   private static class_8030 getBounds(int i, int j, int k, int l, Matrix3x2f matrix3x2f, @Nullable class_8030 screenRectangle) {
      class_8030 screenRectangle2 = new class_8030(i, j, k - i, l - j).method_71523(matrix3x2f);
      return screenRectangle != null ? screenRectangle.method_49701(screenRectangle2) : screenRectangle2;
   }

   public RenderPipeline comp_4055() {
      return this.pipeline;
   }

   public class_11231 comp_4056() {
      return this.textureSetup;
   }

   @Nullable
   public class_8030 comp_4069() {
      return this.scissorArea;
   }

   @Nullable
   public class_8030 comp_4274() {
      return this.bounds;
   }
}
