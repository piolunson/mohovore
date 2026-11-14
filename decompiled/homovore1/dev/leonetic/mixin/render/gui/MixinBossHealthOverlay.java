package dev.leonetic.mixin.render.gui;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_332;
import net.minecraft.class_337;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_337.class)
public class MixinBossHealthOverlay {
   @Inject(method = "method_1796", at = @At("HEAD"), cancellable = true)
   private void homovore$noBossBar(class_332 context, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBossBar.getValue())) {
         ci.cancel();
      }
   }
}
