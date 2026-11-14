package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_1291;
import net.minecraft.class_1294;
import net.minecraft.class_6880;
import net.minecraft.class_7286;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_7286.class)
public abstract class MobEffectFogEnvironmentMixin {
   @Shadow
   public abstract class_6880<class_1291> method_42590();

   @Inject(method = "method_42593", at = @At("HEAD"), cancellable = true)
   private void homovore$effectFog(CallbackInfoReturnable<Boolean> cir) {
      RenderFeatureEvent.Feature feature = null;
      if (this.method_42590() == class_1294.field_5919) {
         feature = RenderFeatureEvent.Feature.BLINDNESS;
      } else if (this.method_42590() == class_1294.field_38092) {
         feature = RenderFeatureEvent.Feature.DARKNESS;
      }

      if (feature != null && RenderFeatureEvent.isSuppressed(feature)) {
         cir.setReturnValue(false);
      }
   }
}
