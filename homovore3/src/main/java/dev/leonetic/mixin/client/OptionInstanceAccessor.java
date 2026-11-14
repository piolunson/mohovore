package dev.leonetic.mixin.client;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface OptionInstanceAccessor<T> {
   @Accessor("value")
   void homovore$setValue(T var1);
}
