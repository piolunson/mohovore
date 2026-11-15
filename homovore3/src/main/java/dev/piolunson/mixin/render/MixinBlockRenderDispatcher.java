package dev.piolunson.mixin.render;

import dev.piolunson.features.modules.render.NoRenderModule;
import java.util.List;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public class MixinBlockRenderDispatcher {
   @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
   private void homovore$noCrystalFire(
      BlockState state,
      BlockPos pos,
      BlockRenderView level,
      MatrixStack poseStack,
      VertexConsumer consumer,
      boolean checkSides,
      List<BlockModelPart> parts,
      CallbackInfo ci
   ) {
      if (state.getBlock() instanceof AbstractFireBlock && NoRenderModule.isActive(m -> m.noCrystalFire.getValue())) {
         ci.cancel();
      }
   }
}
