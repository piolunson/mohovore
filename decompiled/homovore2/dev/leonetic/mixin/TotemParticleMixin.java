package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.PopChamsModule;
import dev.leonetic.render.SeeThroughParticle;
import net.minecraft.class_4002;
import net.minecraft.class_638;
import net.minecraft.class_708;
import net.minecraft.class_734;
import net.minecraft.class_3940.class_11941;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_734.class)
public abstract class TotemParticleMixin extends class_708 {
   private TotemParticleMixin(class_638 level, double x, double y, double z, class_4002 sprites, float gravity) {
      super(level, x, y, z, sprites, gravity);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void homovore$recolour(
      class_638 level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, class_4002 sprites, CallbackInfo ci
   ) {
      PopChamsModule module = PopChamsModule.active();
      if (module != null && module.customizesParticles()) {
         boolean pale = this.field_62633 >= 0.5F && this.field_62634 >= 0.5F;
         this.method_3093(module.particleColor(pale));
         this.field_17867 = this.field_17867 * module.particleScale();
      }
   }

   public class_11941 method_74255() {
      PopChamsModule module = PopChamsModule.active();
      return module != null && module.customizesParticles() && module.particlesThroughWalls.getValue() ? SeeThroughParticle.TRANSLUCENT : super.method_74255();
   }
}
