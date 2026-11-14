package dev.leonetic.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.leonetic.mixin.render.RenderSetupAccessor;
import dev.leonetic.mixin.render.RenderTypeAccessor;
import java.util.Locale;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.RenderSetup.Builder;
import net.minecraft.client.render.RenderSetup.TextureSpec;

public final class SeeThroughRender {
   public static final int SEED_ORDER_BASE = 100000;
   public static final int COLOR_ORDER_BASE = 200000;
   public static boolean active;
   public static boolean cloning;
   public static int cloneSubOrder;
   private static final ConcurrentHashMap<RenderLayer, RenderLayer> SEED_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<RenderLayer, RenderLayer> COLOR_CACHE = new ConcurrentHashMap<>();

   private SeeThroughRender() {
   }

   public static void begin() {
      active = true;
      cloneSubOrder = 0;
   }

   public static void end() {
      active = false;
   }

   public static boolean isGlintType(RenderLayer type) {
      if (type == null) {
         return false;
      }

      String name = type.toString().toLowerCase(Locale.ROOT);
      return name.contains("glint") || name.contains("foil");
   }

   public static RenderLayer wrapDepthSeed(RenderLayer original) {
      return original == null
         ? null
         : SEED_CACHE.computeIfAbsent(original, o -> build(o, "_homovore_seethrough_seed", DepthTestFunction.GREATER_DEPTH_TEST, true, false, Boolean.FALSE));
   }

   public static RenderLayer wrapColor(RenderLayer original) {
      return original == null
         ? null
         : COLOR_CACHE.computeIfAbsent(original, o -> build(o, "_homovore_seethrough_color", DepthTestFunction.LEQUAL_DEPTH_TEST, true, true, null));
   }

   private static RenderLayer build(
      RenderLayer original, String suffix, DepthTestFunction depthFunc, boolean depthWrite, boolean colorWrite, Boolean cullOverride
   ) {
      try {
         RenderSetup origSetup = ((RenderTypeAccessor)original).homovore$getState();
         RenderSetupAccessor sa = (RenderSetupAccessor)origSetup;
         RenderPipeline pipeline = clonePipeline(original.getRenderPipeline(), depthFunc, depthWrite, colorWrite, cullOverride);
         Builder b = RenderSetup.builder(pipeline);

         for (Entry<String, TextureSpec> e : sa.homovore$getTextures().entrySet()) {
            TextureSpec binding = e.getValue();
            if (binding.sampler() != null) {
               b.texture(e.getKey(), binding.location(), binding.sampler());
            } else {
               b.texture(e.getKey(), binding.location());
            }
         }

         if (sa.homovore$useLightmap()) {
            b.useLightmap();
         }

         if (sa.homovore$useOverlay()) {
            b.useOverlay();
         }

         if (sa.homovore$affectsCrumbling()) {
            b.crumbling();
         }

         if (sa.homovore$sortOnUpload()) {
            b.translucent();
         }

         b.expectedBufferSize(sa.homovore$getBufferSize());
         b.layeringTransform(sa.homovore$getLayeringTransform());
         b.textureTransform(sa.homovore$getTextureTransform());
         b.outlineMode(sa.homovore$getOutlineProperty());
         return RenderLayer.of(original.toString() + suffix, b.build());
      } catch (Throwable t) {
         return original;
      }
   }

   private static RenderPipeline clonePipeline(RenderPipeline orig, DepthTestFunction depthFunc, boolean depthWrite, boolean colorWrite, Boolean cullOverride) {
      com.mojang.blaze3d.pipeline.RenderPipeline.Builder b = RenderPipeline.builder(new Snippet[0])
         .withLocation(orig.getLocation())
         .withVertexShader(orig.getVertexShader())
         .withFragmentShader(orig.getFragmentShader())
         .withDepthTestFunction(depthFunc)
         .withPolygonMode(orig.getPolygonMode())
         .withCull(cullOverride != null ? cullOverride : orig.isCull())
         .withColorWrite(colorWrite && orig.isWriteColor(), colorWrite && orig.isWriteAlpha())
         .withDepthWrite(depthWrite)
         .withColorLogic(orig.getColorLogic())
         .withVertexFormat(orig.getVertexFormat(), orig.getVertexFormatMode())
         .withDepthBias(orig.getDepthBiasScaleFactor(), orig.getDepthBiasConstant());
      Optional<BlendFunction> blend = orig.getBlendFunction();
      if (blend.isPresent()) {
         b.withBlend(blend.get());
      } else {
         b.withoutBlend();
      }

      for (String s : orig.getSamplers()) {
         b.withSampler(s);
      }

      for (UniformDescription u : orig.getUniforms()) {
         if (u.textureFormat != null) {
            b.withUniform(u.name, u.type, u.textureFormat);
         } else {
            b.withUniform(u.name, u.type);
         }
      }

      Defines defs = orig.getShaderDefines();

      for (Entry<String, String> e : defs.values().entrySet()) {
         String name = e.getKey();
         String value = e.getValue();

         try {
            b.withShaderDefine(name, Integer.parseInt(value));
         } catch (NumberFormatException nfe1) {
            try {
               b.withShaderDefine(name, Float.parseFloat(value));
            } catch (NumberFormatException var14) {
            }
         }
      }

      for (String flag : defs.flags()) {
         b.withShaderDefine(flag);
      }

      return b.build();
   }
}
