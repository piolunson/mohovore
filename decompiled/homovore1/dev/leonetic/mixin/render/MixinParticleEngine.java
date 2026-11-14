package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_2394;
import net.minecraft.class_2398;
import net.minecraft.class_702;
import net.minecraft.class_703;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_702.class)
public class MixinParticleEngine {
   @Inject(method = "method_3056", at = @At("HEAD"), cancellable = true)
   private void homovore$noParticles(class_2394 particle, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<class_703> cir) {
      if (!NoRenderModule.isActive(m -> m.noPotionParticle.getValue())
         || particle.method_10295() != class_2398.field_11226
            && particle.method_10295() != class_2398.field_11245
            && particle.method_10295() != class_2398.field_11213
            && particle.method_10295() != class_2398.field_11249) {
         if (!NoRenderModule.isActive(m -> m.noExplosion.getValue())
            || particle.method_10295() != class_2398.field_11236 && particle.method_10295() != class_2398.field_11221) {
            if (NoRenderModule.isActive(m -> m.noTotemParticle.getValue()) && particle.method_10295() == class_2398.field_11220) {
               cir.setReturnValue(null);
            } else {
               if (NoRenderModule.isActive(m -> m.noWaterParticle.getValue())
                  && (
                     particle.method_10295() == class_2398.field_11242
                        || particle.method_10295() == class_2398.field_11202
                        || particle.method_10295() == class_2398.field_11210
                        || particle.method_10295() == class_2398.field_11247
                        || particle.method_10295() == class_2398.field_11241
                        || particle.method_10295() == class_2398.field_11238
                        || particle.method_10295() == class_2398.field_28078
                        || particle.method_10295() == class_2398.field_11232
                        || particle.method_10295() == class_2398.field_28079
                        || particle.method_10295() == class_2398.field_18306
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
