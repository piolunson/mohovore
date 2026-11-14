package dev.leonetic.mixin;

import net.minecraft.class_2547;
import net.minecraft.class_2596;
import net.minecraft.class_8038;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_8038.class)
public interface BundlePacketAccessor<T extends class_2547> {
   @Mutable
   @Accessor("field_41876")
   void homovore$setPackets(Iterable<class_2596<? super T>> var1);
}
