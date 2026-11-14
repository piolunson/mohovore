package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.PlaySoundEvent;
import dev.leonetic.util.sound.CustomSoundPlayer;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.SoundSystem.PlayResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public class SoundEngineMixin {
   @Shadow
   @Final
   private Channel channel;

   @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;", at = @At("HEAD"), cancellable = true)
   private void homovore$play(SoundInstance instance, CallbackInfoReturnable<PlayResult> cir) {
      if (instance instanceof CustomSoundPlayer.Instance custom) {
         CustomSoundPlayer.play(this.channel, custom);
         cir.setReturnValue(PlayResult.STARTED);
      } else if (Homovore2Client.EVENT_BUS.post(new PlaySoundEvent(instance))) {
         cir.setReturnValue(PlayResult.NOT_STARTED);
      }
   }

   @Inject(method = "reloadSounds", at = @At("HEAD"))
   private void homovore$clearCustomSoundCache(CallbackInfo ci) {
      CustomSoundPlayer.clear(this.channel);
   }
}
