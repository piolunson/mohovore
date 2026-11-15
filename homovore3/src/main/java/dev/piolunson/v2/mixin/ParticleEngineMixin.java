package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.render.RenderParticleEvent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleEngineMixin {
   @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
   private void homovore$particle(ParticleEffect options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir) {
      if (Homovore2Client.EVENT_BUS.post(new RenderParticleEvent(options.getType()))) {
         cir.setReturnValue(null);
      }
   }
}
