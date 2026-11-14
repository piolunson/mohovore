package dev.leonetic.util.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import dev.leonetic.features.modules.render.SkyboxModule;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class SkyboxRenderer {
   private static final Identifier VERTEX_SHADER = Identifier.of("homovore", "core/skybox");
   private static final Map<SkyboxModule.Mode, RenderLayer> TYPES = new EnumMap<>(SkyboxModule.Mode.class);

   private SkyboxRenderer() {
   }

   private static RenderLayer buildType(SkyboxModule.Mode mode) {
      RenderPipeline pipeline = RenderPipeline.builder(new Snippet[0])
         .withLocation("pipeline/skybox_" + mode.name().toLowerCase())
         .withVertexShader(VERTEX_SHADER)
         .withFragmentShader(Identifier.of("homovore", "core/skybox_" + mode.name().toLowerCase()))
         .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
         .withUniform("Projection", UniformType.UNIFORM_BUFFER)
         .withUniform("Globals", UniformType.UNIFORM_BUFFER)
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withCull(false)
         .build();
      return RenderLayer.of("homovore_skybox_" + mode.name().toLowerCase(), RenderSetup.builder(pipeline).build());
   }

   public static void render(SkyboxModule.Mode mode, float r, float g, float b) {
      RenderLayer type = TYPES.get(mode);
      if (type != null) {
         BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
         buffer.vertex(-1.0F, -1.0F, 0.0F).color(r, g, b, 1.0F);
         buffer.vertex(1.0F, -1.0F, 0.0F).color(r, g, b, 1.0F);
         buffer.vertex(1.0F, 1.0F, 0.0F).color(r, g, b, 1.0F);
         buffer.vertex(-1.0F, 1.0F, 0.0F).color(r, g, b, 1.0F);
         type.draw(buffer.end());
      }
   }

   static {
      for (SkyboxModule.Mode mode : SkyboxModule.Mode.values()) {
         TYPES.put(mode, buildType(mode));
      }
   }
}
