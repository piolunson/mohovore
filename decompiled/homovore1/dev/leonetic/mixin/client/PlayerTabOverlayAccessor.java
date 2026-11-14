package dev.leonetic.mixin.client;

import net.minecraft.class_2561;
import net.minecraft.class_355;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_355.class)
public interface PlayerTabOverlayAccessor {
   @Accessor("field_2153")
   class_2561 homovore$getHeader();

   @Accessor("field_2154")
   class_2561 homovore$getFooter();
}
