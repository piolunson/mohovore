package dev.piolunson.util.player;

import dev.piolunson.Homovore;
import dev.piolunson.util.traits.Util;
import net.minecraft.util.Hand;

public final class EatUtil implements Util {
   private EatUtil() {
      throw new AssertionError();
   }

   public static boolean shouldDefer() {
      if (mc.player == null) {
         return false;
      } else {
         return Homovore.swapManager.isLatched() ? true : mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND;
      }
   }
}
