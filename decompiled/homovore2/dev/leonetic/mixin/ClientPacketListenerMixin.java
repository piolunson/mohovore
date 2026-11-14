package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.EntityAddedEvent;
import dev.leonetic.feature.module.impl.NoRotateModule;
import dev.leonetic.feature.module.impl.VelocityModule;
import net.minecraft.class_243;
import net.minecraft.class_2604;
import net.minecraft.class_2664;
import net.minecraft.class_2708;
import net.minecraft.class_2743;
import net.minecraft.class_310;
import net.minecraft.class_634;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_634.class)
public abstract class ClientPacketListenerMixin {
   @Unique
   private class_243 homovore$preExplosionVelocity;
   @Unique
   private float homovore$preTeleportYaw;
   @Unique
   private float homovore$preTeleportPitch;

   @Inject(method = "method_11112", at = @At("TAIL"))
   private void homovore$entityAdded(class_2604 packet, CallbackInfo ci) {
      class_310 client = class_310.method_1551();
      if (client.method_18854()) {
         Homovore2Client.EVENT_BUS.post(new EntityAddedEvent(packet));
      }
   }

   @Inject(method = "method_11157", at = @At("HEAD"))
   private void homovore$saveRotation(class_2708 packet, CallbackInfo ci) {
      class_310 client = class_310.method_1551();
      if (client.method_18854() && client.field_1724 != null) {
         this.homovore$preTeleportYaw = client.field_1724.method_36454();
         this.homovore$preTeleportPitch = client.field_1724.method_36455();
      }
   }

   @Inject(method = "method_11157", at = @At("TAIL"))
   private void homovore$restoreRotation(class_2708 packet, CallbackInfo ci) {
      class_310 client = class_310.method_1551();
      if (client.method_18854() && client.field_1724 != null && NoRotateModule.isActive()) {
         client.field_1724.method_36456(this.homovore$preTeleportYaw);
         client.field_1724.method_36457(this.homovore$preTeleportPitch);
      }
   }

   @Inject(method = "method_11132", at = @At("HEAD"), cancellable = true)
   private void homovore$adjustKnockback(class_2743 packet, CallbackInfo ci) {
      class_310 client = class_310.method_1551();
      if (client.method_18854() && client.field_1724 != null && packet.method_11818() == client.field_1724.method_5628()) {
         VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
         if (velocity != null) {
            class_243 adjusted = velocity.adjustGrimKnockback(packet.method_73085());
            if (adjusted != null) {
               client.field_1724.method_5750(adjusted);
               ci.cancel();
            } else if (velocity.shouldCancelKnockback()) {
               ci.cancel();
            }
         }
      }
   }

   @Inject(method = "method_11124", at = @At("HEAD"))
   private void homovore$saveExplosionVelocity(class_2664 packet, CallbackInfo ci) {
      class_310 client = class_310.method_1551();
      if (client.method_18854() && client.field_1724 != null) {
         VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
         if (velocity != null && velocity.shouldCancelExplosion()) {
            this.homovore$preExplosionVelocity = client.field_1724.method_18798();
         }
      }
   }

   @Inject(method = "method_11124", at = @At("TAIL"))
   private void homovore$restoreExplosionVelocity(class_2664 packet, CallbackInfo ci) {
      class_243 before = this.homovore$preExplosionVelocity;
      this.homovore$preExplosionVelocity = null;
      if (before != null) {
         class_310 client = class_310.method_1551();
         if (client.field_1724 != null) {
            client.field_1724.method_18799(before);
         }
      }
   }
}
