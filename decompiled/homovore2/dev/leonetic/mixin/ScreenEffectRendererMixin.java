package dev.leonetic.mixin;

import dev.leonetic.event.impl.render.RenderFeatureEvent;
import net.minecraft.class_1058;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_4603;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(class_4603.class)
public class ScreenEffectRendererMixin {
   @Invoker("method_23070")
   private static void homovore$invokeRenderFire(class_4587 poseStack, class_4597 buffers, class_1058 sprite) {
      throw new AssertionError();
   }

   @Invoker("method_23069")
   private static void homovore$invokeRenderWater(class_310 client, class_4587 poseStack, class_4597 buffers) {
      throw new AssertionError();
   }

   @Invoker("method_23068")
   private static void homovore$invokeRenderTex(class_1058 sprite, class_4587 poseStack, class_4597 buffers) {
      throw new AssertionError();
   }

   @Redirect(
      method = "method_23067",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4603;method_23070(Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;Lnet/minecraft/class_1058;)V"
      )
   )
   private static void homovore$fireOverlay(class_4587 poseStack, class_4597 buffers, class_1058 sprite) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.FIRE_OVERLAY)) {
         homovore$invokeRenderFire(poseStack, buffers, sprite);
      }
   }

   @Redirect(
      method = "method_23067",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4603;method_23069(Lnet/minecraft/class_310;Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V"
      )
   )
   private static void homovore$liquidOverlay(class_310 client, class_4587 poseStack, class_4597 buffers) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.LIQUID_OVERLAY)) {
         homovore$invokeRenderWater(client, poseStack, buffers);
      }
   }

   @Redirect(
      method = "method_23067",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4603;method_23068(Lnet/minecraft/class_1058;Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V"
      )
   )
   private static void homovore$inWallOverlay(class_1058 sprite, class_4587 poseStack, class_4597 buffers) {
      if (!RenderFeatureEvent.isSuppressed(RenderFeatureEvent.Feature.IN_WALL_OVERLAY)) {
         homovore$invokeRenderTex(sprite, poseStack, buffers);
      }
   }
}
