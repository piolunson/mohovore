package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.FixesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownsMixin {
   @ModifyVariable(method = "set(Lnet/minecraft/util/Identifier;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private int homovore$syncCooldownToTps(int ticks) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return player != null && player.getItemCooldownManager() == this ? FixesModule.syncCooldown(ticks) : ticks;
   }
}
