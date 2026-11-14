package dev.leonetic.mixin.entity;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface EntityRotationAccessor {
   @Accessor("renderPitch")
   float homovore$getXBob();

   @Accessor("renderPitch")
   void homovore$setXBob(float var1);

   @Accessor("lastRenderPitch")
   float homovore$getXBobO();

   @Accessor("lastRenderPitch")
   void homovore$setXBobO(float var1);

   @Accessor("renderYaw")
   float homovore$getYBob();

   @Accessor("renderYaw")
   void homovore$setYBob(float var1);

   @Accessor("lastRenderYaw")
   float homovore$getYBobO();

   @Accessor("lastRenderYaw")
   void homovore$setYBobO(float var1);

   @Accessor("lastOnGround")
   boolean homovore$getLastOnGround();
}
