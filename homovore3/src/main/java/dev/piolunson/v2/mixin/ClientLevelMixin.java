package dev.piolunson.v2.mixin;

import dev.piolunson.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientWorld.class)
public class ClientLevelMixin {
   @Redirect(
      method = "doRandomBlockDisplayTicks",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/world/ClientWorld;randomBlockDisplayTick(IIIILnet/minecraft/util/math/random/Random;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos$Mutable;)V"
      )
   )
   private void homovore$barrierInvisibility(ClientWorld level, int x, int y, int z, int radius, Random random, Block heldBlock, Mutable pos) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BARRIER_INVISIBILITY)) {
         heldBlock = Blocks.BARRIER;
      }

      level.randomBlockDisplayTick(x, y, z, radius, random, heldBlock, pos);
   }
}
