package dev.leonetic.util;

import dev.leonetic.util.traits.Util;
import java.util.Iterator;
import net.minecraft.class_1297;
import net.minecraft.class_1303;
import net.minecraft.class_1511;
import net.minecraft.class_1542;
import net.minecraft.class_1671;
import net.minecraft.class_1683;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3532;

public final class PlaceUtil implements Util {
   private PlaceUtil() {
      throw new AssertionError();
   }

   public static int minCell(double boxMin) {
      return class_3532.method_15357(boxMin);
   }

   public static int maxCell(double boxMax) {
      return class_3532.method_15384(boxMax) - 1;
   }

   public static boolean canPlace(class_2338 pos) {
      if (mc.field_1687.method_31606(pos)) {
         return false;
      } else if (!mc.field_1687.method_8320(pos).method_45474()) {
         return false;
      } else if (class_243.method_24953(pos).method_1022(mc.field_1724.method_33571()) > 5.154) {
         return false;
      } else {
         return mc.field_1724.method_5829().method_994(new class_238(pos)) ? false : noEntityCollision(pos);
      }
   }

   public static boolean noEntityCollision(class_2338 pos) {
      Iterator var1 = mc.field_1687
         .method_8390(
            class_1297.class,
            new class_238(pos),
            ex -> !(ex instanceof class_1542)
               && !(ex instanceof class_1303)
               && !(ex instanceof class_1683)
               && !(ex instanceof class_1671)
               && !(ex instanceof class_1511)
               && ex != mc.field_1724
         )
         .iterator();
      if (var1.hasNext()) {
         class_1297 e = (class_1297)var1.next();
         return false;
      } else {
         return true;
      }
   }
}
