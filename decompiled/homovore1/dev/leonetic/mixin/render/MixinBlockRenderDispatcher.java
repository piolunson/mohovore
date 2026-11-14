package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.NoRenderModule;
import java.util.List;
import net.minecraft.class_10889;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4770;
import net.minecraft.class_776;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_776.class)
public class MixinBlockRenderDispatcher {
   @Inject(method = "method_3355", at = @At("HEAD"), cancellable = true)
   private void homovore$noCrystalFire(
      class_2680 state,
      class_2338 pos,
      class_1920 level,
      class_4587 poseStack,
      class_4588 consumer,
      boolean checkSides,
      List<class_10889> parts,
      CallbackInfo ci
   ) {
      if (state.method_26204() instanceof class_4770 && NoRenderModule.isActive(m -> m.noCrystalFire.getValue())) {
         ci.cancel();
      }
   }
}
