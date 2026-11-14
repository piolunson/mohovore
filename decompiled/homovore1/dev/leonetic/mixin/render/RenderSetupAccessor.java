package dev.leonetic.mixin.render;

import java.util.Map;
import net.minecraft.class_12245;
import net.minecraft.class_12246;
import net.minecraft.class_12247;
import net.minecraft.class_12250;
import net.minecraft.class_12247.class_12338;
import net.minecraft.class_12247.class_4750;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_12247.class)
public interface RenderSetupAccessor {
   @Accessor("field_63987")
   Map<String, class_12338> homovore$getTextures();

   @Accessor("field_63988")
   class_12250 homovore$getTextureTransform();

   @Accessor("field_63989")
   class_12246 homovore$getOutputTarget();

   @Accessor("field_63990")
   class_4750 homovore$getOutlineProperty();

   @Accessor("field_63991")
   boolean homovore$useLightmap();

   @Accessor("field_63992")
   boolean homovore$useOverlay();

   @Accessor("field_63993")
   boolean homovore$affectsCrumbling();

   @Accessor("field_63994")
   boolean homovore$sortOnUpload();

   @Accessor("field_63995")
   int homovore$getBufferSize();

   @Accessor("field_63996")
   class_12245 homovore$getLayeringTransform();
}
