package dev.leonetic.util.world;

import java.util.Objects;
import net.minecraft.class_1657;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_5134;

public final class AnchorDamageUtil {
   public static final double POWER = 5.0;
   public static final double EFFECT_RADIUS = 10.0;

   private AnchorDamageUtil() {
      throw new AssertionError();
   }

   public static double maxDamage(class_1937 level, class_1657 target, class_243 source) {
      return damage(level, target, source, 1.0F);
   }

   public static double damage(class_1937 level, class_1657 target, class_243 source, class_2338 anchorPos) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(source, "source");
      if (target.method_31549().field_7480) {
         return 0.0;
      }

      double distanceSquared = target.method_5707(source);
      if (distanceSquared >= 100.0) {
         return 0.0;
      }

      float exposure = anchorPos == null ? ExplosionMath.exposure(level, source, target) : ExplosionMath.exposureIgnoring(level, source, target, anchorPos);
      return damage(level, target, source, exposure);
   }

   private static double damage(class_1937 level, class_1657 target, class_243 source, float exposure) {
      return ExplosionMath.damage(
         5.0,
         target.method_5707(source),
         exposure,
         target.method_6096(),
         target.method_45325(class_5134.field_23725),
         ExplosionMath.resistanceFactor(target),
         ExplosionMath.protectionPoints(target),
         level.method_8407().method_5461()
      );
   }
}
