package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_5819;
import net.minecraft.class_638;
import net.minecraft.class_2338.class_2339;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(class_638.class)
public class ClientLevelMixin {
   @Redirect(
      method = "method_2941",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_638;method_2943(IIIILnet/minecraft/class_5819;Lnet/minecraft/class_2248;Lnet/minecraft/class_2338$class_2339;)V"
      )
   )
   private void homovore$barrierInvisibility(class_638 level, int x, int y, int z, int radius, class_5819 random, class_2248 heldBlock, class_2339 pos) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.BARRIER_INVISIBILITY)) {
         heldBlock = class_2246.field_10499;
      }

      level.method_2943(x, y, z, radius, random, heldBlock, pos);
   }
}
