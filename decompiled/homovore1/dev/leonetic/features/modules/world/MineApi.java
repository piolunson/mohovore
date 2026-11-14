package dev.leonetic.features.modules.world;

import net.minecraft.class_2338;

public interface MineApi {
   boolean isAvailable();

   boolean requestBreak(class_2338 var1);

   boolean isMining(class_2338 var1);

   class_2338 getRebreakPos();

   boolean hasFreePrimary();

   boolean hasFreeSecondary();

   boolean inMineRange(class_2338 var1);
}
