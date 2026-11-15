package dev.piolunson.mixin.render;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.render.NoRenderModule;
import dev.piolunson.features.modules.render.ShadersModule;
import dev.piolunson.util.render.HandShaderChain;
import dev.piolunson.util.render.HandShaderRender;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
   @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/RenderDispatcher;render()V", shift = Shift.AFTER, ordinal = 0))
   private void handShader$composite(RenderTickCounter deltaTracker, CallbackInfo ci) {
      ShadersModule mod = Homovore.moduleManager.getModuleByClass(ShadersModule.class);
      if (mod != null && mod.wantsHandShader()) {
         HandShaderRender.composite(
            HandShaderChain.get(
               mod.handOutline.getValue(),
               mod.getHandThickness(),
               mod.handFill.getValue(),
               mod.handGlow.getValue(),
               mod.getHandGlowRadius(),
               mod.getHandGlowIntensity()
            )
         );
      }
   }

   @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
   private void homovore$noTotem(ItemStack floatingItem, CallbackInfo ci) {
      if (floatingItem.isOf(Items.TOTEM_OF_UNDYING) && NoRenderModule.isActive(m -> m.noTotem.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
   private void homovore$noBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBob.getValue())) {
         ci.cancel();
      }
   }

   @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
   private void homovore$noTilt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noTilt.getValue())) {
         ci.cancel();
      }
   }
}
