package dev.leonetic.util;

import dev.leonetic.manager.RotationManager;
import java.util.Objects;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
   private static final double AIM_INSET = 0.0625;
   private static final double EDGE_EPSILON = 1.0E-9;

   private RotationUtil() {
      throw new AssertionError();
   }

   public static Vec3d closestPoint(Vec3d point, Box box) {
      Objects.requireNonNull(point, "point");
      Objects.requireNonNull(box, "box");
      return new Vec3d(
         MathHelper.clamp(point.x, box.minX, box.maxX), MathHelper.clamp(point.y, box.minY, box.maxY), MathHelper.clamp(point.z, box.minZ, box.maxZ)
      );
   }

   public static double distanceTo(Vec3d point, Box box) {
      return point.distanceTo(closestPoint(point, box));
   }

   public static Vec3d closestAimPoint(Vec3d eye, Box box) {
      Vec3d closest = closestPoint(eye, box);
      double x = closest.x;
      double z = closest.z;
      if (Math.abs(x - box.minX) < 1.0E-9) {
         x = Math.min(box.minX + 0.0625, box.maxX - 1.0E-9);
      } else if (Math.abs(x - box.maxX) < 1.0E-9) {
         x = Math.max(box.maxX - 0.0625, box.minX + 1.0E-9);
      }

      if (Math.abs(z - box.minZ) < 1.0E-9) {
         z = Math.min(box.minZ + 0.0625, box.maxZ - 1.0E-9);
      } else if (Math.abs(z - box.maxZ) < 1.0E-9) {
         z = Math.max(box.maxZ - 0.0625, box.minZ + 1.0E-9);
      }

      return new Vec3d(x, closest.y, z);
   }

   public static Vec3d closestOrigin(Box box, Vec3d... candidates) {
      Objects.requireNonNull(box, "box");
      Objects.requireNonNull(candidates, "candidates");
      if (candidates.length == 0) {
         throw new IllegalArgumentException("At least one candidate origin is required");
      }

      Vec3d best = Objects.requireNonNull(candidates[0], "candidate");
      double bestDistance = distanceTo(best, box);

      for (int i = 1; i < candidates.length; i++) {
         Vec3d candidate = Objects.requireNonNull(candidates[i], "candidate");
         double distance = distanceTo(candidate, box);
         if (distance < bestDistance) {
            best = candidate;
            bestDistance = distance;
         }
      }

      return best;
   }

   public static RotationManager.Angles anglesTo(Vec3d from, Vec3d to) {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double dz = to.z - from.z;
      float yaw = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
      return new RotationManager.Angles(yaw, pitch);
   }

   public static boolean rayIntersects(Vec3d origin, Box box, double reach, RotationManager.Angles angles) {
      Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(box, "box");
      Objects.requireNonNull(angles, "angles");
      if (Double.isFinite(reach) && !(reach < 0.0)) {
         Vec3d look = Vec3d.fromPolar(angles.pitch(), angles.yaw());
         return box.raycast(origin, origin.add(look.multiply(reach))).isPresent();
      } else {
         throw new IllegalArgumentException("Reach must be finite and non-negative");
      }
   }
}
