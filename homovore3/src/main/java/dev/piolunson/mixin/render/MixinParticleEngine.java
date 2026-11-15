package dev.piolunson.mixin.render;

import dev.piolunson.features.modules.render.NoRenderModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class MixinParticleEngine {
   @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
   private void homovore$noParticles(
      ParticleEffect particle, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir
   ) {
      if (!NoRenderModule.isActive(m -> m.noPotionParticle.getValue())
         || particle.getType() != ParticleTypes.ENTITY_EFFECT
            && particle.getType() != ParticleTypes.EFFECT
            && particle.getType() != ParticleTypes.INSTANT_EFFECT
            && particle.getType() != ParticleTypes.WITCH) {
         if (!NoRenderModule.isActive(m -> m.noExplosion.getValue())
            || particle.getType() != ParticleTypes.EXPLOSION && particle.getType() != ParticleTypes.EXPLOSION_EMITTER) {
            if (NoRenderModule.isActive(m -> m.noTotemParticle.getValue()) && particle.getType() == ParticleTypes.TOTEM_OF_UNDYING) {
               cir.setReturnValue(null);
            } else {
               if (NoRenderModule.isActive(m -> m.noWaterParticle.getValue())
                  && (
                     particle.getType() == ParticleTypes.RAIN
                        || particle.getType() == ParticleTypes.SPLASH
                        || particle.getType() == ParticleTypes.UNDERWATER
                        || particle.getType() == ParticleTypes.BUBBLE
                        || particle.getType() == ParticleTypes.BUBBLE_POP
                        || particle.getType() == ParticleTypes.BUBBLE_COLUMN_UP
                        || particle.getType() == ParticleTypes.DRIPPING_DRIPSTONE_WATER
                        || particle.getType() == ParticleTypes.DRIPPING_WATER
                        || particle.getType() == ParticleTypes.FALLING_DRIPSTONE_WATER
                        || particle.getType() == ParticleTypes.FALLING_WATER
                  )) {
                  cir.setReturnValue(null);
               }
            }
         } else {
            cir.setReturnValue(null);
         }
      } else {
         cir.setReturnValue(null);
      }
   }
}
