package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.PlaySoundEvent;
import dev.leonetic.util.sound.CustomSoundPlayer;
import net.minecraft.class_1113;
import net.minecraft.class_1140;
import net.minecraft.class_4235;
import net.minecraft.class_1140.class_11518;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1140.class)
public class SoundEngineMixin {
   @Shadow
   @Final
   private class_4235 field_18949;

   @Inject(method = "method_4854(Lnet/minecraft/class_1113;)Lnet/minecraft/class_1140$class_11518;", at = @At("HEAD"), cancellable = true)
   private void homovore$play(class_1113 instance, CallbackInfoReturnable<class_11518> cir) {
      if (instance instanceof CustomSoundPlayer.Instance custom) {
         CustomSoundPlayer.play(this.field_18949, custom);
         cir.setReturnValue(class_11518.field_60954);
      } else if (Homovore2Client.EVENT_BUS.post(new PlaySoundEvent(instance))) {
         cir.setReturnValue(class_11518.field_60956);
      }
   }

   @Inject(method = "method_4837", at = @At("HEAD"))
   private void homovore$clearCustomSoundCache(CallbackInfo ci) {
      CustomSoundPlayer.clear(this.field_18949);
   }
}
