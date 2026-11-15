package dev.piolunson.mixin.network;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.network.AttackBlockEvent;
import dev.piolunson.util.traits.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinMultiPlayerGameMode {
   @Unique
   private float homovore$origUseYaw;
   @Unique
   private float homovore$origUsePitch;

   @Inject(method = "interactItem", at = @At("HEAD"))
   private void homovore$useItemRotateHead(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (player == MinecraftClient.getInstance().player) {
         if (Homovore.rotationManager != null && Homovore.rotationManager.isRotating()) {
            if (!Homovore.rotationManager.isBypassUseSpoof()) {
               this.homovore$origUseYaw = player.getYaw();
               this.homovore$origUsePitch = player.getPitch();
               player.setYaw(Homovore.rotationManager.getRotationYaw());
               player.setPitch(Homovore.rotationManager.getRotationPitch());
            }
         }
      }
   }

   @Inject(method = "interactItem", at = @At("RETURN"))
   private void homovore$useItemRotateReturn(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (player == MinecraftClient.getInstance().player) {
         if (Homovore.rotationManager != null && Homovore.rotationManager.isRotating()) {
            if (!Homovore.rotationManager.isBypassUseSpoof()) {
               player.setYaw(this.homovore$origUseYaw);
               player.setPitch(this.homovore$origUsePitch);
            }
         }
      }
   }

   @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
   private void homovore$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         AttackBlockEvent event = new AttackBlockEvent(pos, mc.world.getBlockState(pos), direction);
         if (Util.EVENT_BUS.post(event)) {
            cir.setReturnValue(false);
         }
      }
   }
}
