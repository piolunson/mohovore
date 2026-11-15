package dev.piolunson.v2.mixin;

import dev.piolunson.v2.Homovore2Client;
import dev.piolunson.v2.event.impl.render.RenderEntityEvent;
import dev.piolunson.v2.event.impl.render.RenderFeatureEvent;
import dev.piolunson.v2.render.EntityRenderStateAccess;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
   @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
   private void homovore$shouldRender(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
      if (Homovore2Client.EVENT_BUS.post(new RenderEntityEvent(entity))) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;", at = @At("RETURN"))
   private void homovore$rememberEntity(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
      ((EntityRenderStateAccess)cir.getReturnValue()).homovore$setEntity(entity);
   }

   @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
   private void homovore$nametags(CallbackInfoReturnable<Text> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.NAMETAGS)) {
         cir.setReturnValue(null);
      }
   }
}
