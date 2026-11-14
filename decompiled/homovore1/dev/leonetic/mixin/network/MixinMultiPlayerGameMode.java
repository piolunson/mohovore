package dev.leonetic.mixin.network;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.network.AttackBlockEvent;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_310;
import net.minecraft.class_636;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_636.class)
public class MixinMultiPlayerGameMode {
   @Unique
   private float homovore$origUseYaw;
   @Unique
   private float homovore$origUsePitch;

   @Inject(method = "method_2919", at = @At("HEAD"))
   private void homovore$useItemRotateHead(class_1657 player, class_1268 hand, CallbackInfoReturnable<class_1269> cir) {
      if (player == class_310.method_1551().field_1724) {
         if (Homovore.rotationManager != null && Homovore.rotationManager.isRotating()) {
            if (!Homovore.rotationManager.isBypassUseSpoof()) {
               this.homovore$origUseYaw = player.method_36454();
               this.homovore$origUsePitch = player.method_36455();
               player.method_36456(Homovore.rotationManager.getRotationYaw());
               player.method_36457(Homovore.rotationManager.getRotationPitch());
            }
         }
      }
   }

   @Inject(method = "method_2919", at = @At("RETURN"))
   private void homovore$useItemRotateReturn(class_1657 player, class_1268 hand, CallbackInfoReturnable<class_1269> cir) {
      if (player == class_310.method_1551().field_1724) {
         if (Homovore.rotationManager != null && Homovore.rotationManager.isRotating()) {
            if (!Homovore.rotationManager.isBypassUseSpoof()) {
               player.method_36456(this.homovore$origUseYaw);
               player.method_36457(this.homovore$origUsePitch);
            }
         }
      }
   }

   @Inject(method = "method_2910", at = @At("HEAD"), cancellable = true)
   private void homovore$onStartDestroyBlock(class_2338 pos, class_2350 direction, CallbackInfoReturnable<Boolean> cir) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 != null && mc.field_1724 != null) {
         AttackBlockEvent event = new AttackBlockEvent(pos, mc.field_1687.method_8320(pos), direction);
         if (Util.EVENT_BUS.post(event)) {
            cir.setReturnValue(false);
         }
      }
   }
}
