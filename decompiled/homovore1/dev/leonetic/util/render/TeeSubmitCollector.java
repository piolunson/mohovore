package dev.leonetic.util.render;

import java.util.List;
import net.minecraft.class_10017;
import net.minecraft.class_1058;
import net.minecraft.class_1087;
import net.minecraft.class_11659;
import net.minecraft.class_11785;
import net.minecraft.class_11791;
import net.minecraft.class_12075;
import net.minecraft.class_1921;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_3879;
import net.minecraft.class_4587;
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
import org.joml.Quaternionf;

public final class TeeSubmitCollector implements class_11659 {
   private final class_11659 primary;
   private final class_11785 secondary;
   private final class_11785 tee;

   public TeeSubmitCollector(class_11659 primary, class_11785 secondary) {
      this.primary = primary;
      this.secondary = secondary;
      this.tee = new TeeSubmitCollector.Tee(primary, secondary);
   }

   public class_11785 method_73529(int order) {
      return new TeeSubmitCollector.Tee(this.primary.method_73529(order), this.secondary);
   }

   public <S> void method_73490(class_3879<? super S> m, S s, class_4587 p, class_1921 r, int l, int o, int t, class_1058 sp, int oc, class_11792 c) {
      this.tee.method_73490(m, s, p, r, l, o, t, sp, oc, c);
   }

   public void method_73494(class_630 part, class_4587 p, class_1921 r, int l, int o, class_1058 sp, boolean cn, boolean fb, int t, class_11792 c, int oc) {
      this.tee.method_73494(part, p, r, l, o, sp, cn, fb, t, c, oc);
   }

   public void method_73480(class_4587 p, class_811 ctx, int l, int o, int oc, int[] tl, List<class_777> q, class_1921 r, class_10445 f) {
      this.tee.method_73480(p, ctx, l, o, oc, tl, q, r, f);
   }

   public void method_73479(class_4587 p, float f, List<class_11680> s) {
      this.primary.method_73479(p, f, s);
   }

   public void method_73482(class_4587 p, class_243 v, int i, class_2561 c, boolean b, int j, double d, class_12075 s) {
      this.primary.method_73482(p, v, i, c, b, j, d, s);
   }

   public void method_73478(class_4587 p, float f, float g, class_5481 t, boolean b, class_6415 m, int i, int j, int k, int l) {
      this.primary.method_73478(p, f, g, t, b, m, i, j, k, l);
   }

   public void method_73488(class_4587 p, class_10017 s, Quaternionf q) {
      this.primary.method_73488(p, s, q);
   }

   public void method_73486(class_4587 p, class_10018 s) {
      this.primary.method_73486(p, s);
   }

   public void method_73481(class_4587 p, class_2680 s, int i, int j, int k) {
      this.primary.method_73481(p, s, i, j, k);
   }

   public void method_73485(class_4587 p, class_11791 s) {
      this.primary.method_73485(p, s);
   }

   public void method_73484(class_4587 p, class_1921 r, class_1087 m, float f, float g, float h, int i, int j, int k) {
      this.primary.method_73484(p, r, m, f, g, h, i, j, k);
   }

   public void method_73483(class_4587 p, class_1921 r, class_11660 renderer) {
      this.primary.method_73483(p, r, renderer);
   }

   public void method_74315(class_11947 renderer) {
      this.primary.method_74315(renderer);
   }

   private record Tee(class_11785 primary, class_11785 secondary) implements class_11785 {
      public <S> void method_73490(class_3879<? super S> m, S s, class_4587 p, class_1921 r, int l, int o, int t, class_1058 sp, int oc, class_11792 c) {
         this.primary.method_73490(m, s, p, r, l, o, t, sp, oc, c);
         this.secondary.method_73490(m, s, p, r, l, o, t, sp, oc, c);
      }

      public void method_73494(class_630 part, class_4587 p, class_1921 r, int l, int o, class_1058 sp, boolean cn, boolean fb, int t, class_11792 c, int oc) {
         this.primary.method_73494(part, p, r, l, o, sp, cn, fb, t, c, oc);
         this.secondary.method_73494(part, p, r, l, o, sp, cn, fb, t, c, oc);
      }

      public void method_73480(class_4587 p, class_811 ctx, int l, int o, int oc, int[] tl, List<class_777> q, class_1921 r, class_10445 f) {
         this.primary.method_73480(p, ctx, l, o, oc, tl, q, r, f);
         this.secondary.method_73480(p, ctx, l, o, oc, tl, q, r, f);
      }

      public void method_73479(class_4587 p, float f, List<class_11680> s) {
         this.primary.method_73479(p, f, s);
      }

      public void method_73482(class_4587 p, class_243 v, int i, class_2561 c, boolean b, int j, double d, class_12075 s) {
         this.primary.method_73482(p, v, i, c, b, j, d, s);
      }

      public void method_73478(class_4587 p, float f, float g, class_5481 t, boolean b, class_6415 m, int i, int j, int k, int l) {
         this.primary.method_73478(p, f, g, t, b, m, i, j, k, l);
      }

      public void method_73488(class_4587 p, class_10017 s, Quaternionf q) {
         this.primary.method_73488(p, s, q);
      }

      public void method_73486(class_4587 p, class_10018 s) {
         this.primary.method_73486(p, s);
      }

      public void method_73481(class_4587 p, class_2680 s, int i, int j, int k) {
         this.primary.method_73481(p, s, i, j, k);
      }

      public void method_73485(class_4587 p, class_11791 s) {
         this.primary.method_73485(p, s);
      }

      public void method_73484(class_4587 p, class_1921 r, class_1087 m, float f, float g, float h, int i, int j, int k) {
         this.primary.method_73484(p, r, m, f, g, h, i, j, k);
      }

      public void method_73483(class_4587 p, class_1921 r, class_11660 renderer) {
         this.primary.method_73483(p, r, renderer);
      }

      public void method_74315(class_11947 renderer) {
         this.primary.method_74315(renderer);
      }
   }
}
