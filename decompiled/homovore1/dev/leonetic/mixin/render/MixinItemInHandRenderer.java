package dev.leonetic.mixin.render;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.render.CrystalHandModule;
import dev.leonetic.features.modules.render.ShadersModule;
import dev.leonetic.features.modules.render.ViewModelModule;
import dev.leonetic.mixin.entity.EntityRotationAccessor;
import dev.leonetic.util.render.HandShaderRender;
import dev.leonetic.util.render.HandSilhouetteCollector;
import dev.leonetic.util.render.TeeSubmitCollector;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_759.class)
public class MixinItemInHandRenderer {
   @Unique
   private boolean handShader$capturing;
   @Unique
   private boolean viewModel$scaled;
   @Shadow
   private float field_4043;
   @Shadow
   private float field_4053;
   @Shadow
   private float field_4052;
   @Shadow
   private float field_4051;
   private float noSway$savedXBob;
   private float noSway$savedXBobO;
   private float noSway$savedYBob;
   private float noSway$savedYBobO;

   @Inject(method = "method_22976", at = @At("HEAD"))
   private void noSway$pre(CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.noSway.getValue())) {
         class_746 player = class_310.method_1551().field_1724;
         if (player != null) {
            EntityRotationAccessor acc = (EntityRotationAccessor)player;
            this.noSway$savedXBob = acc.homovore$getXBob();
            this.noSway$savedXBobO = acc.homovore$getXBobO();
            this.noSway$savedYBob = acc.homovore$getYBob();
            this.noSway$savedYBobO = acc.homovore$getYBobO();
            float xRot = player.method_36455();
            float yRot = player.method_36454();
            acc.homovore$setXBob(xRot);
            acc.homovore$setXBobO(xRot);
            acc.homovore$setYBob(yRot);
            acc.homovore$setYBobO(yRot);
         }
      }
   }

   @Inject(method = "method_22976", at = @At("RETURN"))
   private void noSway$post(CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.noSway.getValue())) {
         class_746 player = class_310.method_1551().field_1724;
         if (player != null) {
            EntityRotationAccessor acc = (EntityRotationAccessor)player;
            acc.homovore$setXBob(this.noSway$savedXBob);
            acc.homovore$setXBobO(this.noSway$savedXBobO);
            acc.homovore$setYBob(this.noSway$savedYBob);
            acc.homovore$setYBobO(this.noSway$savedYBobO);
         }
      }
   }

   @Inject(method = "method_65910", at = @At("HEAD"), cancellable = true)
   private void noSwap$instant(class_1799 visibleStack, class_1799 currentStack, CallbackInfoReturnable<Boolean> cir) {
      if (ViewModelModule.isActive(m -> m.noSwapAnimation.getValue())) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "method_3220", at = @At("TAIL"))
   private void oldAnimation$tick(CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.oldAnimation.getValue())) {
         this.field_4043 = 1.0F;
         this.field_4053 = 1.0F;
         this.field_4052 = 1.0F;
         this.field_4051 = 1.0F;
      }
   }

   @Inject(
      method = "method_3228(Lnet/minecraft/class_742;FFLnet/minecraft/class_1268;FLnet/minecraft/class_1799;FLnet/minecraft/class_4587;Lnet/minecraft/class_11659;I)V",
      at = @At("HEAD")
   )
   private void viewModel$push(
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
      this.viewModel$scaled = false;
      ViewModelModule mod = ViewModelModule.getInstance();
      if (mod != null && mod.isEnabled()) {
         this.viewModel$scaled = true;
         class_1306 arm = hand == class_1268.field_5808 ? player.method_6068() : player.method_6068().method_5928();
         float sign = arm == class_1306.field_6183 ? 1.0F : -1.0F;
         poseStack.method_22903();
         poseStack.method_22904(mod.posX.getValue() * sign, mod.posY.getValue(), mod.posZ.getValue());
         float s = mod.scale.getValue().floatValue();
         poseStack.method_22904(0.56 * sign, -0.52, -0.72);
         poseStack.method_22905(s, s, s);
         poseStack.method_22904(-0.56 * sign, 0.52, 0.72);
      }
   }

   @Inject(
      method = "method_3228(Lnet/minecraft/class_742;FFLnet/minecraft/class_1268;FLnet/minecraft/class_1799;FLnet/minecraft/class_4587;Lnet/minecraft/class_11659;I)V",
      at = @At("RETURN")
   )
   private void viewModel$pop(
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
      if (this.viewModel$scaled) {
         this.viewModel$scaled = false;
         poseStack.method_22909();
      }
   }

   @ModifyArg(
      method = "method_22976",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_759;method_3228(Lnet/minecraft/class_742;FFLnet/minecraft/class_1268;FLnet/minecraft/class_1799;FLnet/minecraft/class_4587;Lnet/minecraft/class_11659;I)V"
      ),
      index = 5
   )
   private class_1799 crystalHand$modifyRenderedItem(class_1799 original) {
      CrystalHandModule mod = Homovore.moduleManager.getModuleByClass(CrystalHandModule.class);
      return mod != null && mod.isEnabled() ? mod.getDisplayStack(original) : original;
   }

   @ModifyVariable(method = "method_22976", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private class_11659 handShader$wrapCollector(class_11659 original) {
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

   @Inject(method = "method_22976", at = @At("TAIL"))
   private void handShader$flush(float partialTick, class_4587 poseStack, class_11659 collector, class_746 player, int light, CallbackInfo ci) {
      if (this.handShader$capturing) {
         this.handShader$capturing = false;
         HandShaderRender.capturePaused = false;
         HandShaderRender.flush();
      }
   }
}
