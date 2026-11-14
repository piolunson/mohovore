package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.ViewModelModule;
import net.minecraft.class_11659;
import net.minecraft.class_1268;
import net.minecraft.class_1306;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_742;
import net.minecraft.class_746;
import net.minecraft.class_759;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_759.class)
public class ItemInHandRendererMixin {
   @Shadow
   private float field_4043;
   @Shadow
   private float field_4053;
   @Shadow
   private float field_4052;
   @Shadow
   private float field_4051;
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

   @Inject(method = "method_22976", at = @At("HEAD"))
   private void homovore$freezeSway(float partialTick, class_4587 poseStack, class_11659 collector, class_746 renderedPlayer, int light, CallbackInfo ci) {
      this.homovore$swayFrozen = false;
      if (ViewModelModule.isActive(module -> module.noSway.getValue())) {
         class_746 player = class_310.method_1551().field_1724;
         if (player != null) {
            this.homovore$savedXBob = player.field_3916;
            this.homovore$savedXBobO = player.field_3914;
            this.homovore$savedYBob = player.field_3932;
            this.homovore$savedYBobO = player.field_3931;
            this.homovore$swayFrozen = true;
            float xRot = player.method_36455();
            float yRot = player.method_36454();
            player.field_3916 = xRot;
            player.field_3914 = xRot;
            player.field_3932 = yRot;
            player.field_3931 = yRot;
         }
      }
   }

   @Inject(method = "method_22976", at = @At("RETURN"))
   private void homovore$restoreSway(float partialTick, class_4587 poseStack, class_11659 collector, class_746 renderedPlayer, int light, CallbackInfo ci) {
      if (this.homovore$swayFrozen) {
         this.homovore$swayFrozen = false;
         class_746 player = class_310.method_1551().field_1724;
         if (player != null) {
            player.field_3916 = this.homovore$savedXBob;
            player.field_3914 = this.homovore$savedXBobO;
            player.field_3932 = this.homovore$savedYBob;
            player.field_3931 = this.homovore$savedYBobO;
         }
      }
   }

   @Inject(method = "method_65910", at = @At("HEAD"), cancellable = true)
   private void homovore$instantSwap(class_1799 visibleStack, class_1799 currentStack, CallbackInfoReturnable<Boolean> cir) {
      if (ViewModelModule.isActive(module -> module.noSwapAnimation.getValue())) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "method_3220", at = @At("TAIL"))
   private void homovore$pinHandHeights(CallbackInfo ci) {
      if (ViewModelModule.isActive(module -> module.oldAnimation.getValue())) {
         this.field_4043 = 1.0F;
         this.field_4053 = 1.0F;
         this.field_4052 = 1.0F;
         this.field_4051 = 1.0F;
      }
   }

   @Inject(method = "method_3228", at = @At("HEAD"))
   private void homovore$applyTransform(
      class_742 player,
      float partialTick,
      float interpolatedPitch,
      class_1268 hand,
      float swingProgress,
      class_1799 stack,
      float equipProgress,
      class_4587 poseStack,
      class_11659 collector,
      int light,
      CallbackInfo ci
   ) {
      this.homovore$pushedPose = false;
      ViewModelModule module = ViewModelModule.active();
      if (module != null) {
         class_1306 arm = hand == class_1268.field_5808 ? player.method_6068() : player.method_6068().method_5928();
         float sign = arm == class_1306.field_6183 ? 1.0F : -1.0F;
         this.homovore$pushedPose = true;
         poseStack.method_22903();
         poseStack.method_22904(module.posX.getValue() * sign, module.posY.getValue(), module.posZ.getValue());
         float scale = module.scale.getValue().floatValue();
         poseStack.method_22904(0.56 * sign, -0.52, -0.72);
         poseStack.method_22905(scale, scale, scale);
         poseStack.method_22904(-0.56 * sign, 0.52, 0.72);
      }
   }

   @Inject(method = "method_3228", at = @At("RETURN"))
   private void homovore$popTransform(
      class_742 player,
      float partialTick,
      float interpolatedPitch,
      class_1268 hand,
      float swingProgress,
      class_1799 stack,
      float equipProgress,
      class_4587 poseStack,
      class_11659 collector,
      int light,
      CallbackInfo ci
   ) {
      if (this.homovore$pushedPose) {
         this.homovore$pushedPose = false;
         poseStack.method_22909();
      }
   }
}
