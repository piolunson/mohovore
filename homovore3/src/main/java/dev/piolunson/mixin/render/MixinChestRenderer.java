package dev.piolunson.mixin.render;

import dev.piolunson.features.modules.render.NoRenderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.ChestBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.ChestBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntityRenderer.class)
public class MixinChestRenderer {
   @Inject(
      method = "render(Lnet/minecraft/client/render/block/entity/state/ChestBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void homovore$tileEntityRange(
      ChestBlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue collector, CameraRenderState camera, CallbackInfo ci
   ) {
      NoRenderModule module = NoRenderModule.getInstance();
      if (module != null && module.isEnabled() && module.tileEntity.getValue() >= 2) {
         Vec3d cameraPos = MinecraftClient.getInstance().getEntityRenderDispatcher().camera.getCameraPos();
         double distanceSquared = state.pos.getSquaredDistance(cameraPos);
         double maxDistance = Math.pow(module.tileEntity.getValue().intValue(), 2.0);
         if (distanceSquared > maxDistance) {
            ci.cancel();
         }
      }
   }
}
