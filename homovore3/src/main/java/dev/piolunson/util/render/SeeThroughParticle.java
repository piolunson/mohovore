package dev.piolunson.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.texture.SpriteAtlasTexture;

public final class SeeThroughParticle {
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[]{RenderPipelines.PARTICLE_SNIPPET})
      .withLocation("homovore/see_through_translucent_particle")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   public static final RenderType TRANSLUCENT_LAYER = new RenderType(true, SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE, PIPELINE);

   private SeeThroughParticle() {
   }
}
