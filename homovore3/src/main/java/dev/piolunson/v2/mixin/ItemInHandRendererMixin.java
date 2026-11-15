package dev.piolunson.v2.mixin;

import dev.piolunson.v2.feature.module.impl.ViewModelModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeldItemRenderer.class)
public class ItemInHandRendererMixin {
   @Shadow
   private float equipProgressMainHand;
   @Shadow
   private float lastEquipProgressMainHand;
   @Shadow
   private float equipProgressOffHand;
   @Shadow
   private float lastEquipProgressOffHand;
   @Unique
   private boolean homovore$pushedPose;
   @Unique
   private float homovore$savedXBob;
   @Unique
   private float homovore$savedXBobO;
   @Unique
   private float homovore$savedYBob;
   @Unique
   private float homovore$savedYBobO;
   @Unique
   private boolean homovore$swayFrozen;

   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
   private void homovore$freezeSway(
      float partialTick, MatrixStack poseStack, OrderedRenderCommandQueue collector, ClientPlayerEntity renderedPlayer, int light, CallbackInfo ci
   ) {
      this.homovore$swayFrozen = false;
      if (ViewModelModule.isActive(module -> module.noSway.getValue())) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            this.homovore$savedXBob = player.renderPitch;
            this.homovore$savedXBobO = player.lastRenderPitch;
            this.homovore$savedYBob = player.renderYaw;
            this.homovore$savedYBobO = player.lastRenderYaw;
            this.homovore$swayFrozen = true;
            float xRot = player.getPitch();
            float yRot = player.getYaw();
            player.renderPitch = xRot;
            player.lastRenderPitch = xRot;
            player.renderYaw = yRot;
            player.lastRenderYaw = yRot;
         }
      }
   }

   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("RETURN"))
   private void homovore$restoreSway(
      float partialTick, MatrixStack poseStack, OrderedRenderCommandQueue collector, ClientPlayerEntity renderedPlayer, int light, CallbackInfo ci
   ) {
      if (this.homovore$swayFrozen) {
         this.homovore$swayFrozen = false;
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            player.renderPitch = this.homovore$savedXBob;
            player.lastRenderPitch = this.homovore$savedXBobO;
            player.renderYaw = this.homovore$savedYBob;
            player.lastRenderYaw = this.homovore$savedYBobO;
         }
      }
   }

   @Inject(method = "shouldSkipHandAnimationOnSwap", at = @At("HEAD"), cancellable = true)
   private void homovore$instantSwap(ItemStack visibleStack, ItemStack currentStack, CallbackInfoReturnable<Boolean> cir) {
      if (ViewModelModule.isActive(module -> module.noSwapAnimation.getValue())) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "updateHeldItems", at = @At("TAIL"))
   private void homovore$pinHandHeights(CallbackInfo ci) {
      if (ViewModelModule.isActive(module -> module.oldAnimation.getValue())) {
         this.equipProgressMainHand = 1.0F;
         this.lastEquipProgressMainHand = 1.0F;
         this.equipProgressOffHand = 1.0F;
         this.lastEquipProgressOffHand = 1.0F;
      }
   }

   @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
   private void homovore$applyTransform(
      AbstractClientPlayerEntity player,
      float partialTick,
      float interpolatedPitch,
      Hand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      MatrixStack poseStack,
      OrderedRenderCommandQueue collector,
      int light,
      CallbackInfo ci
   ) {
      this.homovore$pushedPose = false;
      ViewModelModule module = ViewModelModule.active();
      if (module != null) {
         Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         float sign = arm == Arm.RIGHT ? 1.0F : -1.0F;
         this.homovore$pushedPose = true;
         poseStack.push();
         poseStack.translate(module.posX.getValue() * sign, module.posY.getValue(), module.posZ.getValue());
         float scale = module.scale.getValue().floatValue();
         poseStack.translate(0.56 * sign, -0.52, -0.72);
         poseStack.scale(scale, scale, scale);
         poseStack.translate(-0.56 * sign, 0.52, 0.72);
      }
   }

   @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
   private void homovore$popTransform(
      AbstractClientPlayerEntity player,
      float partialTick,
      float interpolatedPitch,
      Hand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      MatrixStack poseStack,
      OrderedRenderCommandQueue collector,
      int light,
      CallbackInfo ci
   ) {
      if (this.homovore$pushedPose) {
         this.homovore$pushedPose = false;
         poseStack.pop();
      }
   }
}
