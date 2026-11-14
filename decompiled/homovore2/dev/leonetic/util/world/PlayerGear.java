package dev.leonetic.util.world;

import java.util.Set;
import net.minecraft.class_1304;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;

public final class PlayerGear {
   private static final Set<class_1792> GEAR = Set.of(
      class_1802.field_8805,
      class_1802.field_8058,
      class_1802.field_8348,
      class_1802.field_8285,
      class_1802.field_22027,
      class_1802.field_22028,
      class_1802.field_22029,
      class_1802.field_22030
   );
   private static final class_1304[] ARMOR = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};

   private PlayerGear() {
   }

   public static boolean isGeared(class_1657 player) {
      for (class_1304 slot : ARMOR) {
         class_1799 stack = player.method_6118(slot);
         if (stack.method_7960() || !GEAR.contains(stack.method_7909())) {
            return false;
         }
      }

      return true;
   }
}
