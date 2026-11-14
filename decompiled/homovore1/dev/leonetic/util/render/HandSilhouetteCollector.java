package dev.leonetic.util.render;

import java.util.List;
import net.minecraft.class_10017;
import net.minecraft.class_1058;
import net.minecraft.class_1087;
import net.minecraft.class_11785;
import net.minecraft.class_11791;
import net.minecraft.class_12075;
import net.minecraft.class_1921;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_3879;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4618;
import net.minecraft.class_5481;
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

public final class HandSilhouetteCollector implements class_11785 {
   private final class_4618 outline;
   private final int color;

   public HandSilhouetteCollector(class_4618 outline, int rgb) {
      this.outline = outline;
      this.color = 0xFF000000 | rgb;
   }

   public <S> void method_73490(
      class_3879<? super S> model,
      S state,
      class_4587 poseStack,
      class_1921 renderType,
      int light,
      int overlay,
      int tintColor,
      class_1058 sprite,
      int outlineColor,
      class_11792 crumbling
   ) {
      class_4588 consumer = this.buffer(renderType, sprite);
      if (consumer != null) {
         try {
            model.method_2819(state);
            model.method_62100(poseStack, consumer, light, overlay, tintColor);
         } catch (Exception var13) {
         }
      }
   }

   public void method_73494(
      class_630 part,
      class_4587 poseStack,
      class_1921 renderType,
      int light,
      int overlay,
      class_1058 sprite,
      boolean copyNormals,
      boolean fullBright,
      int tintColor,
      class_11792 crumbling,
      int outlineColor
   ) {
      class_4588 consumer = this.buffer(renderType, sprite);
      if (consumer != null) {
         try {
            part.method_22699(poseStack, consumer, light, overlay, tintColor);
         } catch (Exception var14) {
         }
      }
   }

   public void method_73480(
      class_4587 poseStack,
      class_811 context,
      int light,
      int overlay,
      int outlineColor,
      int[] tintLayers,
      List<class_777> quads,
      class_1921 renderType,
      class_10445 foil
   ) {
      class_4588 consumer = this.buffer(renderType, null);
      if (consumer != null) {
         class_4665 pose = poseStack.method_23760();

         for (class_777 quad : quads) {
            consumer.method_22919(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
         }
      }
   }

   private class_4588 buffer(class_1921 renderType, class_1058 sprite) {
      if (HandShaderRender.capturePaused) {
         return null;
      }

      class_4588 consumer;
      try {
         this.outline.method_23286(this.color);
         consumer = this.outline.method_73477(renderType);
      } catch (IllegalStateException noOutline) {
         return null;
      }

      return sprite != null ? sprite.method_24108(consumer) : consumer;
   }

   public void method_73479(class_4587 p, float f, List<class_11680> s) {
   }

   public void method_73482(class_4587 p, class_243 v, int i, class_2561 c, boolean b, int j, double d, class_12075 s) {
   }

   public void method_73478(class_4587 p, float f, float g, class_5481 t, boolean b, class_6415 m, int i, int j, int k, int l) {
   }

   public void method_73488(class_4587 p, class_10017 s, Quaternionf q) {
   }

   public void method_73486(class_4587 p, class_10018 s) {
   }

   public void method_73481(class_4587 p, class_2680 s, int i, int j, int k) {
   }

   public void method_73485(class_4587 p, class_11791 s) {
   }

   public void method_73484(class_4587 p, class_1921 r, class_1087 m, float f, float g, float h, int i, int j, int k) {
   }

   public void method_73483(class_4587 p, class_1921 r, class_11660 renderer) {
   }

   public void method_74315(class_11947 renderer) {
   }
}
