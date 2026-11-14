package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.CrystalHandModule;
import dev.leonetic.features.modules.render.ShadersModule;
import dev.leonetic.features.modules.render.ViewModelModule;
import dev.leonetic.mixin.entity.EntityRotationAccessor;
import dev.leonetic.util.render.HandShaderRender;
import dev.leonetic.util.render.HandSilhouetteCollector;
import dev.leonetic.util.render.TeeSubmitCollector;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeldItemRenderer.class)
public class MixinItemInHandRenderer {
   @Unique
   private boolean handShader$capturing;
   @Unique
   private boolean viewModel$scaled;
   @Shadow
   private float equipProgressMainHand;
   @Shadow
   private float lastEquipProgressMainHand;
   @Shadow
   private float equipProgressOffHand;
   @Shadow
   private float lastEquipProgressOffHand;
   private float noSway$savedXBob;
   private float noSway$savedXBobO;
   private float noSway$savedYBob;
   private float noSway$savedYBobO;

   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
   private void noSway$pre(CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.noSway.getValue())) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            EntityRotationAccessor acc = (EntityRotationAccessor)player;
            this.noSway$savedXBob = acc.homovore$getXBob();
            this.noSway$savedXBobO = acc.homovore$getXBobO();
            this.noSway$savedYBob = acc.homovore$getYBob();
            this.noSway$savedYBobO = acc.homovore$getYBobO();
            float xRot = player.getPitch();
            float yRot = player.getYaw();
            acc.homovore$setXBob(xRot);
            acc.homovore$setXBobO(xRot);
            acc.homovore$setYBob(yRot);
            acc.homovore$setYBobO(yRot);
         }
      }
   }

   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("RETURN"))
   private void noSway$post(CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.noSway.getValue())) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            EntityRotationAccessor acc = (EntityRotationAccessor)player;
            acc.homovore$setXBob(this.noSway$savedXBob);
            acc.homovore$setXBobO(this.noSway$savedXBobO);
            acc.homovore$setYBob(this.noSway$savedYBob);
            acc.homovore$setYBobO(this.noSway$savedYBobO);
         }
      }
   }

   @Inject(method = "shouldSkipHandAnimationOnSwap", at = @At("HEAD"), cancellable = true)
   private void noSwap$instant(ItemStack visibleStack, ItemStack currentStack, CallbackInfoReturnable<Boolean> cir) {
      if (ViewModelModule.isActive(m -> m.noSwapAnimation.getValue())) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "updateHeldItems", at = @At("TAIL"))
   private void oldAnimation$tick(CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.oldAnimation.getValue())) {
         this.equipProgressMainHand = 1.0F;
         this.lastEquipProgressMainHand = 1.0F;
         this.equipProgressOffHand = 1.0F;
         this.lastEquipProgressOffHand = 1.0F;
      }
   }

   @Inject(
      method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
      at = @At("HEAD")
   )
   private void viewModel$push(
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
      this.viewModel$scaled = false;
      ViewModelModule mod = ViewModelModule.getInstance();
      if (mod != null && mod.isEnabled()) {
         this.viewModel$scaled = true;
         Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         float sign = arm == Arm.RIGHT ? 1.0F : -1.0F;
         poseStack.push();
         poseStack.translate(mod.posX.getValue() * sign, mod.posY.getValue(), mod.posZ.getValue());
         float s = mod.scale.getValue().floatValue();
         poseStack.translate(0.56 * sign, -0.52, -0.72);
         poseStack.scale(s, s, s);
         poseStack.translate(-0.56 * sign, 0.52, 0.72);
      }
   }

   @Inject(
      method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
      at = @At("RETURN")
   )
   private void viewModel$pop(
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
      if (this.viewModel$scaled) {
         this.viewModel$scaled = false;
         poseStack.pop();
      }
   }

   @ModifyArg(
      method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"
      ),
      index = 5
   )
   private ItemStack crystalHand$modifyRenderedItem(ItemStack original) {
      CrystalHandModule mod = Homovore.moduleManager.getModuleByClass(CrystalHandModule.class);
      return mod != null && mod.isEnabled() ? mod.getDisplayStack(original) : original;
   }

   @ModifyVariable(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private OrderedRenderCommandQueue handShader$wrapCollector(OrderedRenderCommandQueue original) {
      this.handShader$capturing = false;
      ShadersModule mod = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (mod != null && mod.wantsHandShader() && (mod.handOutline.getValue() || mod.handFill.getValue())) {
         HandSilhouetteCollector secondary = HandShaderRender.beginCapture(mod.getHandRgb());
         if (secondary == null) {
            return original;
         }

         this.handShader$capturing = true;
         HandShaderRender.capturePaused = false;
         return new TeeSubmitCollector(original, secondary);
      } else {
         return original;
      }
   }

   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("TAIL"))
   private void handShader$flush(
      float partialTick, MatrixStack poseStack, OrderedRenderCommandQueue collector, ClientPlayerEntity player, int light, CallbackInfo ci
   ) {
      if (this.handShader$capturing) {
         this.handShader$capturing = false;
         HandShaderRender.capturePaused = false;
         HandShaderRender.flush();
      }
   }
}
