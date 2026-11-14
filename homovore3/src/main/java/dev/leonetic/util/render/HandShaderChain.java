package dev.leonetic.util.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.gl.PostEffectPipeline.Pass;
import net.minecraft.client.gl.PostEffectPipeline.TargetSampler;
import net.minecraft.client.gl.PostEffectPipeline.Targets;
import net.minecraft.client.gl.UniformValue.FloatValue;
import net.minecraft.client.gl.UniformValue.IntValue;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.util.Identifier;

public final class HandShaderChain {
   private static final Identifier SCREENQUAD = Identifier.of("minecraft", "core/screenquad");
   private static final Identifier DILATE_H_FSH = Identifier.of("homovore", "post/hand_outline_h");
   private static final Identifier OUTLINE_FSH = Identifier.of("homovore", "post/hand_outline");
   private static final Identifier GLOW_H_FSH = Identifier.of("homovore", "post/hand_glow_h");
   private static final Identifier OUTLINE_GLOW_FSH = Identifier.of("homovore", "post/hand_outline_glow");
   private static final Identifier DILATED = Identifier.of("homovore", "hand_dilated");
   private static final Identifier GLOW_H = Identifier.of("homovore", "hand_glow_h");
   private static final Identifier CHAIN_NAME = Identifier.of("homovore", "hand_shader_runtime");
   private static final UniformWriter UNIFORM_WRITER = new UniformWriter();
   private static final float FILL_ALPHA = 0.35F;
   private static ProjectionMatrix2 projection;
   private static PostEffectProcessor cached;
   private static int lastLineWidth = Integer.MIN_VALUE;
   private static boolean lastFill = false;
   private static int lastGlowRadius = Integer.MIN_VALUE;
   private static float lastGlowIntensity = Float.NaN;

   private HandShaderChain() {
   }

   public static PostEffectProcessor get(boolean outline, int thickness, boolean fill, boolean glow, int glowRadius, float glowIntensity) {
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
         Map<String, List<UniformValue>> configs = new HashMap<>();
         List<UniformValue> dilateConfig = List.of(integer(lineWidth));
         List<UniformValue> glowConfig = List.of(integer(glowRadius));
         List<UniformValue> outlineConfig;
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
         PostEffectProcessor rebuilt = build(lineWidth, fill, glowR, glowIntensity);
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

   private static PostEffectProcessor build(int lineWidth, boolean fill, int glowRadius, float glowIntensity) {
      try {
         if (projection == null) {
            projection = new ProjectionMatrix2("homovore_hand", 0.1F, 1000.0F, false);
         }

         float fillA = fill ? 0.35F : 0.0F;
         Pass dilateH = new Pass(
            SCREENQUAD,
            DILATE_H_FSH,
            List.of(new TargetSampler("In", PostEffectProcessor.MAIN, false, false)),
            DILATED,
            Map.of("DilateConfig", List.of(integer(lineWidth)))
         );
         PostEffectPipeline config = glowRadius > 0 ? buildGlow(dilateH, fillA, lineWidth, glowRadius, glowIntensity) : buildPlain(dilateH, fillA, lineWidth);
         return PostEffectProcessor.parseEffect(
            config, MinecraftClient.getInstance().getTextureManager(), DefaultFramebufferSet.MAIN_ONLY, CHAIN_NAME, projection
         );
      } catch (Exception e) {
         return null;
      }
   }

   private static PostEffectPipeline buildPlain(Pass dilateH, float fillA, int lineWidth) {
      List<UniformValue> outlineConfig = List.of(flt(fillA), flt(1.0F), integer(lineWidth));
      Pass outline = new Pass(
         SCREENQUAD,
         OUTLINE_FSH,
         List.of(new TargetSampler("In", DILATED, false, false), new TargetSampler("Orig", PostEffectProcessor.MAIN, false, false)),
         PostEffectProcessor.MAIN,
         Map.of("OutlineConfig", outlineConfig)
      );
      return new PostEffectPipeline(Map.of(DILATED, new Targets(Optional.empty(), Optional.empty(), false, 0)), List.of(dilateH, outline));
   }

   private static PostEffectPipeline buildGlow(Pass dilateH, float fillA, int lineWidth, int glowRadius, float glowIntensity) {
      Pass glowH = new Pass(
         SCREENQUAD,
         GLOW_H_FSH,
         List.of(new TargetSampler("In", PostEffectProcessor.MAIN, false, false)),
         GLOW_H,
         Map.of("GlowConfig", List.of(integer(glowRadius)))
      );
      List<UniformValue> outlineConfig = List.of(flt(fillA), flt(1.0F), flt(glowIntensity), integer(lineWidth), integer(glowRadius));
      Pass outline = new Pass(
         SCREENQUAD,
         OUTLINE_GLOW_FSH,
         List.of(
            new TargetSampler("In", DILATED, false, false),
            new TargetSampler("Glow", GLOW_H, false, false),
            new TargetSampler("Orig", PostEffectProcessor.MAIN, false, false)
         ),
         PostEffectProcessor.MAIN,
         Map.of("OutlineConfig", outlineConfig)
      );
      return new PostEffectPipeline(
         Map.of(DILATED, new Targets(Optional.empty(), Optional.empty(), false, 0), GLOW_H, new Targets(Optional.empty(), Optional.empty(), false, 0)),
         List.of(dilateH, glowH, outline)
      );
   }

   private static UniformValue flt(float v) {
      return new FloatValue(v);
   }

   private static UniformValue integer(int v) {
      return new IntValue(v);
   }
}
