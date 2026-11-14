package dev.leonetic.mixin.network;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.TotemPopEvent;
import dev.leonetic.event.impl.network.ChatEvent;
import dev.leonetic.features.modules.movement.VelocityModule;
import dev.leonetic.features.modules.player.NoRotateModule;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_1309;
import net.minecraft.class_243;
import net.minecraft.class_2663;
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
public class MixinClientPlayNetworkHandler {
   @Unique
   private class_243 velocity$preExplosionVel = null;
   @Unique
   private float rotation$prevYaw;
   @Unique
   private float rotation$prevPitch;

   @Inject(method = "method_11157", at = @At("HEAD"))
   private void onHandleMovePlayerPre(class_2708 packet, CallbackInfo ci) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 != null && Homovore.rotationManager != null) {
         this.rotation$prevYaw = mc.field_1724.method_36454();
         this.rotation$prevPitch = mc.field_1724.method_36455();
      }
   }

   @Inject(method = "method_11157", at = @At("TAIL"))
   private void onHandleMovePlayerPost(class_2708 packet, CallbackInfo ci) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 != null && Homovore.rotationManager != null) {
         if (Homovore.rotationManager.isSilentActive() || NoRotateModule.isActive()) {
            mc.field_1724.method_36456(this.rotation$prevYaw);
            mc.field_1724.method_36457(this.rotation$prevPitch);
         }
      }
   }

   @Inject(method = "method_11132", at = @At("HEAD"), cancellable = true)
   private void cancelKnockback(class_2743 packet, CallbackInfo ci) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 != null) {
         VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
         if (velocity != null && velocity.shouldCancelKnockback() && packet.method_11818() == mc.field_1724.method_5628()) {
            ci.cancel();
         }
      }
   }

   @Inject(method = "method_11124", at = @At("HEAD"))
   private void saveExplosionVelocity(class_2664 packet, CallbackInfo ci) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 != null) {
         VelocityModule velocity = Homovore.moduleManager.getModuleByClass(VelocityModule.class);
         if (velocity != null && velocity.isEnabled() && velocity.explosions.getValue() && velocity.phaseConditionMet()) {
            this.velocity$preExplosionVel = mc.field_1724.method_18798();
         }
      }
   }

   @Inject(method = "method_11124", at = @At("TAIL"))
   private void restoreExplosionVelocity(class_2664 packet, CallbackInfo ci) {
      if (this.velocity$preExplosionVel != null) {
         class_310 mc = class_310.method_1551();
         if (mc.field_1724 != null) {
            mc.field_1724.method_18799(this.velocity$preExplosionVel);
         }

         this.velocity$preExplosionVel = null;
      }
   }

   @Inject(method = "method_45729", at = @At("HEAD"), cancellable = true)
   private void sendChatMessageHook(String content, CallbackInfo ci) {
      if (Util.EVENT_BUS.post(new ChatEvent(content))) {
         ci.cancel();
      }
   }

   @Inject(method = "method_11148", at = @At("HEAD"))
   private void handleEntityEventHook(class_2663 packet, CallbackInfo ci) {
      if (class_310.method_1551().method_18854()) {
         if (packet.method_11470() == 35) {
            if (class_310.method_1551().field_1687 != null) {
               if (packet.method_11469(class_310.method_1551().field_1687) instanceof class_1309 living) {
                  Util.EVENT_BUS.post(new TotemPopEvent(living));
               }
            }
         }
      }
   }
}
