package dev.leonetic.mixin.render.gui;

import dev.leonetic.features.modules.render.NoRenderModule;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_437.class)
public class MixinScreen {
   @Inject(method = "method_25420", at = @At("HEAD"), cancellable = true)
   private void homovore$noBackground(class_332 context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (class_310.method_1551().field_1687 != null && NoRenderModule.isActive(m -> m.noBackground.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_52752", at = @At("HEAD"), cancellable = true)
   private void homovore$noTransparentBackground(class_332 context, CallbackInfo ci) {
      if (class_310.method_1551().field_1687 != null && NoRenderModule.isActive(m -> m.noBackground.getValue())) {
         ci.cancel();
      }
   }
}
