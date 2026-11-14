package dev.leonetic.util.inventory.strategy;

import dev.leonetic.util.inventory.Result;

public interface SwapStrategy {
   boolean swap(Result var1);

   boolean swapBack(int var1, Result var2);
}
