package dev.leonetic.util;

import dev.leonetic.util.traits.Util;
import java.util.Iterator;
import net.minecraft.class_1268;
import net.minecraft.class_1292;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1303;
import net.minecraft.class_1304;
import net.minecraft.class_1542;
import net.minecraft.class_1657;
import net.minecraft.class_1683;
import net.minecraft.class_1799;
import net.minecraft.class_1893;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_259;
import net.minecraft.class_2680;
import net.minecraft.class_2846;
import net.minecraft.class_2879;
import net.minecraft.class_3486;
import net.minecraft.class_3965;
import net.minecraft.class_1269.class_9860;
import net.minecraft.class_1269.class_9861;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_2846.class_2847;

public class InteractionUtil implements Util {
   public static boolean canBreak(class_2338 blockPos, class_2680 state) {
      return !mc.field_1724.method_68878() && state.method_26214(mc.field_1687, blockPos) < 0.0F
         ? false
         : state.method_26218(mc.field_1687, blockPos) != class_259.method_1073();
   }

   public static boolean isPlaceable(class_2338 pos, boolean entityCheck) {
      if (!mc.field_1687.method_8320(pos).method_45474()) {
         return false;
      } else {
         Iterator var2 = mc.field_1687
            .method_8390(class_1297.class, new class_238(pos), ex -> !(ex instanceof class_1683) && !(ex instanceof class_1542) && !(ex instanceof class_1303))
            .iterator();
         if (var2.hasNext()) {
            class_1297 e = (class_1297)var2.next();
            return e instanceof class_1657 ? false : !entityCheck;
         } else {
            return true;
         }
      }
   }

   public static boolean breakBlock(class_2338 pos) {
      if (!canBreak(pos, mc.field_1687.method_8320(pos))) {
         return false;
      }

      class_2338 bp = pos instanceof class_2339 ? new class_2338(pos) : pos;
      mc.method_1562().method_52787(new class_2846(class_2847.field_12968, bp, class_2350.field_11036));
      mc.field_1724.method_6104(class_1268.field_5808);
      mc.method_1562().method_52787(new class_2846(class_2847.field_12973, bp, class_2350.field_11036));
      mc.method_1562().method_52787(new class_2879(class_1268.field_5808));
      return true;
   }

   public static void useItem(class_2338 pos) {
      useItem(pos, class_1268.field_5808);
   }

   public static void useItem(class_2338 pos, class_1268 hand) {
      if (mc.field_1687 != null && mc.field_1724 != null && mc.field_1761 != null) {
         class_2350 direction = mc.field_1765 != null ? ((class_3965)mc.field_1765).method_17780() : class_2350.field_11033;
         if (mc.field_1761.method_2896(mc.field_1724, hand, new class_3965(class_243.method_24953(pos), direction, pos, false)) instanceof class_9860 success
            && success.comp_2909() != class_9861.field_52426) {
            mc.field_1724.field_3944.method_52787(new class_2879(hand));
         }
      }
   }

   public static boolean place(class_2338 pos, boolean airPlace) {
      return place(pos, airPlace, class_1268.field_5808);
   }

   public static boolean place(class_2338 pos, boolean airPlace, class_1268 hand) {
      if (mc.field_1687 == null || mc.field_1724 == null || mc.field_1761 == null) {
         return false;
      }

      if (!isPlaceable(pos, false)) {
         return false;
      }

      class_2350 direction = calcSide(pos);
      if (direction == null) {
         if (!airPlace) {
            return false;
         }

         direction = mc.field_1765 != null ? ((class_3965)mc.field_1765).method_17780() : class_2350.field_11033;
      }

      class_2338 bp = airPlace ? pos : pos.method_10093(direction);
      if (mc.field_1761
            .method_2896(
               mc.field_1724,
               hand,
               new class_3965(
                  airPlace ? class_243.method_24953(pos) : class_243.method_24953(bp).method_43206(direction.method_10153(), 0.5),
                  airPlace ? direction : direction.method_10153(),
                  bp,
                  false
               )
            ) instanceof class_9860 success
         && success.comp_2909() != class_9861.field_52426) {
         mc.field_1724.field_3944.method_52787(new class_2879(hand));
      }

      return true;
   }

   public static class_2350 calcSide(class_2338 pos) {
      for (class_2350 d : class_2350.values()) {
         if (!mc.field_1687.method_8320(pos.method_10081(d.method_62675())).method_45474()) {
            return d;
         }
      }

      return null;
   }

   public static double getBlockBreakingSpeed(int slot, class_2338 pos) {
      return getBlockBreakingSpeed(slot, mc.field_1687.method_8320(pos));
   }

   public static double getBlockBreakingSpeed(int slot, class_2680 block) {
      double speed = rawMiningSpeed(slot, block, mc.field_1724.method_24828());
      float hardness = block.method_26214(null, null);
      if (hardness == -1.0F) {
         return 0.0;
      }

      speed /= hardness / (block.method_29291() && !((class_1799)mc.field_1724.method_31548().method_67533().get(slot)).method_7951(block) ? 100 : 30);
      float ticks = (float)(Math.floor(1.0 / speed) + 1.0);
      return (long)(ticks / 20.0F * 1000.0F);
   }

   public static double rawMiningSpeed(int slot, class_2680 block, boolean onGround) {
      double speed = ((class_1799)mc.field_1724.method_31548().method_67533().get(slot)).method_7924(block);
      if (speed > 1.0) {
         class_1799 tool = mc.field_1724.method_31548().method_5438(slot);
         int efficiency = EnchantmentUtil.getLevel(class_1893.field_9131, tool);
         if (efficiency > 0 && !tool.method_7960()) {
            speed += efficiency * efficiency + 1;
         }
      }

      if (class_1292.method_5576(mc.field_1724)) {
         speed *= 1.0F + (class_1292.method_5575(mc.field_1724) + 1) * 0.2F;
      }

      if (mc.field_1724.method_6059(class_1294.field_5901)) {
         float k = switch (mc.field_1724.method_6112(class_1294.field_5901).method_5578()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
         speed *= k;
      }

      if (mc.field_1724.method_5777(class_3486.field_15517) && EnchantmentUtil.has(class_1893.field_9105, class_1304.field_6169)) {
         speed /= 5.0;
      }

      if (!onGround) {
         speed /= 5.0;
      }

      return speed;
   }

   public static float getBreakDelta(class_1799 stack, class_2680 state, class_2338 pos) {
      float hardness = state.method_26214(mc.field_1687, pos);
      if (hardness == -1.0F) {
         return 0.0F;
      }

      float speed = stack.method_7924(state);
      if (speed > 1.0F) {
         int efficiency = EnchantmentUtil.getLevel(class_1893.field_9131, stack);
         if (efficiency > 0 && !stack.method_7960()) {
            speed += efficiency * efficiency + 1;
         }
      }

      if (class_1292.method_5576(mc.field_1724)) {
         speed *= 1.0F + (class_1292.method_5575(mc.field_1724) + 1) * 0.2F;
      }

      if (mc.field_1724.method_6059(class_1294.field_5901)) {
         speed *= switch (mc.field_1724.method_6112(class_1294.field_5901).method_5578()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      if (mc.field_1724.method_5777(class_3486.field_15517) && !EnchantmentUtil.has(class_1893.field_9105, class_1304.field_6169)) {
         speed /= 5.0F;
      }

      if (!mc.field_1724.method_24828()) {
         speed /= 5.0F;
      }

      boolean harvestable = !state.method_29291() || stack.method_7951(state);
      return speed / hardness / (harvestable ? 30 : 100);
   }

   public static double breakDelta(double speed, class_2680 block) {
      float hardness = block.method_26214(null, null);
      return hardness == -1.0F ? 0.0 : speed / hardness / 30.0;
   }

   public static int fastestToolSlot(class_2680 block) {
      int best = mc.field_1724.method_31548().method_67532();
      double bestSpeed = -1.0;

      for (int i = 0; i < 9; i++) {
         double speed = rawMiningSpeed(i, block, true);
         if (speed > bestSpeed) {
            bestSpeed = speed;
            best = i;
         }
      }

      return best;
   }

   public static class_2350 right(class_2350 direction) {
      return switch (direction) {
         case field_11034 -> class_2350.field_11035;
         case field_11035 -> class_2350.field_11039;
         case field_11039 -> class_2350.field_11043;
         case field_11043 -> class_2350.field_11034;
         default -> throw new IllegalStateException("Unexpected value: " + direction);
      };
   }

   public static class_2350 left(class_2350 direction) {
      return switch (direction) {
         case field_11034 -> class_2350.field_11043;
         case field_11035 -> class_2350.field_11034;
         case field_11039 -> class_2350.field_11035;
         case field_11043 -> class_2350.field_11039;
         default -> throw new IllegalStateException("Unexpected value: " + direction);
      };
   }
}
