package dev.leonetic.util.render;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.MovingBlockRenderState;
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
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public final class HandSilhouetteCollector implements RenderCommandQueue {
   private final OutlineVertexConsumerProvider outline;
   private final int color;

   public HandSilhouetteCollector(OutlineVertexConsumerProvider outline, int rgb) {
      this.outline = outline;
      this.color = 0xFF000000 | rgb;
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
      VertexConsumer consumer = this.buffer(renderLayer, sprite);
      if (consumer != null) {
         try {
            model.setAngles(state);
            model.render(matrices, consumer, light, overlay, tintedColor);
         } catch (Exception var13) {
         }
      }
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
      int outlineColor
   ) {
      VertexConsumer consumer = this.buffer(renderLayer, sprite);
      if (consumer != null) {
         try {
            part.render(matrices, consumer, light, overlay, tintedColor);
         } catch (Exception var14) {
         }
      }
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
      VertexConsumer consumer = this.buffer(renderLayer, null);
      if (consumer != null) {
         Entry pose = matrices.peek();

         for (BakedQuad quad : quads) {
            consumer.quad(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
         }
      }
   }

   private VertexConsumer buffer(RenderLayer renderType, Sprite sprite) {
      if (HandShaderRender.capturePaused) {
         return null;
      }

      VertexConsumer consumer;
      try {
         this.outline.setColor(this.color);
         consumer = this.outline.getBuffer(renderType);
      } catch (IllegalStateException noOutline) {
         return null;
      }

      return sprite != null ? sprite.getTextureSpecificVertexConsumer(consumer) : consumer;
   }

   public void submitShadowPieces(MatrixStack matrices, float shadowRadius, List<ShadowPiece> shadowPieces) {
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
   }

   public void submitFire(MatrixStack matrices, EntityRenderState renderState, Quaternionf rotation) {
   }

   public void submitLeash(MatrixStack matrices, LeashData leashData) {
   }

   public void submitBlock(MatrixStack matrices, BlockState state, int light, int overlay, int outlineColor) {
   }

   public void submitMovingBlock(MatrixStack matrices, MovingBlockRenderState state) {
   }

   public void submitBlockStateModel(
      MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor
   ) {
   }

   public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
   }

   public void submitCustom(LayeredCustom customRenderer) {
   }
}
