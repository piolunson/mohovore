package dev.leonetic.util.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

public final class WorldChamsChain {
   private static final Identifier SCREENQUAD = Identifier.of("minecraft", "core/screenquad");
   private static final Identifier H_FSH = Identifier.of("homovore", "post/chams_default_h");
   private static final Identifier C_FSH = Identifier.of("homovore", "post/chams_default_c");
   private static final Identifier V_FSH = Identifier.of("homovore", "post/chams_default_v");
   private static final Identifier CHAMS_H = Identifier.of("homovore", "chams_h");
   private static final Identifier CHAMS_C = Identifier.of("homovore", "chams_c");
   private static final Identifier CHAIN_NAME = Identifier.of("homovore", "chams_default_runtime");
   private static final UniformWriter UNIFORM_WRITER = new UniformWriter();
   private static final int LINE_WIDTH = 2;
   private static ProjectionMatrix2 projection;
   private static PostEffectProcessor cached;
   private static int lastGlowRadius = Integer.MIN_VALUE;
   private static float lastGlowIntensity = Float.NaN;
   private static float lastFillTint = Float.NaN;
   private static float lastFillAlpha = Float.NaN;

   private WorldChamsChain() {
   }

   public static PostEffectProcessor get(Set<Identifier> externalTargets, int glowRadius, float glowIntensity, float fillTint, float fillAlpha) {
      if (cached != null && glowRadius == lastGlowRadius && glowIntensity == lastGlowIntensity && fillTint == lastFillTint && fillAlpha == lastFillAlpha) {
         return cached;
      }

      if (cached != null) {
         lastGlowRadius = glowRadius;
         lastGlowIntensity = glowIntensity;
         lastFillTint = fillTint;
         lastFillAlpha = fillAlpha;
         Map<String, List<UniformValue>> configs = new HashMap<>();
         List<UniformValue> dilateUniforms = List.of(integer(2), integer(glowRadius));
         List<UniformValue> colorUniforms = List.of(integer(2), integer(glowRadius));
         List<UniformValue> chamsUniforms = List.of(flt(glowIntensity), flt(fillTint), flt(fillAlpha), integer(glowRadius), integer(2));
         configs.put("DilateConfig", dilateUniforms);
         configs.put("ColorConfig", colorUniforms);
         configs.put("ChamsConfig", chamsUniforms);
         UNIFORM_WRITER.setUniforms(cached, configs);
         return cached;
      }

      PostEffectProcessor rebuilt = build(externalTargets, glowRadius, glowIntensity, fillTint, fillAlpha);
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

   private static PostEffectProcessor build(Set<Identifier> externalTargets, int glowThickness, float glowIntensity, float fillTint, float fillAlpha) {
      try {
         if (projection == null) {
            projection = new ProjectionMatrix2("homovore_chams", 0.1F, 1000.0F, false);
         }

         Identifier outlineTarget = DefaultFramebufferSet.ENTITY_OUTLINE;
         Pass passH = new Pass(
            SCREENQUAD,
            H_FSH,
            List.of(new TargetSampler("In", outlineTarget, false, false)),
            CHAMS_H,
            Map.of("DilateConfig", List.of(integer(2), integer(glowThickness)))
         );
         Pass passC = new Pass(
            SCREENQUAD,
            C_FSH,
            List.of(new TargetSampler("In", outlineTarget, false, false)),
            CHAMS_C,
            Map.of("ColorConfig", List.of(integer(2), integer(glowThickness)))
         );
         List<UniformValue> chamsConfig = List.of(flt(glowIntensity), flt(fillTint), flt(fillAlpha), integer(glowThickness), integer(2));
         Pass passV = new Pass(
            SCREENQUAD,
            V_FSH,
            List.of(
               new TargetSampler("In", CHAMS_H, false, false),
               new TargetSampler("Color", CHAMS_C, false, false),
               new TargetSampler("Orig", outlineTarget, false, false)
            ),
            outlineTarget,
            Map.of("ChamsConfig", chamsConfig)
         );
         PostEffectPipeline config = new PostEffectPipeline(
            Map.of(CHAMS_H, new Targets(Optional.empty(), Optional.empty(), false, 0), CHAMS_C, new Targets(Optional.empty(), Optional.empty(), false, 0)),
            List.of(passH, passC, passV)
         );
         return PostEffectProcessor.parseEffect(config, MinecraftClient.getInstance().getTextureManager(), externalTargets, CHAIN_NAME, projection);
      } catch (Exception e) {
         return null;
      }
   }

   private static UniformValue flt(float v) {
      return new FloatValue(v);
   }

   private static UniformValue integer(int v) {
      return new IntValue(v);
   }
}
