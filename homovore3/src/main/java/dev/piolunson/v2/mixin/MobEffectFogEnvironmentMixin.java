package dev.piolunson.v2.mixin;

import dev.piolunson.v2.event.impl.render.RenderFeatureEvent;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StatusEffectFogModifier.class)
public abstract class MobEffectFogEnvironmentMixin {
   @Shadow
   public abstract RegistryEntry<StatusEffect> getStatusEffect();

   @Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
   private void homovore$effectFog(CallbackInfoReturnable<Boolean> cir) {
      RenderFeatureEvent.Feature feature = null;
      if (this.getStatusEffect() == StatusEffects.BLINDNESS) {
         feature = RenderFeatureEvent.Feature.BLINDNESS;
      } else if (this.getStatusEffect() == StatusEffects.DARKNESS) {
         feature = RenderFeatureEvent.Feature.DARKNESS;
      }

      if (feature != null && RenderFeatureEvent.isSuppressed(feature)) {
         cir.setReturnValue(false);
      }
   }
}
