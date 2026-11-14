package dev.leonetic.util.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

public final class WorldChamsChain {
   private static final class_2960 SCREENQUAD = class_2960.method_60655("minecraft", "core/screenquad");
   private static final class_2960 H_FSH = class_2960.method_60655("homovore", "post/chams_default_h");
   private static final class_2960 C_FSH = class_2960.method_60655("homovore", "post/chams_default_c");
   private static final class_2960 V_FSH = class_2960.method_60655("homovore", "post/chams_default_v");
   private static final class_2960 CHAMS_H = class_2960.method_60655("homovore", "chams_h");
   private static final class_2960 CHAMS_C = class_2960.method_60655("homovore", "chams_c");
   private static final class_2960 CHAIN_NAME = class_2960.method_60655("homovore", "chams_default_runtime");
   private static final UniformWriter UNIFORM_WRITER = new UniformWriter();
   private static final int LINE_WIDTH = 2;
   private static class_11278 projection;
   private static class_279 cached;
   private static int lastGlowRadius = Integer.MIN_VALUE;
   private static float lastGlowIntensity = Float.NaN;
   private static float lastFillTint = Float.NaN;
   private static float lastFillAlpha = Float.NaN;

   private WorldChamsChain() {
   }

   public static class_279 get(Set<class_2960> externalTargets, int glowRadius, float glowIntensity, float fillTint, float fillAlpha) {
      if (cached != null && glowRadius == lastGlowRadius && glowIntensity == lastGlowIntensity && fillTint == lastFillTint && fillAlpha == lastFillAlpha) {
         return cached;
      }

      if (cached != null) {
         lastGlowRadius = glowRadius;
         lastGlowIntensity = glowIntensity;
         lastFillTint = fillTint;
         lastFillAlpha = fillAlpha;
         Map<String, List<class_11287>> configs = new HashMap<>();
         List<class_11287> dilateUniforms = List.of(integer(2), integer(glowRadius));
         List<class_11287> colorUniforms = List.of(integer(2), integer(glowRadius));
         List<class_11287> chamsUniforms = List.of(flt(glowIntensity), flt(fillTint), flt(fillAlpha), integer(glowRadius), integer(2));
         configs.put("DilateConfig", dilateUniforms);
         configs.put("ColorConfig", colorUniforms);
         configs.put("ChamsConfig", chamsUniforms);
         UNIFORM_WRITER.setUniforms(cached, configs);
         return cached;
      }

      class_279 rebuilt = build(externalTargets, glowRadius, glowIntensity, fillTint, fillAlpha);
      if (rebuilt == null) {
         return cached;
      }

      if (cached != null) {
         cached.close();
      }

      cached = rebuilt;
      lastGlowRadius = glowRadius;
      lastGlowIntensity = glowIntensity;
      lastFillTint = fillTint;
      lastFillAlpha = fillAlpha;
      return cached;
   }

   private static class_279 build(Set<class_2960> externalTargets, int glowThickness, float glowIntensity, float fillTint, float fillAlpha) {
      try {
         if (projection == null) {
            projection = new class_11278("homovore_chams", 0.1F, 1000.0F, false);
         }

         class_2960 outlineTarget = class_9960.field_53089;
         class_9967 passH = new class_9967(
            SCREENQUAD,
            H_FSH,
            List.of(new class_9968("In", outlineTarget, false, false)),
            CHAMS_H,
            Map.of("DilateConfig", List.of(integer(2), integer(glowThickness)))
         );
         class_9967 passC = new class_9967(
            SCREENQUAD,
            C_FSH,
            List.of(new class_9968("In", outlineTarget, false, false)),
            CHAMS_C,
            Map.of("ColorConfig", List.of(integer(2), integer(glowThickness)))
         );
         List<class_11287> chamsConfig = List.of(flt(glowIntensity), flt(fillTint), flt(fillAlpha), integer(glowThickness), integer(2));
         class_9967 passV = new class_9967(
            SCREENQUAD,
            V_FSH,
            List.of(
               new class_9968("In", CHAMS_H, false, false), new class_9968("Color", CHAMS_C, false, false), new class_9968("Orig", outlineTarget, false, false)
            ),
            outlineTarget,
            Map.of("ChamsConfig", chamsConfig)
         );
         class_9962 config = new class_9962(
            Map.of(CHAMS_H, new class_9966(Optional.empty(), Optional.empty(), false, 0), CHAMS_C, new class_9966(Optional.empty(), Optional.empty(), false, 0)),
            List.of(passH, passC, passV)
         );
         return class_279.method_1256(config, class_310.method_1551().method_1531(), externalTargets, CHAIN_NAME, projection);
      } catch (Exception e) {
         return null;
      }
   }

   private static class_11287 flt(float v) {
      return new class_11288(v);
   }

   private static class_11287 integer(int v) {
      return new class_11290(v);
   }
}
