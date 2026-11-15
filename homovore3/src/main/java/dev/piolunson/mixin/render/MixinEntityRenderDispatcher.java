package dev.piolunson.mixin.render;

import dev.piolunson.Homovore;
import dev.piolunson.features.modules.render.SeeThroughModule;
import dev.piolunson.util.render.SeeThroughRender;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderManager.class)
public class MixinEntityRenderDispatcher {
   @Redirect(
      method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"
      )
   )
   private void homovore$seeThroughSubmit(
      EntityRenderer renderer, EntityRenderState state, MatrixStack poseStack, OrderedRenderCommandQueue collector, CameraRenderState camera
   ) {
      boolean apply = false;
      SeeThroughModule seeThrough = Homovore.moduleManager == null ? null : Homovore.moduleManager.getModuleByClass(SeeThroughModule.class);
      if (seeThrough != null && seeThrough.isEnabled() && seeThrough.shouldSeeThrough(state)) {
         apply = true;
         SeeThroughRender.begin();
      }

      try {
         renderer.render(state, poseStack, collector, camera);
      } finally {
         if (apply) {
            SeeThroughRender.end();
         }
      }
   }
}
