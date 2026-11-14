package dev.leonetic.mixin;

import dev.leonetic.Homovore2Client;
import dev.leonetic.event.impl.render.RenderEntityEvent;
import dev.leonetic.event.impl.render.RenderFeatureEvent;
import dev.leonetic.render.EntityRenderStateAccess;
import net.minecraft.class_10017;
import net.minecraft.class_1297;
import net.minecraft.class_2561;
import net.minecraft.class_4604;
import net.minecraft.class_897;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_897.class)
public class EntityRendererMixin {
   @Inject(method = "method_3933", at = @At("HEAD"), cancellable = true)
   private void homovore$shouldRender(class_1297 entity, class_4604 frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
      if (Homovore2Client.EVENT_BUS.post(new RenderEntityEvent(entity))) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "method_62425(Lnet/minecraft/class_1297;F)Lnet/minecraft/class_10017;", at = @At("RETURN"))
   private void homovore$rememberEntity(class_1297 entity, float partialTick, CallbackInfoReturnable<class_10017> cir) {
      ((EntityRenderStateAccess)cir.getReturnValue()).homovore$setEntity(entity);
   }

   @Inject(method = "method_62426", at = @At("HEAD"), cancellable = true)
   private void homovore$nametags(CallbackInfoReturnable<class_2561> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.NAMETAGS)) {
         cir.setReturnValue(null);
      }
   }
}
