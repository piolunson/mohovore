package dev.piolunson.mixin.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderLayer.class)
public interface RenderTypeAccessor {
   @Accessor("renderSetup")
   RenderSetup homovore$getState();
}
