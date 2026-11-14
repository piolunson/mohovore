package dev.leonetic.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface MultiPlayerGameModeAccessor {
   @Accessor("lastSelectedSlot")
   void homovore$setCarriedIndex(int var1);
}
