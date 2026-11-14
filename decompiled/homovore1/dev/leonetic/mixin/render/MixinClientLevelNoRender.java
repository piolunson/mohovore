package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_11749;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_6012;
import net.minecraft.class_638;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_638.class)
public class MixinClientLevelNoRender {
   @Inject(method = "method_31595", at = @At("HEAD"), cancellable = true)
   private void homovore$noDestroyBlockEffect(class_2338 pos, class_2680 state, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBlockBreak.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_74254", at = @At("HEAD"), cancellable = true)
   private void homovore$noBreakingBlockEffect(class_2338 pos, class_2350 direction, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBlockBreak.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_73228", at = @At("HEAD"), cancellable = true)
   private void homovore$noExplosionTracker(class_243 center, float radius, int blockCount, class_6012<class_11749> blockParticles, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noExplosion.getValue())) {
         ci.cancel();
      }
   }
}
