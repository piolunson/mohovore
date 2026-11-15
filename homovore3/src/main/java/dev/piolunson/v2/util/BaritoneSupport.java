package dev.piolunson.v2.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

public final class BaritoneSupport {
   private static final String[] MOD_IDS = new String[]{"baritone-meteor", "baritone"};
   private static Boolean available;

   private BaritoneSupport() {
   }

   public static boolean isAvailable() {
      if (available == null) {
         available = false;

         for (String modId : MOD_IDS) {
            if (FabricLoader.getInstance().isModLoaded(modId)) {
               available = true;
               break;
            }
         }
      }

      return available;
   }

   public static boolean isPathing() {
      return isAvailable() && BaritoneBridge.isPathing();
   }

   public static void pause() {
      if (isAvailable()) {
         BaritoneBridge.setPaused(true);
      }
   }

   public static void resume() {
      if (isAvailable()) {
         BaritoneBridge.setPaused(false);
      }
   }

   public static boolean isPaused() {
      return isAvailable() && BaritoneBridge.isPaused();
   }

   public static void pathTo(BlockPos pos) {
      if (isAvailable()) {
         BaritoneBridge.pathTo(pos);
      }
   }

   public static void clearGoal() {
      if (isAvailable()) {
         BaritoneBridge.clearGoal();
      }
   }
}
