package dev.leonetic.features.modules.world;

import net.minecraft.util.math.BlockPos;

public interface MineApi {
   boolean isAvailable();

   boolean requestBreak(BlockPos var1);

   boolean isMining(BlockPos var1);

   BlockPos getRebreakPos();

   boolean hasFreePrimary();

   boolean hasFreeSecondary();

   boolean inMineRange(BlockPos var1);
}
