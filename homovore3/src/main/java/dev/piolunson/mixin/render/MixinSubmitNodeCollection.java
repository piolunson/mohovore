package dev.piolunson.mixin.render;

import dev.piolunson.util.render.SeeThroughRender;
import java.util.List;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BatchingRenderCommandQueue.class)
public class MixinSubmitNodeCollection {
   @Shadow
   @Final
   private OrderedRenderCommandQueueImpl orderedQueueImpl;

   @Inject(method = "submitModel", at = @At("RETURN"))
   private <S> void homovore$cloneModelForSeeThrough(
      Model<? super S> model,
      S modelState,
      MatrixStack poseStack,
      RenderLayer renderType,
      int packedLight,
      int overlay,
      int blockPosition,
      Sprite sprite,
      int flags,
      CrumblingOverlayCommand crumbling,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         if (!SeeThroughRender.isGlintType(renderType)) {
            int sub = SeeThroughRender.cloneSubOrder++;
            SeeThroughRender.cloning = true;

            try {
               RenderLayer seed = SeeThroughRender.wrapDepthSeed(renderType);
               this.orderedQueueImpl
                  .getBatchingQueue(100000 + sub)
                  .submitModel(model, modelState, poseStack, seed, packedLight, overlay, blockPosition, sprite, flags, crumbling);
               RenderLayer color = SeeThroughRender.wrapColor(renderType);
               this.orderedQueueImpl
                  .getBatchingQueue(200000 + sub)
                  .submitModel(model, modelState, poseStack, color, packedLight, overlay, blockPosition, sprite, flags, crumbling);
            } finally {
               SeeThroughRender.cloning = false;
            }
         }
      }
   }

   @Inject(method = "submitModelPart", at = @At("RETURN"))
   private void homovore$cloneModelPartForSeeThrough(
      ModelPart part,
      MatrixStack poseStack,
      RenderLayer renderType,
      int packedLight,
      int overlay,
      Sprite sprite,
      boolean copyNormals,
      boolean fullBright,
      int flags,
      CrumblingOverlayCommand crumbling,
      int data,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         if (!SeeThroughRender.isGlintType(renderType)) {
            int sub = SeeThroughRender.cloneSubOrder++;
            SeeThroughRender.cloning = true;

            try {
               RenderLayer seed = SeeThroughRender.wrapDepthSeed(renderType);
               this.orderedQueueImpl
                  .getBatchingQueue(100000 + sub)
                  .submitModelPart(part, poseStack, seed, packedLight, overlay, sprite, copyNormals, fullBright, flags, crumbling, data);
               RenderLayer color = SeeThroughRender.wrapColor(renderType);
               this.orderedQueueImpl
                  .getBatchingQueue(200000 + sub)
                  .submitModelPart(part, poseStack, color, packedLight, overlay, sprite, copyNormals, fullBright, flags, crumbling, data);
            } finally {
               SeeThroughRender.cloning = false;
            }
         }
      }
   }

   @Inject(method = "submitItem", at = @At("RETURN"))
   private void homovore$cloneItemForSeeThrough(
      MatrixStack poseStack,
      ItemDisplayContext context,
      int packedLight,
      int overlay,
      int data,
      int[] tints,
      List<BakedQuad> quads,
      RenderLayer renderType,
      Glint foil,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         if (!SeeThroughRender.isGlintType(renderType)) {
            int sub = SeeThroughRender.cloneSubOrder++;
            SeeThroughRender.cloning = true;

            try {
               RenderLayer seed = SeeThroughRender.wrapDepthSeed(renderType);
               this.orderedQueueImpl.getBatchingQueue(100000 + sub).submitItem(poseStack, context, packedLight, overlay, data, tints, quads, seed, Glint.NONE);
               RenderLayer color = SeeThroughRender.wrapColor(renderType);
               this.orderedQueueImpl.getBatchingQueue(200000 + sub).submitItem(poseStack, context, packedLight, overlay, data, tints, quads, color, Glint.NONE);
            } finally {
               SeeThroughRender.cloning = false;
            }
         }
      }
   }
}
