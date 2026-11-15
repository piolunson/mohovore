package dev.piolunson.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;

class Pipelines {
   static final RenderPipeline GLOBAL_QUADS_PIPELINE = RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
      .withLocation("pipeline/global_fill_pipeline")
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   static final RenderPipeline GLOBAL_LINES_PIPELINE = RenderPipeline.builder(new Snippet[]{RenderPipelines.RENDERTYPE_LINES_SNIPPET})
      .withLocation("pipeline/global_lines_pipeline")
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.LINES)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthWrite(false)
      .withCull(false)
      .build();
}
