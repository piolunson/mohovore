package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.FixesModule;
import net.minecraft.class_1796;
import net.minecraft.class_310;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(class_1796.class)
public abstract class ItemCooldownsMixin {
   @ModifyVariable(method = "method_7906(Lnet/minecraft/class_2960;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private int homovore$syncCooldownToTps(int ticks) {
      class_746 player = class_310.method_1551().field_1724;
      return player != null && player.method_7357() == this ? FixesModule.syncCooldown(ticks) : ticks;
   }
}
