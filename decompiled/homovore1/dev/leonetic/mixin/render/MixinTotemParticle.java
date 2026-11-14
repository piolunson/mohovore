package dev.leonetic.mixin.render;

import dev.leonetic.features.modules.render.PopEffectsModule;
import dev.leonetic.util.render.SeeThroughParticle;
import net.minecraft.class_2398;
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
public abstract class MixinTotemParticle extends class_708 {
   protected MixinTotemParticle(class_638 level, double x, double y, double z, class_4002 sprites, float scale) {
      super(level, x, y, z, sprites, scale);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void homovore$customizeTotem(
      class_638 level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, class_4002 sprites, CallbackInfo ci
   ) {
      PopEffectsModule module = PopEffectsModule.get();
      if (module != null && module.shouldCustomize(class_2398.field_11220)) {
         boolean yellowVariant = this.field_62633 >= 0.5F && this.field_62634 >= 0.5F;
         this.method_3093(module.getRgb(yellowVariant));
         this.field_17867 = this.field_17867 * module.getScale();
      }
   }

   public class_11941 method_74255() {
      PopEffectsModule module = PopEffectsModule.get();
      return module != null && module.shouldCustomize(class_2398.field_11220) && module.throughWalls.getValue()
         ? SeeThroughParticle.TRANSLUCENT_LAYER
         : super.method_74255();
   }
}
