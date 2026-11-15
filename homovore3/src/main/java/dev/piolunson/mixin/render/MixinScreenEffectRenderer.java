package dev.piolunson.mixin.render;

import dev.piolunson.features.modules.render.NoRenderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class MixinScreenEffectRenderer {
   @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
   private static void homovore$noFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noFire.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
   private static void homovore$noWater(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noLiquid.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
   private static void homovore$noWall(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noWall.getValue())) {
         ci.cancel();
      }
   }
}
