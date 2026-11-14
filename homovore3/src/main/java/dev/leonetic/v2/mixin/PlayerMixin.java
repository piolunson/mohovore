package dev.leonetic.v2.mixin;

import dev.leonetic.v2.feature.module.impl.FixesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerMixin {
   @Inject(
      method = "knockbackTarget",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"),
      cancellable = true
   )
   private void homovore$keepAttackVelocity(CallbackInfo ci) {
      if ((Object)this == MinecraftClient.getInstance().player && FixesModule.cancelsAttackVelocity()) {
         ci.cancel();
      }
   }
}
