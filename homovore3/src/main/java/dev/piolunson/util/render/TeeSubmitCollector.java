package dev.piolunson.util.render;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.Custom;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.LayeredCustom;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState.LeashData;
import net.minecraft.client.render.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public final class TeeSubmitCollector implements OrderedRenderCommandQueue {
   private final OrderedRenderCommandQueue primary;
   private final RenderCommandQueue secondary;
   private final RenderCommandQueue tee;

   public TeeSubmitCollector(OrderedRenderCommandQueue primary, RenderCommandQueue secondary) {
      this.primary = primary;
      this.secondary = secondary;
      this.tee = new TeeSubmitCollector.Tee(primary, secondary);
   }

   public RenderCommandQueue getBatchingQueue(int order) {
      return new TeeSubmitCollector.Tee(this.primary.getBatchingQueue(order), this.secondary);
   }

   public <S> void submitModel(
      Model<? super S> model,
      S state,
      MatrixStack matrices,
      RenderLayer renderLayer,
      int light,
      int overlay,
      int tintedColor,
      Sprite sprite,
      int outlineColor,
      CrumblingOverlayCommand crumblingOverlay
   ) {
      this.tee.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay);
   }

   public void submitModelPart(
      ModelPart part,
      MatrixStack matrices,
      RenderLayer renderLayer,
      int light,
      int overlay,
      Sprite sprite,
      boolean sheeted,
      boolean hasGlint,
      int tintedColor,
      CrumblingOverlayCommand crumblingOverlay,
      int oc
   ) {
      this.tee.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, tintedColor, crumblingOverlay, oc);
   }

   public void submitItem(
      MatrixStack matrices,
      ItemDisplayContext displayContext,
      int light,
      int overlay,
      int outlineColors,
      int[] tintLayers,
      List<BakedQuad> quads,
      RenderLayer renderLayer,
      Glint glintType
   ) {
      this.tee.submitItem(matrices, displayContext, light, overlay, outlineColors, tintLayers, quads, renderLayer, glintType);
   }

   public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
      this.primary.submitShadowPieces(matrices, shadowRadius, shadowPieces);
   }

   public void submitLabel(
      MatrixStack matrices,
      Vec3d nameLabelPos,
      int y,
      Text label,
      boolean notSneaking,
      int light,
      double squaredDistanceToCamera,
      CameraRenderState cameraState
   ) {
      this.primary.submitLabel(matrices, nameLabelPos, y, label, notSneaking, light, squaredDistanceToCamera, cameraState);
   }

   public void submitText(
      MatrixStack matrices,
      float x,
      float y,
      OrderedText text,
      boolean dropShadow,
      TextLayerType layerType,
      int light,
      int color,
      int backgroundColor,
      int outlineColor
   ) {
      this.primary.submitText(matrices, x, y, text, dropShadow, layerType, light, color, backgroundColor, outlineColor);
   }

   public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
      this.primary.submitFire(matrices, renderState, rotation);
   }

   public void submitLeash(MatrixStack matrices, LeashData leashData) {
      this.primary.submitLeash(matrices, leashData);
   }

   public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
      this.primary.submitBlock(matrices, state, light, overlay, outlineColor);
   }

   public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
      this.primary.submitMovingBlock(matrices, state);
   }

   public void submitBlockStateModel(
      MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
   ) {
      this.primary.submitBlockStateModel(matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
   }

   public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
      this.primary.submitCustom(matrices, renderLayer, customRenderer);
   }

   public void submitCustom(LayeredCustom customRenderer) {
      this.primary.submitCustom(customRenderer);
   }

   private record Tee(RenderCommandQueue primary, RenderCommandQueue secondary) implements RenderCommandQueue {
      public <S> void submitModel(
         Model<? super S> model,
         S state,
         MatrixStack matrices,
         RenderLayer renderLayer,
         int light,
         int overlay,
         int tintedColor,
         Sprite sprite,
         int outlineColor,
         CrumblingOverlayCommand crumblingOverlay
      ) {
         this.primary.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay);
         this.secondary.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay);
      }

      public void submitModelPart(
         ModelPart part,
         MatrixStack matrices,
         RenderLayer renderLayer,
         int light,
         int overlay,
         Sprite sprite,
         boolean sheeted,
         boolean hasGlint,
         int tintedColor,
         CrumblingOverlayCommand crumblingOverlay,
         int oc
      ) {
         this.primary.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, tintedColor, crumblingOverlay, oc);
         this.secondary.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, tintedColor, crumblingOverlay, oc);
      }

      public void submitItem(
         MatrixStack matrices,
         ItemDisplayContext displayContext,
         int light,
         int overlay,
         int outlineColors,
         int[] tintLayers,
         List<BakedQuad> quads,
         RenderLayer renderLayer,
         Glint glintType
      ) {
         this.primary.submitItem(matrices, displayContext, light, overlay, outlineColors, tintLayers, quads, renderLayer, glintType);
         this.secondary.submitItem(matrices, displayContext, light, overlay, outlineColors, tintLayers, quads, renderLayer, glintType);
      }

      public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
         this.primary.submitShadowPieces(matrices, shadowRadius, shadowPieces);
      }

      public void submitLabel(
         MatrixStack matrices,
         Vec3d nameLabelPos,
         int y,
         Text label,
         boolean notSneaking,
         int light,
         double squaredDistanceToCamera,
         CameraRenderState cameraState
      ) {
         this.primary.submitLabel(matrices, nameLabelPos, y, label, notSneaking, light, squaredDistanceToCamera, cameraState);
      }

      public void submitText(
         MatrixStack matrices,
         float x,
         float y,
         OrderedText text,
         boolean dropShadow,
         TextLayerType layerType,
         int light,
         int color,
         int backgroundColor,
         int outlineColor
      ) {
         this.primary.submitText(matrices, x, y, text, dropShadow, layerType, light, color, backgroundColor, outlineColor);
      }

      public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
         this.primary.submitFire(matrices, renderState, rotation);
      }

      public void submitLeash(MatrixStack matrices, LeashData leashData) {
         this.primary.submitLeash(matrices, leashData);
      }

      public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
         this.primary.submitBlock(matrices, state, light, overlay, outlineColor);
      }

      public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
         this.primary.submitMovingBlock(matrices, state);
      }

      public void submitBlockStateModel(
         MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
      ) {
         this.primary.submitBlockStateModel(matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
      }

      public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
         this.primary.submitCustom(matrices, renderLayer, customRenderer);
      }

      public void submitCustom(LayeredCustom customRenderer) {
         this.primary.submitCustom(customRenderer);
      }
   }
}
