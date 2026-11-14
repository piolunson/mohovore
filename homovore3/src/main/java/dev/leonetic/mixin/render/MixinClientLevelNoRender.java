package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockParticleEffect;
import net.minecraft.util.collection.WeightedPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientLevelNoRender {
   @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
   private void homovore$noDestroyBlockEffect(BlockPos pos, BlockState state, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBlockBreak.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "spawnBlockBreakingParticle", at = @At("HEAD"), cancellable = true)
   private void homovore$noBreakingBlockEffect(BlockPos pos, Direction direction, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBlockBreak.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "addBlockParticleEffects", at = @At("HEAD"), cancellable = true)
   private void homovore$noExplosionTracker(Vec3d center, float radius, int blockCount, WeightedPool<BlockParticleEffect> blockParticles, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noExplosion.getValue())) {
         ci.cancel();
      }
   }
}
