package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.EntityAddedEvent;
import dev.piolunson.v2.feature.module.impl.NoRotateModule;
import dev.piolunson.v2.feature.module.impl.VelocityModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
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
public abstract class ClientPacketListenerMixin {
   @Unique
   private Vec3d homovore$preExplosionVelocity;
   @Unique
   private float homovore$preTeleportYaw;
   @Unique
   private float homovore$preTeleportPitch;

   @Inject(method = "onEntitySpawn", at = @At("TAIL"))
   private void homovore$entityAdded(EntitySpawnS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread()) {
         Homovore2Client.EVENT_BUS.post(new EntityAddedEvent(packet));
      }
   }

   @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
   private void homovore$saveRotation(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.player != null) {
         this.homovore$preTeleportYaw = client.player.getYaw();
         this.homovore$preTeleportPitch = client.player.getPitch();
      }
   }

   @Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
   private void homovore$restoreRotation(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.player != null && NoRotateModule.isActive()) {
         client.player.setYaw(this.homovore$preTeleportYaw);
         client.player.setPitch(this.homovore$preTeleportPitch);
      }
   }

   @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
   private void homovore$adjustKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.player != null && packet.getEntityId() == client.player.getId()) {
         VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
         if (velocity != null) {
            Vec3d adjusted = velocity.adjustGrimKnockback(packet.getVelocity());
            if (adjusted != null) {
               client.player.setVelocityClient(adjusted);
               ci.cancel();
            } else if (velocity.shouldCancelKnockback()) {
               ci.cancel();
            }
         }
      }
   }

   @Inject(method = "onExplosion", at = @At("HEAD"))
   private void homovore$saveExplosionVelocity(ExplosionS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread() && client.player != null) {
         VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
         if (velocity != null && velocity.shouldCancelExplosion()) {
            this.homovore$preExplosionVelocity = client.player.getVelocity();
         }
      }
   }

   @Inject(method = "onExplosion", at = @At("TAIL"))
   private void homovore$restoreExplosionVelocity(ExplosionS2CPacket packet, CallbackInfo ci) {
      Vec3d before = this.homovore$preExplosionVelocity;
      this.homovore$preExplosionVelocity = null;
      if (before != null) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player != null) {
            client.player.setVelocity(before);
         }
      }
   }
}
