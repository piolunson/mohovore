package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import net.minecraft.class_9779.class_9781;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_9781.class)
public class MixinRenderTickCounter {
   @Shadow
   private float field_51958;

   @Inject(method = "method_60639(J)I", at = @At(value = "FIELD", target = "Lnet/minecraft/class_9779$class_9781;field_51962:J"))
   public void beginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> cir) {
      this.field_51958 = this.field_51958 * Homovore.TIMER;
   }
}
