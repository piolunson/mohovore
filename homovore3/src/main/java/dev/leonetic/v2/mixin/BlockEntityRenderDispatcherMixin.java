package dev.leonetic.v2.mixin;

import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.render.RenderBlockEntityEvent;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderDispatcherMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void homovore$blockEntity(
      BlockEntityRenderState state, MatrixStack poseStack, OrderedRenderCommandQueue collector, CameraRenderState camera, CallbackInfo ci
   ) {
      if (Homovore2Client.EVENT_BUS.post(new RenderBlockEntityEvent(state.type))) {
         ci.cancel();
      }
   }
}
