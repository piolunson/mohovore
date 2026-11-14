package dev.leonetic.util.render;

import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import dev.leonetic.util.traits.Util;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.Custom;
import net.minecraft.client.render.command.OrderedRenderCommandQueue.LayeredCustom;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
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
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

public final class WireframeEntityRenderer implements Util {
   private static final int FULL_BRIGHT = 15728880;

   private WireframeEntityRenderer() {
   }

   public static List<float[][]> render(
      MatrixStack eventStack, Entity entity, Vec3d pos, List<float[][]> quads, float partialTick, Color sideColor, Color lineColor, float lineWidth
   ) {
      if (quads == null) {
         quads = captureGeometry(entity, partialTick);
      }

      if (quads.isEmpty()) {
         return quads;
      }

      Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();
      float dx = (float)(pos.x - cam.getX());
      float dy = (float)(pos.y - cam.getY());
      float dz = (float)(pos.z - cam.getZ());
      eventStack.push();
      eventStack.translate(dx, dy, dz);
      Entry pose = eventStack.peek();
      if (sideColor.getAlpha() > 0) {
         BufferBuilder fill = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

         for (float[][] q : quads) {
            for (float[] v : q) {
               fill.vertex(pose, v[0], v[1], v[2]).color(sideColor.getRGB());
            }
         }

         Layers.quads().draw(fill.end());
      }

      BufferBuilder edges = Tessellator.getInstance().begin(DrawMode.LINES, VertexFormats.POSITION_COLOR_LINE_WIDTH);

      for (float[][] q : quads) {
         for (int i = 0; i < 4; i++) {
            float[] v1 = q[i];
            float[] v2 = q[(i + 1) % 4];
            edges.vertex(pose, v1[0], v1[1], v1[2]).color(lineColor.getRGB()).lineWidth(lineWidth);
            edges.vertex(pose, v2[0], v2[1], v2[2]).color(lineColor.getRGB()).lineWidth(lineWidth);
         }
      }

      Layers.lines().draw(edges.end());
      eventStack.pop();
      return quads;
   }

   private static List<float[][]> captureGeometry(Entity entity, float partialTick) {
      try {
         EntityRenderState state = mc.getEntityRenderDispatcher().getAndUpdateRenderState(entity, partialTick);
         CameraRenderState camera = new CameraRenderState();
         camera.pos = Vec3d.ZERO;
         camera.entityPos = Vec3d.ZERO;
         camera.blockPos = BlockPos.ORIGIN;
         camera.orientation = new Quaternionf();
         camera.initialized = true;
         WireframeEntityRenderer.CapturingCollector collector = new WireframeEntityRenderer.CapturingCollector();
         mc.getEntityRenderDispatcher().render(state, camera, 0.0, 0.0, 0.0, new MatrixStack(), collector);
         return collector.consumer.quads;
      } catch (Exception ignored) {
         return new ArrayList<>();
      }
   }

   private static final class CapturingCollector implements OrderedRenderCommandQueue {
      final WireframeEntityRenderer.CapturingConsumer consumer = new WireframeEntityRenderer.CapturingConsumer();

      public RenderCommandQueue getBatchingQueue(int order) {
         return this;
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
         if (model instanceof BipedEntityModel<?> humanoid) {
            this.renderFirstLayerOnly(humanoid, matrices, light, overlay);
         } else {
            model.render(matrices, this.consumer, light, overlay);
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
         int data
      ) {
         part.render(matrices, this.consumer, light, overlay);
      }

      private void renderFirstLayerOnly(BipedEntityModel<?> model, MatrixStack poseStack, int light, int overlay) {
         boolean hatVis = model.hat.visible;
         model.hat.visible = false;
         boolean jacketVis = false;
         boolean lSleeveVis = false;
         boolean rSleeveVis = false;
         boolean lPantsVis = false;
         boolean rPantsVis = false;
         if (model instanceof PlayerEntityModel pm) {
            jacketVis = pm.jacket.visible;
            pm.jacket.visible = false;
            lSleeveVis = pm.leftSleeve.visible;
            pm.leftSleeve.visible = false;
            rSleeveVis = pm.rightSleeve.visible;
            pm.rightSleeve.visible = false;
            lPantsVis = pm.leftPants.visible;
            pm.leftPants.visible = false;
            rPantsVis = pm.rightPants.visible;
            pm.rightPants.visible = false;
         }

         model.render(poseStack, this.consumer, light, overlay);
         model.hat.visible = hatVis;
         if (model instanceof PlayerEntityModel pm) {
            pm.jacket.visible = jacketVis;
            pm.leftSleeve.visible = lSleeveVis;
            pm.rightSleeve.visible = rSleeveVis;
            pm.leftPants.visible = lPantsVis;
            pm.rightPants.visible = rPantsVis;
         }
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
      }

      public void submitCustom(MatrixStack matrices, RenderLayer renderLayer, Custom customRenderer) {
      }

      public void submitCustom(LayeredCustom customRenderer) {
      }
   }

   private static final class CapturingConsumer implements VertexConsumer {
      final List<float[][]> quads = new ArrayList<>();
      private final float[][] pending = new float[4][];
      private int count = 0;

      private void pushVertex(float x, float y, float z) {
         this.pending[this.count++] = new float[]{x, y, z};
         if (this.count == 4) {
            this.quads
               .add(
                  new float[][]{
                     (float[])this.pending[0].clone(), (float[])this.pending[1].clone(), (float[])this.pending[2].clone(), (float[])this.pending[3].clone()
                  }
               );
            this.count = 0;
         }
      }

      public VertexConsumer vertex(float x, float y, float z) {
         this.pushVertex(x, y, z);
         return this;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer color(int argb) {
         return this;
      }

      public VertexConsumer texture(float u, float v) {
         return this;
      }

      public VertexConsumer overlay(int u, int v) {
         return this;
      }

      public VertexConsumer light(int u, int v) {
         return this;
      }

      public VertexConsumer normal(float x, float y, float z) {
         return this;
      }

      public VertexConsumer lineWidth(float width) {
         return this;
      }
   }
}
