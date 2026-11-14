package dev.leonetic.util.player;

import dev.leonetic.Homovore;
import dev.leonetic.util.traits.Util;
import net.minecraft.class_1268;

public final class EatUtil implements Util {
   private EatUtil() {
      throw new AssertionError();
   }

   public static boolean shouldDefer() {
      if (mc.field_1724 == null) {
         return false;
      } else {
         return Homovore.swapManager.isLatched() ? true : mc.field_1724.method_6115() && mc.field_1724.method_6058() == class_1268.field_5808;
      }
   }
}
