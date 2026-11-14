package dev.leonetic.mixin.client;

import net.minecraft.class_7172;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_7172.class)
public interface OptionInstanceAccessor<T> {
   @Accessor("field_37868")
   void homovore$setValue(T var1);
}
