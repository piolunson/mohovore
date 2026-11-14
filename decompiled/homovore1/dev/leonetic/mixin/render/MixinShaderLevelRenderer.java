package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.ShadersModule;
import dev.leonetic.util.render.WorldChamsChain;
import java.util.Set;
import net.minecraft.class_10151;
import net.minecraft.class_276;
import net.minecraft.class_279;
import net.minecraft.class_2960;
import net.minecraft.class_761;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_761.class)
public class MixinShaderLevelRenderer {
   @Shadow
   private class_276 field_53080;

   @Inject(method = "method_3270", at = @At("HEAD"), cancellable = true)
   private void homovore$forceOutlines(CallbackInfoReturnable<Boolean> cir) {
      ShadersModule shaders = Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.wantsOutlines()) {
         cir.setReturnValue(true);
      }
   }

   @Redirect(
      method = "method_22710",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_10151;method_62941(Lnet/minecraft/class_2960;Ljava/util/Set;)Lnet/minecraft/class_279;")
   )
   private class_279 homovore$swapEntityOutlineChain(class_10151 sm, class_2960 id, Set<class_2960> externalTargets) {
      ShadersModule shaders = Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.mode.getValue() == ShadersModule.Mode.Default) {
         class_279 optimised = WorldChamsChain.get(
            externalTargets, shaders.getGlowRadius(), shaders.getGlowIntensity(), shaders.getFillTint(), shaders.getFillAlpha()
         );
         if (optimised != null) {
            return optimised;
         }
      }

      return sm.method_62941(id, externalTargets);
   }
}
