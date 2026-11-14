package dev.leonetic.util.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.class_5596;
import dev.leonetic.features.modules.render.SkyboxModule;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.class_10789;
import net.minecraft.class_12247;
import net.minecraft.class_1921;
import net.minecraft.class_287;
import net.minecraft.class_289;
import net.minecraft.class_290;
import net.minecraft.class_2960;

public final class SkyboxRenderer {
   private static final class_2960 VERTEX_SHADER = class_2960.method_60655("homovore", "core/skybox");
   private static final Map<SkyboxModule.Mode, class_1921> TYPES = new EnumMap<>(SkyboxModule.Mode.class);

   private SkyboxRenderer() {
   }

   private static class_1921 buildType(SkyboxModule.Mode mode) {
      RenderPipeline pipeline = RenderPipeline.builder(new Snippet[0])
         .withLocation("pipeline/skybox_" + mode.name().toLowerCase())
         .withVertexShader(VERTEX_SHADER)
         .withFragmentShader(class_2960.method_60655("homovore", "core/skybox_" + mode.name().toLowerCase()))
         .withUniform("DynamicTransforms", class_10789.field_60031)
         .withUniform("Projection", class_10789.field_60031)
         .withUniform("Globals", class_10789.field_60031)
         .withVertexFormat(class_290.field_1576, class_5596.field_27382)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withCull(false)
         .build();
      return class_1921.method_75940("homovore_skybox_" + mode.name().toLowerCase(), class_12247.method_75927(pipeline).method_75938());
   }

   public static void render(SkyboxModule.Mode mode, float r, float g, float b) {
      class_1921 type = TYPES.get(mode);
      if (type != null) {
         class_287 buffer = class_289.method_1348().method_60827(class_5596.field_27382, class_290.field_1576);
         buffer.method_22912(-1.0F, -1.0F, 0.0F).method_22915(r, g, b, 1.0F);
         buffer.method_22912(1.0F, -1.0F, 0.0F).method_22915(r, g, b, 1.0F);
         buffer.method_22912(1.0F, 1.0F, 0.0F).method_22915(r, g, b, 1.0F);
         buffer.method_22912(-1.0F, 1.0F, 0.0F).method_22915(r, g, b, 1.0F);
         type.method_60895(buffer.method_60800());
      }
   }

   static {
      for (SkyboxModule.Mode mode : SkyboxModule.Mode.values()) {
         TYPES.put(mode, buildType(mode));
      }
   }
}
