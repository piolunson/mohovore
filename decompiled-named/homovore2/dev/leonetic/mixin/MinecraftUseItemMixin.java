package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.UseItemEvent;
import dev.leonetic.feature.module.impl.FastUseModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftUseItemMixin {
   @Shadow
   private int itemUseCooldown;

   @Inject(
      method = "doItemUse",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;itemUseCooldown:I", opcode = 181, shift = Shift.AFTER),
      cancellable = true
   )
   private void homovore$onStartUseItem(CallbackInfo ci) {
      if (FastUseModule.shouldSkipDelay()) {
         this.itemUseCooldown = 0;
      }

      if (Homovore2Client.EVENT_BUS.post(new UseItemEvent())) {
         ci.cancel();
      }
   }
}
