package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.SeeThroughModule;
import dev.leonetic.util.render.SeeThroughRender;
import net.minecraft.class_10017;
import net.minecraft.class_11659;
import net.minecraft.class_12075;
import net.minecraft.class_4587;
import net.minecraft.class_897;
import net.minecraft.class_898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(class_898.class)
public class MixinEntityRenderDispatcher {
   @Redirect(
      method = "method_72976(Lnet/minecraft/class_10017;Lnet/minecraft/class_12075;DDDLnet/minecraft/class_4587;Lnet/minecraft/class_11659;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_897;method_3936(Lnet/minecraft/class_10017;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_12075;)V"
      )
   )
   private void homovore$seeThroughSubmit(class_897 renderer, class_10017 state, class_4587 poseStack, class_11659 collector, class_12075 camera) {
      boolean apply = false;
      SeeThroughModule seeThrough = Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(SeeThroughModule.class);
      if (seeThrough != null && seeThrough.isEnabled() && seeThrough.shouldSeeThrough(state)) {
         apply = true;
         SeeThroughRender.begin();
      }

      try {
         renderer.method_3936(state, poseStack, collector, camera);
      } finally {
         if (apply) {
            SeeThroughRender.end();
         }
      }
   }
}
