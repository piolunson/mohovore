package dev.piolunson.v2.mixin;

import dev.piolunson.v2.feature.module.impl.FreecamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MinecraftPickMixin {
   private static final double FREECAM_REACH = 256.0;

   @Inject(method = "updateCrosshairTarget", at = @At("HEAD"), cancellable = true)
   private void homovore$freecamPick(float partialTick, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      FreecamModule freecam = FreecamModule.active();
      if (freecam != null && client.world != null && client.player != null) {
         Vec3d start = freecam.getCameraPosition(partialTick);
         Vec3d end = start.add(Vec3d.fromPolar(freecam.getCameraPitch(partialTick), freecam.getCameraYaw(partialTick)).multiply(256.0));
         client.crosshairTarget = client.world.raycast(new RaycastContext(start, end, ShapeType.OUTLINE, FluidHandling.NONE, client.player));
         client.targetedEntity = null;
         ci.cancel();
      }
   }
}
