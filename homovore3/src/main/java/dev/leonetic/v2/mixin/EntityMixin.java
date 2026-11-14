package dev.leonetic.v2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.leonetic.v2.Homovore2Client;
import dev.leonetic.v2.event.impl.render.RenderFeatureEvent;
import dev.leonetic.v2.feature.module.impl.BounceModule;
import dev.leonetic.v2.feature.module.impl.ChamsModule;
import dev.leonetic.v2.feature.module.impl.VelocityModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
   @Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
   private void homovore$bounceStandingPose(CallbackInfoReturnable<EntityPose> cir) {
      if (this.homovore$isBouncing()) {
         cir.setReturnValue(EntityPose.STANDING);
      }
   }

   @Inject(method = "isSprinting", at = @At("HEAD"), cancellable = true)
   private void homovore$bounceSprinting(CallbackInfoReturnable<Boolean> cir) {
      if (this.homovore$isBouncing()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void homovore$bouncePush(Entity entity, CallbackInfo ci) {
      if (this.homovore$isBouncing() && entity != (Object)this) {
         ci.cancel();
      }
   }

   @WrapOperation(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
   private void homovore$velocityEntityPush(Entity pushed, double x, double y, double z, Operation<Void> original) {
      if (pushed == MinecraftClient.getInstance().player) {
         VelocityModule velocity = Homovore2Client.modules().get(VelocityModule.class);
         if (velocity != null && velocity.shouldCancelEntityPush()) {
            return;
         }
      }

      original.call(new Object[]{pushed, x, y, z});
   }

   private boolean homovore$isBouncing() {
      return (Object)this == MinecraftClient.getInstance().player && BounceModule.flying() != null;
   }

   @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
   private void homovore$invisibility(CallbackInfoReturnable<Boolean> cir) {
      if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.INVISIBILITY)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
   private void homovore$glowing(CallbackInfoReturnable<Boolean> cir) {
      ChamsModule chams = ChamsModule.active();
      if (chams != null && chams.shades((Entity)(Object)this)) {
         cir.setReturnValue(true);
      } else if (RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.GLOWING)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
   private void homovore$teamColor(CallbackInfoReturnable<Integer> cir) {
      ChamsModule chams = ChamsModule.active();
      if (chams != null && chams.shades((Entity)(Object)this)) {
         cir.setReturnValue(chams.colorOf((Entity)(Object)this));
      }
   }
}
