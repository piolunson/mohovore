package dev.leonetic.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.leonetic.mixin.render.RenderSetupAccessor;
import dev.leonetic.mixin.render.RenderTypeAccessor;
import java.util.Locale;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_10149;
import net.minecraft.class_12247;
import net.minecraft.class_1921;
import net.minecraft.class_12247.class_12248;
import net.minecraft.class_12247.class_12338;

public final class SeeThroughRender {
   public static final int SEED_ORDER_BASE = 100000;
   public static final int COLOR_ORDER_BASE = 200000;
   public static boolean active;
   public static boolean cloning;
   public static int cloneSubOrder;
   private static final ConcurrentHashMap<class_1921, class_1921> SEED_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<class_1921, class_1921> COLOR_CACHE = new ConcurrentHashMap<>();

   private SeeThroughRender() {
   }

   public static void begin() {
      active = true;
      cloneSubOrder = 0;
   }

   public static void end() {
      active = false;
   }

   public static boolean isGlintType(class_1921 type) {
      if (type == null) {
         return false;
      }

      String name = type.toString().toLowerCase(Locale.ROOT);
      return name.contains("glint") || name.contains("foil");
   }

   public static class_1921 wrapDepthSeed(class_1921 original) {
      return original == null
         ? null
         : SEED_CACHE.computeIfAbsent(original, o -> build(o, "_homovore_seethrough_seed", DepthTestFunction.GREATER_DEPTH_TEST, true, false, Boolean.FALSE));
   }

   public static class_1921 wrapColor(class_1921 original) {
      return original == null
         ? null
         : COLOR_CACHE.computeIfAbsent(original, o -> build(o, "_homovore_seethrough_color", DepthTestFunction.LEQUAL_DEPTH_TEST, true, true, null));
   }

   private static class_1921 build(
      class_1921 original, String suffix, DepthTestFunction depthFunc, boolean depthWrite, boolean colorWrite, Boolean cullOverride
   ) {
      try {
         class_12247 origSetup = ((RenderTypeAccessor)original).homovore$getState();
         RenderSetupAccessor sa = (RenderSetupAccessor)origSetup;
         RenderPipeline pipeline = clonePipeline(original.method_73243(), depthFunc, depthWrite, colorWrite, cullOverride);
         class_12248 b = class_12247.method_75927(pipeline);

         for (Entry<String, class_12338> e : sa.homovore$getTextures().entrySet()) {
            class_12338 binding = e.getValue();
            if (binding.comp_5229() != null) {
               b.method_76560(e.getKey(), binding.comp_5228(), binding.comp_5229());
            } else {
               b.method_75934(e.getKey(), binding.comp_5228());
            }
         }

         if (sa.homovore$useLightmap()) {
            b.method_75928();
         }

         if (sa.homovore$useOverlay()) {
            b.method_75935();
         }

         if (sa.homovore$affectsCrumbling()) {
            b.method_75936();
         }

         if (sa.homovore$sortOnUpload()) {
            b.method_75937();
         }

         b.method_75929(sa.homovore$getBufferSize());
         b.method_75930(sa.homovore$getLayeringTransform());
         b.method_75933(sa.homovore$getTextureTransform());
         b.method_75932(sa.homovore$getOutlineProperty());
         return class_1921.method_75940(original.toString() + suffix, b.method_75938());
      } catch (Throwable t) {
         return original;
      }
   }

   private static RenderPipeline clonePipeline(RenderPipeline orig, DepthTestFunction depthFunc, boolean depthWrite, boolean colorWrite, Boolean cullOverride) {
      Builder b = RenderPipeline.builder(new Snippet[0])
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

      class_10149 defs = orig.getShaderDefines();

      for (Entry<String, String> e : defs.comp_3103().entrySet()) {
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

      for (String flag : defs.comp_3104()) {
         b.withShaderDefine(flag);
      }

      return b.build();
   }
}
