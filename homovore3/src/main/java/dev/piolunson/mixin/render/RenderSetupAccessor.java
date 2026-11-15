package dev.piolunson.mixin.render;

import java.util.Map;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.TextureTransform;
import net.minecraft.client.render.RenderSetup.OutlineMode;
import net.minecraft.client.render.RenderSetup.TextureSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
   @Accessor("textures")
   Map<String, TextureSpec> homovore$getTextures();

   @Accessor("textureTransform")
   TextureTransform homovore$getTextureTransform();

   @Accessor("outputTarget")
   OutputTarget homovore$getOutputTarget();

   @Accessor("outlineMode")
   OutlineMode homovore$getOutlineProperty();

   @Accessor("useLightmap")
   boolean homovore$useLightmap();

   @Accessor("useOverlay")
   boolean homovore$useOverlay();

   @Accessor("hasCrumbling")
   boolean homovore$affectsCrumbling();

   @Accessor("translucent")
   boolean homovore$sortOnUpload();

   @Accessor("expectedBufferSize")
   int homovore$getBufferSize();

   @Accessor("layeringTransform")
   LayeringTransform homovore$getLayeringTransform();
}
