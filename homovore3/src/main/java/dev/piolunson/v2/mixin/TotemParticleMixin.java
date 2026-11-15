package dev.piolunson.v2.mixin;

import dev.piolunson.v2.feature.module.impl.PopChamsModule;
import dev.piolunson.v2.render.SeeThroughParticle;
import net.minecraft.client.particle.AnimatedParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TotemParticle.class)
public abstract class TotemParticleMixin extends AnimatedParticle {
   private TotemParticleMixin(ClientWorld level, double x, double y, double z, SpriteProvider sprites, float gravity) {
      super(level, x, y, z, sprites, gravity);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void homovore$recolour(
      ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteProvider sprites, CallbackInfo ci
   ) {
      PopChamsModule module = PopChamsModule.active();
      if (module != null && module.customizesParticles()) {
         boolean pale = this.red >= 0.5F && this.green >= 0.5F;
         this.setColor(module.particleColor(pale));
         this.scale = this.scale * module.particleScale();
      }
   }

   public RenderType getRenderType() {
      PopChamsModule module = PopChamsModule.active();
      return module != null && module.customizesParticles() && module.particlesThroughWalls.getValue() ? SeeThroughParticle.TRANSLUCENT : super.getRenderType();
   }
}
