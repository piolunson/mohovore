package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.render.RenderParticleEvent;
import net.minecraft.class_2394;
import net.minecraft.class_702;
import net.minecraft.class_703;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_702.class)
public class ParticleEngineMixin {
   @Inject(method = "method_3056", at = @At("HEAD"), cancellable = true)
   private void homovore$particle(class_2394 options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<class_703> cir) {
      if (Homovore2Client.EVENT_BUS.post(new RenderParticleEvent(options.method_10295()))) {
         cir.setReturnValue(null);
      }
   }
}
