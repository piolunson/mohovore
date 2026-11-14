package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.PopEffectsModule;
import dev.leonetic.util.render.SeeThroughParticle;
import net.minecraft.client.particle.AnimatedParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TotemParticle.class)
public abstract class MixinTotemParticle extends AnimatedParticle {
   protected MixinTotemParticle(ClientWorld level, double x, double y, double z, SpriteProvider sprites, float scale) {
      super(level, x, y, z, sprites, scale);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void homovore$customizeTotem(
      ClientWorld level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteProvider sprites, CallbackInfo ci
   ) {
      PopEffectsModule module = PopEffectsModule.get();
      if (module != null && module.shouldCustomize(ParticleTypes.TOTEM_OF_UNDYING)) {
         boolean yellowVariant = this.red >= 0.5F && this.green >= 0.5F;
         this.setColor(module.getRgb(yellowVariant));
         this.scale = this.scale * module.getScale();
      }
   }

   public RenderType getRenderType() {
      PopEffectsModule module = PopEffectsModule.get();
      return module != null && module.shouldCustomize(ParticleTypes.TOTEM_OF_UNDYING) && module.throughWalls.getValue()
         ? SeeThroughParticle.TRANSLUCENT_LAYER
         : super.getRenderType();
   }
}
