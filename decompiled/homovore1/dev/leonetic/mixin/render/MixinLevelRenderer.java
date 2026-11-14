package dev.leonetic.mixin.render;

import dev.leonetic.event.impl.render.RenderBlockOutlineEvent;
import dev.leonetic.features.modules.render.NoRenderModule;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_11658;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_761;
import net.minecraft.class_4597.class_4598;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_761.class)
public class MixinLevelRenderer {
   @Inject(method = "method_62210", at = @At("HEAD"), cancellable = true)
   public void renderBlockOutline(class_4598 bufferSource, class_4587 poseStack, boolean bl, class_11658 levelRenderState, CallbackInfo ci) {
      if (Util.EVENT_BUS.post(new RenderBlockOutlineEvent())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_43788", at = @At("HEAD"), cancellable = true)
   private void homovore$noDarkness(class_4184 camera, CallbackInfoReturnable<Boolean> cir) {
      if (NoRenderModule.isActive(m -> m.noDarkness.getValue())) {
         cir.setReturnValue(false);
      }
   }
}
