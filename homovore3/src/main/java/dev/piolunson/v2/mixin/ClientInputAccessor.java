package dev.piolunson.v2.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface ClientInputAccessor {
   @Accessor("movementVector")
   void homovore$setMoveVector(Vec2f var1);
}
