package dev.leonetic.mixin.render.gui;

import dev.leonetic.event.impl.render.Render2DEvent;
import dev.leonetic.features.modules.render.NoRenderModule;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_1297;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_329;
import net.minecraft.class_332;
import net.minecraft.class_9779;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_329.class)
public abstract class MixinInGameHud {
   @Shadow
   @Final
   private static class_2960 field_27960;

   @Shadow
   protected abstract void method_31977(class_332 var1, class_2960 var2, float var3);

   @Inject(method = "method_1753", at = @At("RETURN"))
   public void render(class_332 context, class_9779 tickCounter, CallbackInfo ci) {
      if (!class_310.method_1551().field_1705.method_53531().method_53536()) {
         Render2DEvent event = new Render2DEvent(context, tickCounter.method_60637(true));
         Util.EVENT_BUS.post(event);
      }
   }

   @Inject(method = "method_1765", at = @At("HEAD"), cancellable = true)
   private void homovore$noPotions(class_332 context, class_9779 tickCounter, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noPotIcon.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_1736", at = @At("HEAD"), cancellable = true)
   private void homovore$noCrosshair(class_332 context, class_9779 tickCounter, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noCrosshair.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_1749", at = @At("HEAD"), cancellable = true)
   private void homovore$noItemName(class_332 context, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noItemName.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_1746", at = @At("HEAD"), cancellable = true)
   private void homovore$noPortal(class_332 context, float alpha, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noPortal.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_1735", at = @At("HEAD"), cancellable = true)
   private void homovore$noVignette(class_332 context, class_1297 entity, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noVignette.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "method_61980", at = @At("HEAD"), cancellable = true)
   private void homovore$noNausea(class_332 context, float distortionStrength, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noNausea.getValue())) {
         ci.cancel();
      }
   }

   @Redirect(
      method = "method_55798",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_329;method_31977(Lnet/minecraft/class_332;Lnet/minecraft/class_2960;F)V")
   )
   private void homovore$noCameraOverlay(class_329 instance, class_332 context, class_2960 texture, float alpha) {
      if (!NoRenderModule.isActive(m -> m.noPowderedSnow.getValue()) || !field_27960.equals(texture)) {
         if (!NoRenderModule.isActive(m -> m.noPumpkin.getValue()) || !texture.method_12832().contains("pumpkin")) {
            this.method_31977(context, texture, alpha);
         }
      }
   }
}
