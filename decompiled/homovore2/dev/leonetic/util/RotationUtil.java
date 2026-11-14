package dev.leonetic.util;

import dev.leonetic.manager.RotationManager;
import java.util.Objects;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3532;

public final class RotationUtil {
   private static final double AIM_INSET = 0.0625;
   private static final double EDGE_EPSILON = 1.0E-9;

   private RotationUtil() {
      throw new AssertionError();
   }

   public static class_243 closestPoint(class_243 point, class_238 box) {
      Objects.requireNonNull(point, "point");
      Objects.requireNonNull(box, "box");
      return new class_243(
         class_3532.method_15350(point.field_1352, box.field_1323, box.field_1320),
         class_3532.method_15350(point.field_1351, box.field_1322, box.field_1325),
         class_3532.method_15350(point.field_1350, box.field_1321, box.field_1324)
      );
   }

   public static double distanceTo(class_243 point, class_238 box) {
      return point.method_1022(closestPoint(point, box));
   }

   public static class_243 closestAimPoint(class_243 eye, class_238 box) {
      class_243 closest = closestPoint(eye, box);
      double x = closest.field_1352;
      double z = closest.field_1350;
      if (Math.abs(x - box.field_1323) < 1.0E-9) {
         x = Math.min(box.field_1323 + 0.0625, box.field_1320 - 1.0E-9);
      } else if (Math.abs(x - box.field_1320) < 1.0E-9) {
         x = Math.max(box.field_1320 - 0.0625, box.field_1323 + 1.0E-9);
      }

      if (Math.abs(z - box.field_1321) < 1.0E-9) {
         z = Math.min(box.field_1321 + 0.0625, box.field_1324 - 1.0E-9);
      } else if (Math.abs(z - box.field_1324) < 1.0E-9) {
         z = Math.max(box.field_1324 - 0.0625, box.field_1321 + 1.0E-9);
      }

      return new class_243(x, closest.field_1351, z);
   }

   public static class_243 closestOrigin(class_238 box, class_243... candidates) {
      Objects.requireNonNull(box, "box");
      Objects.requireNonNull(candidates, "candidates");
      if (candidates.length == 0) {
         throw new IllegalArgumentException("At least one candidate origin is required");
      }

      class_243 best = Objects.requireNonNull(candidates[0], "candidate");
      double bestDistance = distanceTo(best, box);

      for (int i = 1; i < candidates.length; i++) {
         class_243 candidate = Objects.requireNonNull(candidates[i], "candidate");
         double distance = distanceTo(candidate, box);
         if (distance < bestDistance) {
            best = candidate;
            bestDistance = distance;
         }
      }

      return best;
   }

   public static RotationManager.Angles anglesTo(class_243 from, class_243 to) {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
      double dx = to.field_1352 - from.field_1352;
      double dy = to.field_1351 - from.field_1351;
      double dz = to.field_1350 - from.field_1350;
      float yaw = (float)class_3532.method_15338(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
      return new RotationManager.Angles(yaw, pitch);
   }

   public static boolean rayIntersects(class_243 origin, class_238 box, double reach, RotationManager.Angles angles) {
      Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(box, "box");
      Objects.requireNonNull(angles, "angles");
      if (Double.isFinite(reach) && !(reach < 0.0)) {
         class_243 look = class_243.method_1030(angles.pitch(), angles.yaw());
         return box.method_992(origin, origin.method_1019(look.method_1021(reach))).isPresent();
      } else {
         throw new IllegalArgumentException("Reach must be finite and non-negative");
      }
   }
}
