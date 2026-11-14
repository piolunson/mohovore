package dev.leonetic.util.render;

import com.mojang.blaze3d.vertex.VertexFormat.class_5596;
import dev.leonetic.util.traits.Util;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_10017;
import net.minecraft.class_1058;
import net.minecraft.class_1087;
import net.minecraft.class_11659;
import net.minecraft.class_11785;
import net.minecraft.class_11791;
import net.minecraft.class_12075;
import net.minecraft.class_1297;
import net.minecraft.class_1921;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_3879;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_5481;
import net.minecraft.class_572;
import net.minecraft.class_591;
import net.minecraft.class_630;
import net.minecraft.class_777;
import net.minecraft.class_811;
import net.minecraft.class_10017.class_10018;
import net.minecraft.class_10017.class_11680;
import net.minecraft.class_10444.class_10445;
import net.minecraft.class_11659.class_11660;
import net.minecraft.class_11659.class_11947;
import net.minecraft.class_11683.class_11792;
import net.minecraft.class_327.class_6415;
import net.minecraft.class_4587.class_4665;
import org.joml.Quaternionf;

public final class WireframeEntityRenderer implements Util {
   private static final int FULL_BRIGHT = 15728880;

   private WireframeEntityRenderer() {
   }

   public static List<float[][]> render(
      class_4587 eventStack, class_1297 entity, class_243 pos, List<float[][]> quads, float partialTick, Color sideColor, Color lineColor, float lineWidth
   ) {
      if (quads == null) {
         quads = captureGeometry(entity, partialTick);
      }

      if (quads.isEmpty()) {
         return quads;
      }

      class_243 cam = mc.method_1561().field_4686.method_71156();
      float dx = (float)(pos.field_1352 - cam.method_10216());
      float dy = (float)(pos.field_1351 - cam.method_10214());
      float dz = (float)(pos.field_1350 - cam.method_10215());
      eventStack.method_22903();
      eventStack.method_46416(dx, dy, dz);
      class_4665 pose = eventStack.method_23760();
      if (sideColor.getAlpha() > 0) {
         class_287 fill = class_289.method_1348().method_60827(class_5596.field_27382, class_290.field_1576);

         for (float[][] q : quads) {
            for (float[] v : q) {
               fill.method_56824(pose, v[0], v[1], v[2]).method_39415(sideColor.getRGB());
            }
         }

         Layers.quads().method_60895(fill.method_60800());
      }

      class_287 edges = class_289.method_1348().method_60827(class_5596.field_27377, class_290.field_63454);

      for (float[][] q : quads) {
         for (int i = 0; i < 4; i++) {
            float[] v1 = q[i];
            float[] v2 = q[(i + 1) % 4];
            edges.method_56824(pose, v1[0], v1[1], v1[2]).method_39415(lineColor.getRGB()).method_75298(lineWidth);
            edges.method_56824(pose, v2[0], v2[1], v2[2]).method_39415(lineColor.getRGB()).method_75298(lineWidth);
         }
      }

      Layers.lines().method_60895(edges.method_60800());
      eventStack.method_22909();
      return quads;
   }

   private static List<float[][]> captureGeometry(class_1297 entity, float partialTick) {
      try {
         class_10017 state = mc.method_1561().method_72977(entity, partialTick);
         class_12075 camera = new class_12075();
         camera.field_63078 = class_243.field_1353;
         camera.field_63080 = class_243.field_1353;
         camera.field_63077 = class_2338.field_10980;
         camera.field_63081 = new Quaternionf();
         camera.field_63079 = true;
         WireframeEntityRenderer.CapturingCollector collector = new WireframeEntityRenderer.CapturingCollector();
         mc.method_1561().method_72976(state, camera, 0.0, 0.0, 0.0, new class_4587(), collector);
         return collector.consumer.quads;
      } catch (Exception ignored) {
         return new ArrayList<>();
      }
   }

   private static final class CapturingCollector implements class_11659 {
      final WireframeEntityRenderer.CapturingConsumer consumer = new WireframeEntityRenderer.CapturingConsumer();

      public class_11785 method_73529(int order) {
         return this;
      }

      public <S> void method_73490(
         class_3879<? super S> model,
         S modelState,
         class_4587 poseStack,
         class_1921 renderType,
         int packedLight,
         int overlay,
         int blockPosition,
         class_1058 sprite,
         int flags,
         class_11792 crumbling
      ) {
         if (model instanceof class_572<?> humanoid) {
            this.renderFirstLayerOnly(humanoid, poseStack, packedLight, overlay);
         } else {
            model.method_60879(poseStack, this.consumer, packedLight, overlay);
         }
      }

      public void method_73494(
         class_630 part,
         class_4587 poseStack,
         class_1921 renderType,
         int packedLight,
         int overlay,
         class_1058 sprite,
         boolean copyNormals,
         boolean fullBright,
         int flags,
         class_11792 crumbling,
         int data
      ) {
         part.method_22698(poseStack, this.consumer, packedLight, overlay);
      }

      private void renderFirstLayerOnly(class_572<?> model, class_4587 poseStack, int light, int overlay) {
         boolean hatVis = model.field_3394.field_3665;
         model.field_3394.field_3665 = false;
         boolean jacketVis = false;
         boolean lSleeveVis = false;
         boolean rSleeveVis = false;
         boolean lPantsVis = false;
         boolean rPantsVis = false;
         if (model instanceof class_591 pm) {
            jacketVis = pm.field_3483.field_3665;
            pm.field_3483.field_3665 = false;
            lSleeveVis = pm.field_3484.field_3665;
            pm.field_3484.field_3665 = false;
            rSleeveVis = pm.field_3486.field_3665;
            pm.field_3486.field_3665 = false;
            lPantsVis = pm.field_3482.field_3665;
            pm.field_3482.field_3665 = false;
            rPantsVis = pm.field_3479.field_3665;
            pm.field_3479.field_3665 = false;
         }

         model.method_60879(poseStack, this.consumer, light, overlay);
         model.field_3394.field_3665 = hatVis;
         if (model instanceof class_591 pm) {
            pm.field_3483.field_3665 = jacketVis;
            pm.field_3484.field_3665 = lSleeveVis;
            pm.field_3486.field_3665 = rSleeveVis;
            pm.field_3482.field_3665 = lPantsVis;
            pm.field_3479.field_3665 = rPantsVis;
         }
      }

      public void method_73479(class_4587 p, float r, List<class_11680> s) {
      }

      public void method_73482(class_4587 p, class_243 pos, int bg, class_2561 text, boolean player, int color, double dist, class_12075 cam) {
      }

      public void method_73478(class_4587 p, float x, float y, class_5481 text, boolean shadow, class_6415 mode, int bg, int fg, int light, int lh) {
      }

      public void method_73488(class_4587 p, class_10017 s, Quaternionf q) {
      }

      public void method_73486(class_4587 p, class_10018 l) {
      }

      public void method_73481(class_4587 p, class_2680 b, int x, int y, int z) {
      }

      public void method_73485(class_4587 p, class_11791 m) {
      }

      public void method_73484(class_4587 p, class_1921 r, class_1087 m, float a, float b, float c, int l, int o, int f) {
      }

      public void method_73480(class_4587 p, class_811 ctx, int l1, int l2, int l3, int[] tints, List<class_777> quads, class_1921 r, class_10445 foil) {
      }

      public void method_73483(class_4587 p, class_1921 r, class_11660 geom) {
      }

      public void method_74315(class_11947 renderer) {
      }
   }

   private static final class CapturingConsumer implements class_4588 {
      final List<float[][]> quads = new ArrayList<>();
      private final float[][] pending = new float[4][];
      private int count = 0;

      private void pushVertex(float x, float y, float z) {
         this.pending[this.count++] = new float[]{x, y, z};
         if (this.count == 4) {
            this.quads
               .add(
                  new float[][]{
                     (float[])this.pending[0].clone(), (float[])this.pending[1].clone(), (float[])this.pending[2].clone(), (float[])this.pending[3].clone()
                  }
               );
            this.count = 0;
         }
      }

      public class_4588 method_22912(float x, float y, float z) {
         this.pushVertex(x, y, z);
         return this;
      }

      public class_4588 method_1336(int r, int g, int b, int a) {
         return this;
      }

      public class_4588 method_39415(int argb) {
         return this;
      }

      public class_4588 method_22913(float u, float v) {
         return this;
      }

      public class_4588 method_60796(int u, int v) {
         return this;
      }

      public class_4588 method_22921(int u, int v) {
         return this;
      }

      public class_4588 method_22914(float nx, float ny, float nz) {
         return this;
      }

      public class_4588 method_75298(float w) {
         return this;
      }
   }
}
