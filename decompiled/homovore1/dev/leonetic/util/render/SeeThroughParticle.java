package dev.leonetic.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.class_1059;
import net.minecraft.class_10799;
import net.minecraft.class_3940.class_11941;

public final class SeeThroughParticle {
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[]{class_10799.field_56861})
      .withLocation("homovore/see_through_translucent_particle")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   public static final class_11941 TRANSLUCENT_LAYER = new class_11941(true, class_1059.field_17898, PIPELINE);

   private SeeThroughParticle() {
   }
}
