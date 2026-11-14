package dev.leonetic.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.class_5596;
import net.minecraft.class_10799;
import net.minecraft.class_290;

class Pipelines {
   static final RenderPipeline GLOBAL_QUADS_PIPELINE = RenderPipeline.builder(new Snippet[]{class_10799.field_56860})
      .withLocation("pipeline/global_fill_pipeline")
      .withVertexFormat(class_290.field_1576, class_5596.field_27382)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   static final RenderPipeline GLOBAL_LINES_PIPELINE = RenderPipeline.builder(new Snippet[]{class_10799.field_56859})
      .withLocation("pipeline/global_lines_pipeline")
      .withVertexFormat(class_290.field_63454, class_5596.field_27377)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthWrite(false)
      .withCull(false)
      .build();
}
