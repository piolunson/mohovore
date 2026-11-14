package dev.leonetic.v2.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface LocalPlayerAccessor {
   @Accessor("ticksSinceLastPositionPacketSent")
   void homovore$setPositionReminder(int var1);

   @Accessor("lastYawClient")
   void homovore$setYRotLast(float var1);

   @Accessor("lastPitchClient")
   void homovore$setXRotLast(float var1);
}
