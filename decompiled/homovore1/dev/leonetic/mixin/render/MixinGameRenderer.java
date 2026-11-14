package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.NoRenderModule;
import dev.leonetic.features.modules.render.ShadersModule;
import dev.leonetic.util.render.HandShaderChain;
import dev.leonetic.util.render.HandShaderRender;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_4587;
import net.minecraft.class_757;
import net.minecraft.class_9779;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_757.class)
public class MixinGameRenderer {
   @Inject(method = "method_3188", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_11684;method_73002()V", shift = Shift.AFTER, ordinal = 0))
   private void handShader$composite(class_9779 deltaTracker, CallbackInfo ci) {
      ShadersModule mod = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (mod != null && mod.wantsHandShader()) {
         HandShaderRender.composite(
            HandShaderChain.get(
               mod.handOutline.getValue(),
               mod.getHandThickness(),
               mod.handFill.getValue(),
               mod.handGlow.getValue(),
               mod.getHandGlowRadius(),
               mod.getHandGlowIntensity()
            )
         );
      }
   }

   @Inject(method = "method_3189", at = @At("HEAD"), cancellable = true)
   private void homovore$noTotem(class_1799 floatingItem, CallbackInfo ci) {
      if (floatingItem.method_31574(class_1802.field_8288) && NoRenderModule.isActive(m -> m.noTotem.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_3186", at = @At("HEAD"), cancellable = true)
   private void homovore$noBob(class_4587 matrices, float tickDelta, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBob.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_3198", at = @At("HEAD"), cancellable = true)
   private void homovore$noTilt(class_4587 matrices, float tickDelta, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noTilt.getValue())) {
         ci.cancel();
      }
   }
}
