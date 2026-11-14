package dev.leonetic.util.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.class_11278;
import net.minecraft.class_11287;
import net.minecraft.class_279;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_9960;
import net.minecraft.class_9962;
import net.minecraft.class_11287.class_11288;
import net.minecraft.class_11287.class_11290;
import net.minecraft.class_9962.class_9966;
import net.minecraft.class_9962.class_9967;
import net.minecraft.class_9962.class_9968;

public final class HandShaderChain {
   private static final class_2960 SCREENQUAD = class_2960.method_60655("minecraft", "core/screenquad");
   private static final class_2960 DILATE_H_FSH = class_2960.method_60655("homovore", "post/hand_outline_h");
   private static final class_2960 OUTLINE_FSH = class_2960.method_60655("homovore", "post/hand_outline");
   private static final class_2960 GLOW_H_FSH = class_2960.method_60655("homovore", "post/hand_glow_h");
   private static final class_2960 OUTLINE_GLOW_FSH = class_2960.method_60655("homovore", "post/hand_outline_glow");
   private static final class_2960 DILATED = class_2960.method_60655("homovore", "hand_dilated");
   private static final class_2960 GLOW_H = class_2960.method_60655("homovore", "hand_glow_h");
   private static final class_2960 CHAIN_NAME = class_2960.method_60655("homovore", "hand_shader_runtime");
   private static final UniformWriter UNIFORM_WRITER = new UniformWriter();
   private static final float FILL_ALPHA = 0.35F;
   private static class_11278 projection;
   private static class_279 cached;
   private static int lastLineWidth = Integer.MIN_VALUE;
   private static boolean lastFill = false;
   private static int lastGlowRadius = Integer.MIN_VALUE;
   private static float lastGlowIntensity = Float.NaN;

   private HandShaderChain() {
   }

   public static class_279 get(boolean outline, int thickness, boolean fill, boolean glow, int glowRadius, float glowIntensity) {
      int lineWidth = outline ? Math.max(1, thickness) : 0;
      int glowR = glow ? Math.max(0, glowRadius) : 0;
      if (lineWidth == 0 && !fill && glowR == 0) {
         return null;
      }

      if (cached != null && lineWidth == lastLineWidth && fill == lastFill && glowR == lastGlowRadius && glowIntensity == lastGlowIntensity) {
         return cached;
      }

      if (cached != null && lastGlowRadius == glowR) {
         lastLineWidth = lineWidth;
         lastFill = fill;
         lastGlowRadius = glowR;
         lastGlowIntensity = glowIntensity;
         float fillA = fill ? 0.35F : 0.0F;
         Map<String, List<class_11287>> configs = new HashMap<>();
         List<class_11287> dilateConfig = List.of(integer(lineWidth));
         List<class_11287> glowConfig = List.of(integer(glowRadius));
         List<class_11287> outlineConfig;
         if (glowRadius > 0) {
            outlineConfig = List.of(flt(fillA), flt(1.0F), flt(glowIntensity), integer(lineWidth), integer(glowRadius));
         } else {
            outlineConfig = List.of(flt(fillA), flt(1.0F), integer(lineWidth));
         }

         configs.put("DilateConfig", dilateConfig);
         configs.put("OutlineConfig", outlineConfig);
         configs.put("GlowConfig", glowConfig);
         UNIFORM_WRITER.setUniforms(cached, configs);
         return cached;
      } else {
         class_279 rebuilt = build(lineWidth, fill, glowR, glowIntensity);
         if (rebuilt == null) {
            return cached;
         }

         if (cached != null) {
            cached.close();
         }

         cached = rebuilt;
         lastLineWidth = lineWidth;
         lastFill = fill;
         lastGlowRadius = glowR;
         lastGlowIntensity = glowIntensity;
         return cached;
      }
   }

   private static class_279 build(int lineWidth, boolean fill, int glowRadius, float glowIntensity) {
      try {
         if (projection == null) {
            projection = new class_11278("homovore_hand", 0.1F, 1000.0F, false);
         }

         float fillA = fill ? 0.35F : 0.0F;
         class_9967 dilateH = new class_9967(
            SCREENQUAD,
            DILATE_H_FSH,
            List.of(new class_9968("In", class_279.field_53104, false, false)),
            DILATED,
            Map.of("DilateConfig", List.of(integer(lineWidth)))
         );
         class_9962 config = glowRadius > 0 ? buildGlow(dilateH, fillA, lineWidth, glowRadius, glowIntensity) : buildPlain(dilateH, fillA, lineWidth);
         return class_279.method_1256(config, class_310.method_1551().method_1531(), class_9960.field_53902, CHAIN_NAME, projection);
      } catch (Exception e) {
         return null;
      }
   }

   private static class_9962 buildPlain(class_9967 dilateH, float fillA, int lineWidth) {
      List<class_11287> outlineConfig = List.of(flt(fillA), flt(1.0F), integer(lineWidth));
      class_9967 outline = new class_9967(
         SCREENQUAD,
         OUTLINE_FSH,
         List.of(new class_9968("In", DILATED, false, false), new class_9968("Orig", class_279.field_53104, false, false)),
         class_279.field_53104,
         Map.of("OutlineConfig", outlineConfig)
      );
      return new class_9962(Map.of(DILATED, new class_9966(Optional.empty(), Optional.empty(), false, 0)), List.of(dilateH, outline));
   }

   private static class_9962 buildGlow(class_9967 dilateH, float fillA, int lineWidth, int glowRadius, float glowIntensity) {
      class_9967 glowH = new class_9967(
         SCREENQUAD, GLOW_H_FSH, List.of(new class_9968("In", class_279.field_53104, false, false)), GLOW_H, Map.of("GlowConfig", List.of(integer(glowRadius)))
      );
      List<class_11287> outlineConfig = List.of(flt(fillA), flt(1.0F), flt(glowIntensity), integer(lineWidth), integer(glowRadius));
      class_9967 outline = new class_9967(
         SCREENQUAD,
         OUTLINE_GLOW_FSH,
         List.of(
            new class_9968("In", DILATED, false, false),
            new class_9968("Glow", GLOW_H, false, false),
            new class_9968("Orig", class_279.field_53104, false, false)
         ),
         class_279.field_53104,
         Map.of("OutlineConfig", outlineConfig)
      );
      return new class_9962(
         Map.of(DILATED, new class_9966(Optional.empty(), Optional.empty(), false, 0), GLOW_H, new class_9966(Optional.empty(), Optional.empty(), false, 0)),
         List.of(dilateH, glowH, outline)
      );
   }

   private static class_11287 flt(float v) {
      return new class_11288(v);
   }

   private static class_11287 integer(int v) {
      return new class_11290(v);
   }
}
