package dev.leonetic.mixin.network;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.TotemPopEvent;
import dev.leonetic.event.impl.network.ChatEvent;
import dev.leonetic.features.modules.movement.VelocityModule;
import dev.leonetic.features.modules.player.NoRotateModule;
import dev.leonetic.util.traits.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
   @Unique
   private Vec3d velocity$preExplosionVel = null;
   @Unique
   private float rotation$prevYaw;
   @Unique
   private float rotation$prevPitch;

   @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
   private void onHandleMovePlayerPre(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && Homovore.rotationManager != null) {
         this.rotation$prevYaw = mc.player.getYaw();
         this.rotation$prevPitch = mc.player.getPitch();
      }
   }

   @Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
   private void onHandleMovePlayerPost(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && Homovore.rotationManager != null) {
         if (Homovore.rotationManager.isSilentActive() || NoRotateModule.isActive()) {
            mc.player.setYaw(this.rotation$prevYaw);
            mc.player.setPitch(this.rotation$prevPitch);
         }
      }
   }

   @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
   private void cancelKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
         if (velocity != null && velocity.shouldCancelKnockback() && packet.getEntityId() == mc.player.getId()) {
            ci.cancel();
         }
      }
   }

   @Inject(method = "onExplosion", at = @At("HEAD"))
   private void saveExplosionVelocity(ExplosionS2CPacket packet, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
         if (velocity != null && velocity.isEnabled() && velocity.explosions.getValue() && velocity.phaseConditionMet()) {
            this.velocity$preExplosionVel = mc.player.getVelocity();
         }
      }
   }

   @Inject(method = "onExplosion", at = @At("TAIL"))
   private void restoreExplosionVelocity(ExplosionS2CPacket packet, CallbackInfo ci) {
      if (this.velocity$preExplosionVel != null) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            mc.player.setVelocity(this.velocity$preExplosionVel);
         }

         this.velocity$preExplosionVel = null;
      }
   }

   @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
   private void sendChatMessageHook(String content, CallbackInfo ci) {
      if (Util.EVENT_BUS.post(new ChatEvent(content))) {
         ci.cancel();
      }
   }

   @Inject(method = "onEntityStatus", at = @At("HEAD"))
   private void handleEntityEventHook(EntityStatusS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         if (packet.getStatus() == 35) {
            if (MinecraftClient.getInstance().world != null) {
               if (packet.getEntity(MinecraftClient.getInstance().world) instanceof LivingEntity living) {
                  Util.EVENT_BUS.post(new TotemPopEvent(living));
               }
            }
         }
      }
   }
}
